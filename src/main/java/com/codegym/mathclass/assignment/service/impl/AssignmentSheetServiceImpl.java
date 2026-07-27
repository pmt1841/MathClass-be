package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentSheetItem;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetItemRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentSheetServiceImpl implements AssignmentSheetService {

    private final AssignmentSheetRepository assignmentSheetRepository;
    private final AssignmentSheetItemRepository assignmentSheetItemRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;

    /**
     * Xuất bản một phiếu bài tập tới kho cá nhân (Master Sheet) và tùy chọn tới các lớp học.
     *
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Resolve danh sách bài tập gốc từ request hoặc fallback tìm lại từ phiếu cùng tên.</li>
     *   <li>Upsert Master Sheet (classroom = null) làm bản lưu trữ trong kho giáo viên.</li>
     *   <li>Clone phiếu và bài tập tới từng lớp được chỉ định (nếu có).</li>
     *   <li>Archive các bài tập gốc ở trạng thái DRAFT sau khi toàn bộ clone hoàn tất.</li>
     * </ol>
     *
     * @param request   Thông tin phiếu bài tập cần publish, bao gồm danh sách bài tập và lớp đích.
     * @param teacherId ID của giáo viên thực hiện thao tác.
     * @throws ResourceNotFoundException nếu giáo viên không tồn tại hoặc lớp học không tìm thấy.
     * @throws AccessDeniedException     nếu một trong các bài tập không thuộc về giáo viên.
     * @throws IllegalArgumentException  nếu không tìm thấy bài tập nào để publish.
     */
    @Override
    @Transactional
    public void publishAssignmentSheet(PublishAssignmentSheetRequest request, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên"));

        List<Assignment> originalAssignments = resolveOriginalAssignments(request, teacherId);

        upsertMasterSheet(request, teacher, originalAssignments);

        if (request.getTargets() != null) {
            publishToClassrooms(request.getTargets(), request, teacher, originalAssignments);
        }

        archiveDraftAssignments(originalAssignments);
    }

    // ─── Resolve original assignments ────────────────────────────────────────

    /**
     * Xác định danh sách bài tập gốc cần publish.
     *
     * <p>Có hai path:
     * <ul>
     *   <li><b>Path A</b>: {@code request.assignmentIds} được cung cấp → fetch và validate ownership.</li>
     *   <li><b>Path B</b>: {@code assignmentIds} rỗng → fallback tìm lại từ phiếu cùng tiêu đề (trường hợp giao lại phiếu cũ).</li>
     * </ul>
     *
     * <p>Ownership validation so sánh số lượng kết quả trả về với số IDs yêu cầu.
     * Nếu không khớp, có thể do ID không tồn tại hoặc thuộc giáo viên khác —
     * cả hai trường hợp đều từ chối để tránh leak thông tin.
     *
     * @throws AccessDeniedException    nếu có bài tập không thuộc {@code teacherId}.
     * @throws IllegalArgumentException nếu fallback cũng không tìm thấy bài tập nào.
     */
    private List<Assignment> resolveOriginalAssignments(PublishAssignmentSheetRequest request, long teacherId) {
        if (request.getAssignmentIds() != null && !request.getAssignmentIds().isEmpty()) {
            List<Assignment> owned = assignmentRepository
                    .findAllByIdInAndTeacherId(request.getAssignmentIds(), teacherId);
            if (owned.size() != request.getAssignmentIds().size()) {
                throw new AccessDeniedException(
                        "Một số bài tập không thuộc về bạn hoặc không tồn tại.");
            }
            return owned;
        }
        List<Assignment> resolved = resolveFromExistingSheets(teacherId, request.getTitle());
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy bài tập nào để giao.");
        }
        return resolved;
    }

    /**
     * Fallback resolve: tìm lại bài tập gốc từ các phiếu cùng tiêu đề của giáo viên.
     *
     * <p>Duyệt qua phiếu đầu tiên có items hợp lệ:
     * <ul>
     *   <li>Nếu item trỏ tới clone (có {@code parentId}), thu thập parentIds rồi batch-fetch một lần.</li>
     *   <li>Nếu item trỏ thẳng tới bài gốc (không có {@code parentId}), dùng trực tiếp.</li>
     * </ul>
     *
     * <p>Batch {@code findAllById(parentIds)} thay thế N × {@code findById} trong vòng lặp.
     *
     * @return Danh sách bài tập gốc, hoặc {@code List.of()} nếu không tìm thấy phiếu nào hợp lệ.
     */
    private List<Assignment> resolveFromExistingSheets(long teacherId, String title) {
        List<AssignmentSheet> sheets = assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, title);

        for (AssignmentSheet sheet : sheets) {
            if (sheet.getItems() == null || sheet.getItems().isEmpty()) continue;

            List<Long> parentIds = new ArrayList<>();
            List<Assignment> directOriginals = new ArrayList<>();

            for (AssignmentSheetItem item : sheet.getItems()) {
                Assignment asgn = item.getAssignment();
                if (asgn == null) continue;
                if (asgn.getParentId() != null) {
                    parentIds.add(asgn.getParentId());
                } else {
                    directOriginals.add(asgn);
                }
            }

            List<Assignment> resolved = new ArrayList<>(directOriginals);
            if (!parentIds.isEmpty()) {
                resolved.addAll(assignmentRepository.findAllById(parentIds));
            }

            if (!resolved.isEmpty()) return resolved;
        }
        return List.of();
    }

    // ─── Master Sheet ────────────────────────────────────────────────────

    /**
     * Tạo hoặc lấy Master Sheet (phiếu gốc trong kho, không gắn với lớp nào).
     *
     * <p>Nếu Master Sheet chưa có clones hợp lệ (tất cả items phải trỏ tới bài clone qua {@code parentId}),
     * tiến hành xóa items cũ và tạo lại để đảm bảo tính nhất quán.
     * Điều này xử lý trường hợp phiếu bị chỉnh sửa sau khi tạo lần đầu.
     */
    private void upsertMasterSheet(
            PublishAssignmentSheetRequest request, User teacher, List<Assignment> originals) {

        AssignmentSheet masterSheet = assignmentSheetRepository
                .findFirstByTeacherIdAndTitleAndClassroomIsNull(teacher.getId(), request.getTitle())
                .orElseGet(() -> assignmentSheetRepository.save(buildMasterSheet(request, teacher)));

        if (!masterSheetHasValidClones(masterSheet)) {
            populateMasterSheetItems(masterSheet, originals, teacher);
        }
    }

    /**
     * Khởi tạo AssignmentSheet làm Master Sheet (kho cá nhân, classroom = null).
     * Visibility chỉ được set nếu request cung cấp giá trị, tránh ghi đè giá trị mặc định của entity.
     */
    private AssignmentSheet buildMasterSheet(PublishAssignmentSheetRequest request, User teacher) {
        AssignmentSheet sheet = new AssignmentSheet();
        sheet.setTitle(request.getTitle());
        sheet.setDescription(request.getDescription());
        sheet.setTeacher(teacher);
        sheet.setClassroom(null);
        if (request.getVisibility() != null) {
            sheet.setVisibility(request.getVisibility());
        }
        return sheet;
    }

    /**
     * Kiểm tra xem Master Sheet đã có items hợp lệ chưa.
     *
     * <p>"Hợp lệ" nghĩa là tất cả items đều có assignment với {@code parentId} khác null,
     * tức là đã được clone từ bài gốc, không phải bài gốc trực tiếp.
     */
    private boolean masterSheetHasValidClones(AssignmentSheet masterSheet) {
        return masterSheet.getItems() != null
                && !masterSheet.getItems().isEmpty()
                && masterSheet.getItems().stream()
                        .allMatch(item -> item.getAssignment() != null
                                && item.getAssignment().getParentId() != null);
    }

    /**
     * Xóa items cũ của Master Sheet (nếu có) rồi tạo mới bằng batch operations.
     *
     * <p>Dùng {@code saveAll} để giảm số lượng INSERT từ N xuống 1 batch cho cả assignments và items.
     * Cascade {@code CascadeType.ALL} trên {@code Assignment.drawings} và {@code .images}
     * đảm bảo drawings/images được persist tự động khi save assignment clone.
     */
    private void populateMasterSheetItems(
            AssignmentSheet masterSheet, List<Assignment> originals, User teacher) {

        if (masterSheet.getItems() != null && !masterSheet.getItems().isEmpty()) {
            assignmentSheetItemRepository.deleteAll(masterSheet.getItems());
            masterSheet.getItems().clear();
        }

        List<Assignment> masterClones = originals.stream()
                .map(original -> buildAssignmentClone(original, teacher, null, null))
                .collect(Collectors.toList());
        List<Assignment> savedClones = assignmentRepository.saveAll(masterClones);

        List<AssignmentSheetItem> items = savedClones.stream()
                .map(clone -> buildSheetItem(masterSheet, clone))
                .collect(Collectors.toList());
        assignmentSheetItemRepository.saveAll(items);
    }

    // ─── Publish to classrooms ──────────────────────────────────────────────

    /**
     * Lặp qua từng lớp đích và publish phiếu tới từng lớp.
     *
     * @throws ResourceNotFoundException nếu không tìm thấy lớp học theo classCode.
     */
    private void publishToClassrooms(
            List<PublishAssignmentSheetRequest.TargetClass> targets,
            PublishAssignmentSheetRequest request,
            User teacher,
            List<Assignment> originals) {

        for (PublishAssignmentSheetRequest.TargetClass target : targets) {
            Classroom classroom = classroomRepository.findByClassCode(target.getClassCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy lớp học: " + target.getClassCode()));
            publishToClassroom(target, request, teacher, classroom, originals);
        }
    }

    /**
     * Publish phiếu bài tập tới một lớp học cụ thể.
     *
     * <p>Tạo một AssignmentSheet mới gắn với lớp, sau đó clone toàn bộ bài tập gốc
     * với deadline và classroom tương ứng. Dùng {@code saveAll} để giảm số lượng INSERT.
     */
    private void publishToClassroom(
            PublishAssignmentSheetRequest.TargetClass target,
            PublishAssignmentSheetRequest request,
            User teacher,
            Classroom classroom,
            List<Assignment> originals) {

        final AssignmentSheet clonedSheet = assignmentSheetRepository.save(
                buildClassroomSheet(request, teacher, classroom, target.getDeadline()));

        List<Assignment> clonedAssignments = originals.stream()
                .map(original -> buildAssignmentClone(original, teacher, classroom, target.getDeadline()))
                .collect(Collectors.toList());
        List<Assignment> savedClones = assignmentRepository.saveAll(clonedAssignments);

        List<AssignmentSheetItem> items = savedClones.stream()
                .map(clone -> buildSheetItem(clonedSheet, clone))
                .collect(Collectors.toList());
        assignmentSheetItemRepository.saveAll(items);
    }

    /**
     * Khởi tạo AssignmentSheet dành cho một lớp học cụ thể.
     * Sheet này là bản clone của Master Sheet, gắn với classroom và có deadline riêng.
     */
    private AssignmentSheet buildClassroomSheet(
            PublishAssignmentSheetRequest request, User teacher,
            Classroom classroom, LocalDateTime deadline) {

        AssignmentSheet sheet = new AssignmentSheet();
        sheet.setTitle(request.getTitle());
        sheet.setDescription(request.getDescription());
        sheet.setDeadline(deadline);
        sheet.setTeacher(teacher);
        sheet.setClassroom(classroom);
        return sheet;
    }

    // ─── Archive ────────────────────────────────────────────────────────────

    /**
     * Archive tất cả bài tập gốc ở trạng thái DRAFT bằng một saveAll duy nhất.
     * Được gọi sau khi toàn bộ clone đã hoàn tất, tránh duplicate update trong vòng lặp.
     */
    /**
     * Archive tất cả bài tập gốc đang ở trạng thái DRAFT sau khi publish hoàn tất.
     *
     * <p>Logic archive được tách ra gọi một lần duy nhất sau khi toàn bộ clone hoàn thành,
     * thay vì gọi lặp trong vòng lặp per-class như thiết kế cũ. Điều này đảm bảo:
     * <ul>
     *   <li>Không có UPDATE thừa khi publish tới nhiều lớp cùng lúc.</li>
     *   <li>Một {@code saveAll} thay thế N × {@code save}.</li>
     * </ul>
     */
    private void archiveDraftAssignments(List<Assignment> originals) {
        List<Assignment> toArchive = originals.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.DRAFT)
                .peek(a -> a.setStatus(AssignmentStatus.ARCHIVED))
                .collect(Collectors.toList());

        if (!toArchive.isEmpty()) {
            assignmentRepository.saveAll(toArchive);
        }
    }

    // ─── Builder helpers ───────────────────────────────────────────────────

    /**
     * Tạo bản sao (clone) của một bài tập gốc với trạng thái PUBLISHED.
     *
     * <p>Clone sao chép toàn bộ nội dung ({@code title}, {@code description}, {@code content}),
     * drawings và images. {@code parentId} được set để truy ngược về bài gốc.
     * Clone <b>chưa được persist</b> — caller chịu trách nhiệm gọi {@code save/saveAll}.
     *
     * <p>Cascade {@code CascadeType.ALL} trên drawings/images đảm bảo chúng
     * được INSERT cùng với assignment khi gọi {@code assignmentRepository.saveAll}.
     *
     * @param original  Bài tập gốc cần clone.
     * @param teacher   Giáo viên sở hữu clone.
     * @param classroom Lớp học gắn với clone, hoặc {@code null} nếu là Master clone.
     * @param deadline  Deadline của clone, hoặc {@code null} nếu là Master clone.
     * @return Entity chưa persist, sẵn sàng để {@code saveAll}.
     */
    private Assignment buildAssignmentClone(
            Assignment original, User teacher, Classroom classroom, LocalDateTime deadline) {

        Assignment clone = new Assignment();
        clone.setTitle(original.getTitle());
        clone.setDescription(original.getDescription());
        clone.setContent(original.getContent());
        clone.setDeadline(deadline);
        clone.setStatus(AssignmentStatus.PUBLISHED);
        clone.setTeacher(teacher);
        clone.setParentId(original.getId());
        clone.setClassroom(classroom);

        if (original.getDrawings() != null) {
            for (AssignmentDrawing src : original.getDrawings()) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(src.getShapeCode());
                drawing.setJsxGraphData(src.getJsxGraphData());
                drawing.setAssignment(clone);
                clone.getDrawings().add(drawing);
            }
        }

        if (original.getImages() != null) {
            for (AssignmentImage src : original.getImages()) {
                AssignmentImage image = new AssignmentImage();
                image.setImageCode(src.getImageCode());
                image.setImageUrl(src.getImageUrl());
                image.setAssignment(clone);
                clone.getImages().add(image);
            }
        }

        return clone;
    }

    /**
     * Tạo một {@link AssignmentSheetItem} liên kết sheet với assignment.
     * Entity chưa được persist — caller gọi {@code saveAll} sau khi build toàn bộ list.
     */
    private AssignmentSheetItem buildSheetItem(AssignmentSheet sheet, Assignment assignment) {
        AssignmentSheetItem item = new AssignmentSheetItem();
        item.setSheet(sheet);
        item.setAssignment(assignment);
        return item;
    }

    /**
     * Lấy danh sách phiếu bài tập phân trang theo role của người dùng.
     *
     * <ul>
     *   <li><b>TEACHER</b>: xem kho cá nhân (Master Sheets, classroom = null) hoặc lọc theo lớp.</li>
     *   <li><b>STUDENT</b>: xem phiếu của các lớp mình tham gia.</li>
     * </ul>
     *
     * <p>Response được enrich thêm dữ liệu phụ (submission status / danh sách lớp đã publish)
     * qua batch queries để tránh N+1.
     *
     * @param userId    ID người dùng hiện tại.
     * @param role      Role của người dùng: "TEACHER" hoặc "STUDENT".
     * @param keyword   Từ khóa tìm kiếm theo tiêu đề (nullable).
     * @param classCode Lọc theo mã lớp (nullable); nếu null và role là TEACHER → hiển thị kho.
     * @param pageable  Thông tin phân trang và sắp xếp.
     * @return Trang phiếu bài tập đã được enrich theo role.
     * @throws AccessDeniedException nếu role không hợp lệ.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getAssignmentSheetsForCurrentUser(
            long userId, String role, String keyword, String classCode, Pageable pageable) {

        Specification<AssignmentSheet> spec = buildSpecForRole(userId, role, classCode)
                .and(buildKeywordSpec(keyword))
                .and(buildClassCodeSpec(classCode));

        Page<AssignmentSheet> sheetPage = assignmentSheetRepository.findAll(spec, pageable);
        Page<AssignmentSheetResponse> responsePage = sheetPage.map(AssignmentSheetResponse::fromEntity);

        if (Role.STUDENT.name().equals(role)) {
            enrichPageForStudent(responsePage, userId);
        } else if (Role.TEACHER.name().equals(role)) {
            enrichPageForTeacher(responsePage, userId);
        }

        return responsePage;
    }

    // ─── Specification builders ───────────────────────────────────────────────

    /**
     * Chọn Specification phù hợp với role.
     *
     * @throws AccessDeniedException nếu {@code role} không phải TEACHER hoặc STUDENT.
     */
    private Specification<AssignmentSheet> buildSpecForRole(long userId, String role, String classCode) {
        if (Role.TEACHER.name().equals(role)) {
            return buildTeacherSpec(userId, classCode);
        }
        if (Role.STUDENT.name().equals(role)) {
            return buildStudentSpec(userId);
        }
        throw new AccessDeniedException("Role không hợp lệ: " + role);
    }

    /**
     * Spec cho giáo viên:
     * <ul>
     *   <li>Khi {@code classCode} null/blank → hiển thị kho cá nhân (classroom IS NULL).</li>
     *   <li>Khi {@code classCode} được cung cấp → hiển thị phiếu thuộc lớp đó.</li>
     * </ul>
     */
    private Specification<AssignmentSheet> buildTeacherSpec(long teacherId, String classCode) {
        Specification<AssignmentSheet> spec = (root, query, cb) ->
                cb.equal(root.get("teacher").get("id"), teacherId);

        boolean isLibraryView = classCode == null || classCode.isBlank();
        if (isLibraryView) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("classroom")));
        }
        return spec;
    }

    /**
     * Spec cho học sinh: lấy phiếu thuộc các lớp mà học sinh đang tham gia.
     * Dùng INNER JOIN để loại phiếu không gắn lớp (Master Sheets) ra khỏi kết quả.
     */
    private Specification<AssignmentSheet> buildStudentSpec(long studentId) {
        return (root, query, cb) -> {
            Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            Join<Classroom, User> studentsJoin = classroomJoin.join("students", JoinType.INNER);
            return cb.equal(studentsJoin.get("id"), studentId);
        };
    }

    /**
     * Spec tìm kiếm case-insensitive theo tiêu đề phiếu bài tập.
     * Trả về {@code Specification.where(null)} (no-op) nếu keyword rỗng.
     */
    private Specification<AssignmentSheet> buildKeywordSpec(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.where((Specification<AssignmentSheet>) null);
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }

    /**
     * Spec lọc theo mã lớp, dùng INNER JOIN với classroom.
     * Trả về {@code Specification.where(null)} (no-op) nếu classCode rỗng.
     */
    private Specification<AssignmentSheet> buildClassCodeSpec(String classCode) {
        if (classCode == null || classCode.isBlank()) {
            return Specification.where((Specification<AssignmentSheet>) null);
        }
        return (root, query, cb) -> {
            Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            return cb.equal(classroomJoin.get("classCode"), classCode);
        };
    }

    // ─── Response enrichment ─────────────────────────────────────────────────

    /**
     * Enrich danh sách phiếu bài tập với trạng thái nộp bài của học sinh.
     *
     * <p>Thu thập tất cả assignmentIds trong trang, fetch submissions bằng một batch query duy nhất,
     * sau đó gán dữ liệu vào từng item và tính submission status cấp sheet.
     * Tránh N+1 query so với cách gọi DB theo từng sheet/item.
     */
    private void enrichPageForStudent(Page<AssignmentSheetResponse> page, long studentId) {
        List<Long> allAssignmentIds = page.getContent().stream()
                .filter(sheet -> sheet.getItems() != null)
                .flatMap(sheet -> sheet.getItems().stream())
                .map(AssignmentResponse::getId)
                .collect(Collectors.toList());

        if (allAssignmentIds.isEmpty()) return;

        Map<Long, Submission> submissionByAssignmentId = fetchSubmissionsByAssignmentIds(allAssignmentIds, studentId);

        for (AssignmentSheetResponse sheet : page.getContent()) {
            applySubmissionDataToSheetItems(sheet, submissionByAssignmentId);
            sheet.setSubmissionStatus(resolveSheetSubmissionStatus(sheet));
        }
    }

    /**
     * Fetch tất cả submissions của học sinh cho danh sách assignments bằng một truy vấn.
     *
     * <p>Nếu cùng một assignment có nhiều submissions (re-submit),
     * chỉ giữ lại submission có {@code updatedAt} mới nhất.
     *
     * @return Map từ assignmentId sang submission mới nhất của học sinh.
     */
    private Map<Long, Submission> fetchSubmissionsByAssignmentIds(List<Long> assignmentIds, long studentId) {
        List<Submission> submissions = submissionRepository
                .findAllByAssignmentIdInAndStudentId(assignmentIds, studentId);

        // Dùng merge function giữ submission có updatedAt mới nhất nếu có nhiều submission cho cùng assignment
        return submissions.stream()
                .collect(Collectors.toMap(
                        s -> s.getAssignment().getId(),
                        s -> s,
                        (existing, incoming) -> existing.getUpdatedAt().isAfter(incoming.getUpdatedAt())
                                ? existing : incoming
                ));
    }

    /**
     * Gán dữ liệu submission (status, timestamps) vào từng AssignmentResponse trong sheet.
     * Chỉ gán nếu tồn tại submission tương ứng; item chưa nộp bài được giữ nguyên.
     */
    private void applySubmissionDataToSheetItems(
            AssignmentSheetResponse sheet, Map<Long, Submission> submissionByAssignmentId) {
        if (sheet.getItems() == null) return;

        for (AssignmentResponse item : sheet.getItems()) {
            Submission submission = submissionByAssignmentId.get(item.getId());
            if (submission == null) continue;

            item.setSubmissionStatus(submission.getStatus().name());
            item.setSubmissionCreatedAt(submission.getCreatedAt());
            item.setSubmissionUpdatedAt(submission.getUpdatedAt());
        }
    }

    /**
     * Tính submission status cấp sheet dựa trên trạng thái tất cả items.
     * - GRADED  : tất cả items đều có submission GRADED
     * - SUBMITTED: tất cả items có submission (không còn null hay DRAFT)
     * - null    : còn bất kỳ item nào chưa nộp hoặc mới DRAFT
     */
    private String resolveSheetSubmissionStatus(AssignmentSheetResponse sheet) {
        List<AssignmentResponse> items = sheet.getItems();
        if (items == null || items.isEmpty()) return null;

        boolean allGraded = items.stream()
                .allMatch(item -> SubmissionStatus.GRADED.name().equals(item.getSubmissionStatus()));
        if (allGraded) return SubmissionStatus.GRADED.name();

        boolean allSubmittedOrGraded = items.stream()
                .map(AssignmentResponse::getSubmissionStatus)
                .allMatch(status -> SubmissionStatus.SUBMITTED.name().equals(status)
                        || SubmissionStatus.GRADED.name().equals(status));
        if (allSubmittedOrGraded) return SubmissionStatus.SUBMITTED.name();

        return null;
    }

    /**
     * Enrich danh sách phiếu bài tập với danh sách lớp đã publish.
     *
     * <p>Dùng một batch query để lấy {title → [classCode]} cho toàn bộ trang,
     * sau đó gán vào từng sheet. Tránh N queries cho N sheets trong trang.
     */
    private void enrichPageForTeacher(Page<AssignmentSheetResponse> page, long teacherId) {
        List<String> titles = page.getContent().stream()
                .map(AssignmentSheetResponse::getTitle)
                .distinct()
                .collect(Collectors.toList());

        if (titles.isEmpty()) return;

        Map<String, List<String>> publishedCodesByTitle = fetchPublishedCodesByTitles(teacherId, titles);

        for (AssignmentSheetResponse sheet : page.getContent()) {
            List<String> codes = publishedCodesByTitle.getOrDefault(sheet.getTitle(), List.of());
            sheet.setPublishedClassCodes(codes);
        }
    }

    /**
     * Fetch danh sách (title, classCode) của các phiếu đã publish thuộc giáo viên,
     * lọc theo tập tiêu đề đang hiển thị trong trang.
     *
     * @return Map từ title sang danh sách classCode của các lớp đã được publish phiếu đó.
     */
    private Map<String, List<String>> fetchPublishedCodesByTitles(long teacherId, List<String> titles) {
        List<Object[]> rows = assignmentSheetRepository
                .findTitleAndClassCodeByTeacherIdAndTitlesIn(teacherId, titles);

        Map<String, List<String>> result = new HashMap<>();
        for (Object[] row : rows) {
            String title = (String) row[0];
            String classCode = (String) row[1];
            result.computeIfAbsent(title, k -> new ArrayList<>()).add(classCode);
        }
        return result;
    }

    /**
     * Xóa một phiếu bài tập.
     *
     * <p>Cascade delete trên entity sẽ tự động xóa các {@link AssignmentSheetItem} liên quan.
     *
     * @param sheetId   ID của phiếu cần xóa.
     * @param teacherId ID của giáo viên thực hiện thao tác.
     * @throws ResourceNotFoundException nếu phiếu không tồn tại.
     * @throws AccessDeniedException     nếu phiếu không thuộc về giáo viên.
     */
    @Override
    @Transactional
    public void deleteAssignmentSheet(long sheetId, long teacherId) {
        AssignmentSheet sheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (sheet.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xóa phiếu bài tập này");
        }

        assignmentSheetRepository.delete(sheet);
    }

    /**
     * Cập nhật tiêu đề, mô tả và visibility của một phiếu bài tập.
     *
     * <p>Khi tiêu đề thay đổi, toàn bộ phiếu liên quan cùng tiêu đề cũ (Master Sheet và classroom clones)
     * cũng được đồng bộ tiêu đề mới. Điều này đảm bảo tất cả phiếu cùng nhóm luôn nhất quán.
     *
     * @param sheetId   ID của phiếu cần cập nhật.
     * @param request   Thông tin cập nhật mới.
     * @param teacherId ID giáo viên thực hiện thao tác.
     * @return Phiếu bài tập sau khi cập nhật.
     * @throws ResourceNotFoundException nếu phiếu không tồn tại.
     * @throws AccessDeniedException     nếu phiếu không thuộc về giáo viên.
     */
    @Override
    @Transactional
    public AssignmentSheetResponse updateAssignmentSheet(
            long sheetId, UpdateAssignmentSheetRequest request, long teacherId) {

        AssignmentSheet sheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (sheet.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền sửa phiếu bài tập này");
        }

        String oldTitle = sheet.getTitle();
        boolean titleChanged = !oldTitle.equals(request.getTitle());

        sheet.setTitle(request.getTitle());
        sheet.setDescription(request.getDescription() != null ? request.getDescription() : "");
        if (request.getVisibility() != null) {
            sheet.setVisibility(request.getVisibility());
        }
        sheet = assignmentSheetRepository.save(sheet);

        String searchTitle = titleChanged ? oldTitle : sheet.getTitle();
        syncRelatedSheets(teacherId, searchTitle, request, titleChanged);

        return AssignmentSheetResponse.fromEntity(sheet);
    }

    /**
     * Đồng bộ tiêu đề, mô tả và visibility cho tất cả sheet liên quan cùng tên.
     * Được gọi sau khi cập nhật master sheet để đảm bảo classroom clones nhất quán.
     */
    /**
     * Đồng bộ tiêu đề, mô tả và visibility cho tất cả phiếu liên quan cùng tên.
     *
     * <p>Được gọi sau khi cập nhật master sheet để đảm bảo tất cả classroom clones
     * cùng nhóm (cùng tiêu đề, cùng giáo viên) phản ánh thay đổi mới nhất.
     *
     * @param searchTitle Tiêu đề dùng để tìm các phiếu liên quan
     *                    (tiêu đề cũ nếu đổi tên, tiêu đề mới nếu không đổi tên).
     * @param updateTitle {@code true} nếu cần cập nhật cả tiêu đề, {@code false} chỉ cập nhật mô tả/visibility.
     */
    private void syncRelatedSheets(
            long teacherId, String searchTitle,
            UpdateAssignmentSheetRequest request, boolean updateTitle) {

        List<AssignmentSheet> relatedSheets = assignmentSheetRepository
                .findByTeacherIdAndTitle(teacherId, searchTitle);

        for (AssignmentSheet related : relatedSheets) {
            if (updateTitle) {
                related.setTitle(request.getTitle());
            }
            related.setDescription(request.getDescription() != null ? request.getDescription() : "");
            if (request.getVisibility() != null) {
                related.setVisibility(request.getVisibility());
            }
        }
        assignmentSheetRepository.saveAll(relatedSheets);
    }

    /**
     * Lấy danh sách phiếu bài tập công khai (visibility = PUBLIC, không gắn lớp) với phân trang.
     *
     * <p>Phiếu PUBLIC là phiếu nằm trong Thư viện chia sẻ — bất kỳ giáo viên nào cũng có thể xem và clone.
     * Items được load thêm thủ công nếu {@code AssignmentSheetResponse.fromEntity} chưa map được,
     * và lọc bỏ các bài tập đã DELETED.
     *
     * @param keyword  Từ khóa tìm kiếm theo tiêu đề (nullable).
     * @param pageable Thông tin phân trang.
     * @return Trang phiếu bài tập công khai.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable) {
        Specification<AssignmentSheet> baseSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), AssignmentVisibility.PUBLIC),
                cb.isNull(root.get("classroom"))
        );
        Specification<AssignmentSheet> spec = baseSpec.and(buildKeywordSpec(keyword));

        Page<AssignmentSheet> sheets = assignmentSheetRepository.findAll(spec, pageable);
        return sheets.map(sheet -> {
            AssignmentSheetResponse res = AssignmentSheetResponse.fromEntity(sheet);
            if ((res.getItems() == null || res.getItems().isEmpty())
                    && sheet.getItems() != null && !sheet.getItems().isEmpty()) {
                res.setItems(sheet.getItems().stream()
                        .filter(item -> item.getAssignment() != null
                                && item.getAssignment().getStatus() != AssignmentStatus.DELETED)
                        .map(item -> AssignmentResponse.fromEntityWithoutContent(item.getAssignment()))
                        .collect(Collectors.toList()));
            }
            return res;
        });
    }

    /**
     * Clone một phiếu bài tập từ Thư viện vào kho cá nhân của giáo viên.
     *
     * <p>Chỉ cho phép clone phiếu có visibility = PUBLIC.
     * Phiếu mới được tạo ở trạng thái PRIVATE và các bài tập clone có trạng thái DRAFT,
     * giáo viên cần chỉnh sửa và publish lại theo ý muốn.
     *
     * <p>{@code originalAuthor} được giữ lại để truy ngược nguồn gốc của phiếu.
     *
     * @param sheetId   ID của phiếu trong Thư viện cần clone.
     * @param teacherId ID giáo viên thực hiện clone.
     * @return Phiếu bài tập mới trong kho cá nhân của giáo viên.
     * @throws ResourceNotFoundException nếu giáo viên hoặc phiếu không tồn tại.
     * @throws BadRequestException       nếu phiếu không ở trạng thái PUBLIC.
     */
    @Override
    @Transactional
    public AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        AssignmentSheet originalSheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (originalSheet.getVisibility() != AssignmentVisibility.PUBLIC) {
            throw new BadRequestException("Phiếu bài tập này không ở trạng thái công khai trong Thư viện");
        }

        User originalAuthor = originalSheet.getOriginalAuthor() != null
                ? originalSheet.getOriginalAuthor()
                : originalSheet.getTeacher();

        AssignmentSheet clonedSheet = buildLibraryCloneSheet(originalSheet, teacher, originalAuthor);
        clonedSheet = assignmentSheetRepository.save(clonedSheet);

        if (originalSheet.getItems() != null) {
            cloneLibrarySheetItems(originalSheet.getItems(), clonedSheet, teacher, originalAuthor);
        }

        return AssignmentSheetResponse.fromEntity(clonedSheet);
    }

    /**
     * Khởi tạo AssignmentSheet làm bản clone trong kho cá nhân của giáo viên.
     * Visibility mặc định là PRIVATE; {@code originalAuthor} được giữ để ghi nhận tác giả gốc.
     */
    private AssignmentSheet buildLibraryCloneSheet(
            AssignmentSheet original, User teacher, User originalAuthor) {
        AssignmentSheet sheet = new AssignmentSheet();
        sheet.setTitle(original.getTitle());
        sheet.setDescription(original.getDescription());
        sheet.setTeacher(teacher);
        sheet.setOriginalAuthor(originalAuthor);
        sheet.setVisibility(AssignmentVisibility.PRIVATE);
        sheet.setClassroom(null);
        return sheet;
    }

    /**
     * Clone toàn bộ items từ phiếu gốc sang phiếu mới bằng batch operations.
     *
     * <p>Bỏ qua các bài tập đã DELETED. Clone mới có trạng thái DRAFT và visibility PRIVATE.
     * Dùng {@code saveAll} để giảm N INSERT queries xuống còn 2 batch (assignments + items).
     */
    private void cloneLibrarySheetItems(
            List<AssignmentSheetItem> sourceItems, AssignmentSheet clonedSheet,
            User teacher, User originalAuthor) {

        List<Assignment> clonedAssignments = sourceItems.stream()
                .map(AssignmentSheetItem::getAssignment)
                .filter(asgn -> asgn != null && asgn.getStatus() != AssignmentStatus.DELETED)
                .map(asgn -> {
                    Assignment clone = buildAssignmentClone(asgn, teacher, null, null);
                    clone.setOriginalAuthor(originalAuthor);
                    clone.setStatus(AssignmentStatus.DRAFT);
                    clone.setVisibility(AssignmentVisibility.PRIVATE);
                    return clone;
                })
                .collect(Collectors.toList());

        List<Assignment> savedClones = assignmentRepository.saveAll(clonedAssignments);

        List<AssignmentSheetItem> items = savedClones.stream()
                .map(clone -> buildSheetItem(clonedSheet, clone))
                .collect(Collectors.toList());
        assignmentSheetItemRepository.saveAll(items);
    }
}
