package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.request.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.request.UpdateAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.request.UpdateVisibilityRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetSpecification;
import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.mapper.AssignmentSheetMapper;
import com.codegym.mathclass.assignment.service.AssignmentLibraryService;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.assignment.service.helper.AssignmentCloneHelper;
import com.codegym.mathclass.assignment.service.helper.SheetEnrichmentHelper;
import com.codegym.mathclass.utils.DateTimeUtils;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.CompletedStudentProjection;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.assignment.dto.response.SheetCompletedStudentResponse;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
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
import com.codegym.mathclass.exception.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentSheetServiceImpl implements AssignmentSheetService {

    private final AssignmentSheetRepository assignmentSheetRepository;

    private final AssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentCloneHelper assignmentCloneHelper;
    private final AssignmentLibraryService assignmentLibraryService;
    private final SheetEnrichmentHelper sheetEnrichmentHelper;
    private final AssignmentSheetMapper assignmentSheetMapper;
    private final AssignmentMapper assignmentMapper;

    /**
     * Xuất bản một phiếu bài tập tới kho cá nhân (Master Sheet) và tùy chọn tới các
     * lớp học.
     *
     * <p>
     * Luồng xử lý:
     * <ol>
     * <li>Resolve danh sách bài tập gốc từ request hoặc fallback tìm lại từ phiếu
     * cùng tên.</li>
     * <li>Upsert Master Sheet (classroom = null) làm bản lưu trữ trong kho giáo
     * viên.</li>
     * <li>Clone phiếu và bài tập tới từng lớp được chỉ định (nếu có).</li>
     * <li>Archive các bài tập gốc ở trạng thái DRAFT sau khi toàn bộ clone hoàn
     * tất.</li>
     * </ol>
     *
     * @param request   Thông tin phiếu bài tập cần publish, bao gồm danh sách bài
     *                  tập và lớp đích.
     * @param teacherId ID của giáo viên thực hiện thao tác.
     * @throws ResourceNotFoundException nếu giáo viên không tồn tại hoặc lớp học
     *                                   không tìm thấy.
     * @throws AccessDeniedException     nếu một trong các bài tập không thuộc về
     *                                   giáo viên.
     * @throws IllegalArgumentException  nếu không tìm thấy bài tập nào để publish.
     */
    @Override
    @Transactional
    public void publishAssignmentSheet(PublishAssignmentSheetRequest request, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên"));

        List<Assignment> originalAssignments = resolveOriginalAssignments(request, teacherId);

        AssignmentSheet masterSheet = upsertMasterSheet(request, teacher, originalAssignments);

        if (request.getTargets() != null) {
            publishToClassrooms(request.getTargets(), request, teacher, originalAssignments, masterSheet);
        }

        archiveDraftAssignments(originalAssignments);
    }

    // ─── Resolve original assignments ────────────────────────────────────────

    /**
     * Xác định danh sách bài tập gốc cần publish.
     *
     * <p>
     * Có hai path:
     * <ul>
     * <li><b>Path A</b>: {@code request.assignmentIds} được cung cấp → fetch và
     * validate ownership.</li>
     * <li><b>Path B</b>: {@code assignmentIds} rỗng → fallback tìm lại từ phiếu
     * cùng tiêu đề (trường hợp giao lại phiếu cũ).</li>
     * </ul>
     *
     * <p>
     * Ownership validation so sánh số lượng kết quả trả về với số IDs yêu cầu.
     * Nếu không khớp, có thể do ID không tồn tại hoặc thuộc giáo viên khác —
     * cả hai trường hợp đều từ chối để tránh leak thông tin.
     *
     * @throws AccessDeniedException    nếu có bài tập không thuộc
     *                                  {@code teacherId}.
     * @throws IllegalArgumentException nếu fallback cũng không tìm thấy bài tập
     *                                  nào.
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
     * Fallback resolve: tìm lại bài tập gốc từ các phiếu cùng tiêu đề của giáo
     * viên.
     *
     * <p>
     * Duyệt qua phiếu đầu tiên có items hợp lệ:
     * <ul>
     * <li>Nếu item trỏ tới clone (có {@code parentId}), thu thập parentIds rồi
     * batch-fetch một lần.</li>
     * <li>Nếu item trỏ thẳng tới bài gốc (không có {@code parentId}), dùng trực
     * tiếp.</li>
     * </ul>
     *
     * <p>
     * Batch {@code findAllById(parentIds)} thay thế N × {@code findById} trong vòng
     * lặp.
     *
     * @return Danh sách bài tập gốc, hoặc {@code List.of()} nếu không tìm thấy
     *         phiếu nào hợp lệ.
     */
    private List<Assignment> resolveFromExistingSheets(long teacherId, String title) {
        List<AssignmentSheet> sheets = assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, title);

        for (AssignmentSheet sheet : sheets) {
            if (sheet.getItems() == null || sheet.getItems().isEmpty())
                continue;

            List<Long> parentIds = new ArrayList<>();
            List<Assignment> directOriginals = new ArrayList<>();

            for (Assignment asgn : sheet.getItems()) {
                if (asgn == null)
                    continue;
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

            if (!resolved.isEmpty())
                return resolved;
        }
        return List.of();
    }

    // ─── Master Sheet ────────────────────────────────────────────────────

    /**
     * Tạo hoặc lấy Master Sheet (phiếu gốc trong kho, không gắn với lớp nào).
     *
     * <p>
     * Nếu Master Sheet chưa có clones hợp lệ (tất cả items phải trỏ tới bài clone
     * qua {@code parentId}),
     * tiến hành xóa items cũ và tạo lại để đảm bảo tính nhất quán.
     * Điều này xử lý trường hợp phiếu bị chỉnh sửa sau khi tạo lần đầu.
     */
    private AssignmentSheet upsertMasterSheet(
            PublishAssignmentSheetRequest request, User teacher, List<Assignment> originals) {

        AssignmentSheet masterSheet;
        if (request.getMasterSheetId() != null) {
            masterSheet = assignmentSheetRepository.findById(request.getMasterSheetId())
                    .orElseGet(() -> assignmentSheetRepository.save(buildMasterSheet(request, teacher)));
        } else {
            masterSheet = assignmentSheetRepository.save(buildMasterSheet(request, teacher));
        }

        if (!masterSheetHasValidClones(masterSheet)) {
            populateMasterSheetItems(masterSheet, originals, teacher, request.getItemScores());
        }
        return masterSheet;
    }

    /**
     * Khởi tạo AssignmentSheet làm Master Sheet (kho cá nhân, classroom = null).
     * Visibility chỉ được set nếu request cung cấp giá trị, tránh ghi đè giá trị
     * mặc định của entity.
     */
    private AssignmentSheet buildMasterSheet(PublishAssignmentSheetRequest request, User teacher) {
        AssignmentSheet sheet = AssignmentSheet.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .teacher(teacher)
                .classroom(null)
                .build();

        if (request.getVisibility() != null) {
            sheet.setVisibility(request.getVisibility());
        }
        return sheet;
    }

    /**
     * Kiểm tra xem Master Sheet đã có items hợp lệ chưa.
     *
     * <p>
     * "Hợp lệ" nghĩa là tất cả items đều có assignment với {@code parentId} khác
     * null,
     * tức là đã được clone từ bài gốc, không phải bài gốc trực tiếp.
     */
    private boolean masterSheetHasValidClones(AssignmentSheet masterSheet) {
        return masterSheet.getItems() != null
                && !masterSheet.getItems().isEmpty()
                && masterSheet.getItems().stream()
                        .allMatch(item -> item != null
                                && item.getParentId() != null);
    }

    /**
     * Xóa items cũ của Master Sheet (nếu có) rồi tạo mới bằng batch operations.
     *
     * <p>
     * Dùng {@code saveAll} để giảm số lượng INSERT từ N xuống 1 batch cho cả
     * assignments và items.
     * Cascade {@code CascadeType.ALL} trên {@code Assignment.drawings} và
     * {@code .images}
     * đảm bảo drawings/images được persist tự động khi save assignment clone.
     */
    private void populateMasterSheetItems(
            AssignmentSheet masterSheet, List<Assignment> originals, User teacher,
            List<PublishAssignmentSheetRequest.ItemScoreDto> itemScores) {

        if (masterSheet.getItems() != null && !masterSheet.getItems().isEmpty()) {
            assignmentRepository.deleteAll(masterSheet.getItems());
            masterSheet.getItems().clear();
        }

        Map<Long, Double> maxScoreMap = new HashMap<>();
        if (itemScores != null) {
            for (PublishAssignmentSheetRequest.ItemScoreDto score : itemScores) {
                maxScoreMap.put(score.getAssignmentId(), score.getMaxScore());
            }
        }

        List<Assignment> masterClones = originals.stream()
                .map(original -> {
                    Double maxScore = maxScoreMap.get(original.getId());
                    Assignment clone = assignmentCloneHelper.cloneForSheet(original, teacher, null, null, maxScore);
                    clone.setAssignmentSheet(masterSheet);
                    return clone;
                })
                .toList();
        List<Assignment> savedClones = assignmentRepository.saveAll(masterClones);

        masterSheet.getItems().addAll(savedClones);
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
            List<Assignment> originals,
            AssignmentSheet masterSheet) {

        for (PublishAssignmentSheetRequest.TargetClass target : targets) {
            Classroom classroom = classroomRepository.findByClassCode(target.getClassCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy lớp học: " + target.getClassCode()));
            publishToClassroom(target, request, teacher, classroom, originals, masterSheet);
        }
    }

    /**
     * Publish phiếu bài tập tới một lớp học cụ thể.
     *
     * <p>
     * Tạo một AssignmentSheet mới gắn với lớp, sau đó clone toàn bộ bài tập gốc
     * với deadline và classroom tương ứng. Dùng {@code saveAll} để giảm số lượng
     * INSERT.
     */
    private void publishToClassroom(
            PublishAssignmentSheetRequest.TargetClass target,
            PublishAssignmentSheetRequest request,
            User teacher,
            Classroom classroom,
            List<Assignment> originals,
            AssignmentSheet masterSheet) {

        AssignmentSheet clonedSheet = buildClassroomSheet(request, teacher, classroom,
                DateTimeUtils.convertVietnamLocalToUtc(target.getDeadline()), masterSheet);
        clonedSheet = assignmentSheetRepository.save(clonedSheet);

        final AssignmentSheet finalClonedSheet = clonedSheet;

        Map<Long, Double> maxScoreMap = new HashMap<>();
        if (request.getItemScores() != null) {
            for (PublishAssignmentSheetRequest.ItemScoreDto score : request.getItemScores()) {
                maxScoreMap.put(score.getAssignmentId(), score.getMaxScore());
            }
        }

        // fallback to master sheet
        if (masterSheet != null && masterSheet.getItems() != null) {
            for (Assignment item : masterSheet.getItems()) {
                if (item != null && item.getParentId() != null && !maxScoreMap.containsKey(item.getParentId())) {
                    maxScoreMap.put(item.getParentId(), item.getMaxScore());
                }
            }
        }

        List<Assignment> clonedAssignments = originals.stream()
                .map(original -> {
                    Double maxScore = maxScoreMap.get(original.getId());
                    Assignment clone = assignmentCloneHelper.cloneForSheet(original, teacher, classroom,
                            DateTimeUtils.convertVietnamLocalToUtc(target.getDeadline()), maxScore);
                    clone.setAssignmentSheet(finalClonedSheet);
                    return clone;
                })
                .toList();
        List<Assignment> savedClones = assignmentRepository.saveAll(clonedAssignments);

        finalClonedSheet.getItems().addAll(savedClones);
    }

    /**
     * Khởi tạo AssignmentSheet dành cho một lớp học cụ thể.
     * Sheet này là bản clone của Master Sheet, gắn với classroom và có deadline
     * riêng.
     */
    private AssignmentSheet buildClassroomSheet(
            PublishAssignmentSheetRequest request, User teacher,
            Classroom classroom, LocalDateTime deadline, AssignmentSheet masterSheet) {

        return AssignmentSheet.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .deadline(deadline)
                .teacher(teacher)
                .classroom(classroom)
                .masterSheet(masterSheet)
                .build();
    }

    // ─── Archive ────────────────────────────────────────────────────────────

    /**
     * Archive tất cả bài tập gốc đang ở trạng thái DRAFT sau khi publish hoàn tất.
     *
     * <p>
     * Logic archive được tách ra gọi một lần duy nhất sau khi toàn bộ clone hoàn
     * thành,
     * thay vì gọi lặp trong vòng lặp per-class như thiết kế cũ. Điều này đảm bảo:
     * <ul>
     * <li>Không có UPDATE thừa khi publish tới nhiều lớp cùng lúc.</li>
     * <li>Một {@code saveAll} thay thế N × {@code save}.</li>
     * </ul>
     */
    private void archiveDraftAssignments(List<Assignment> originals) {
        List<Assignment> toArchive = originals.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.DRAFT)
                .peek(a -> a.setStatus(AssignmentStatus.ARCHIVED))
                .toList();

        if (!toArchive.isEmpty()) {
            assignmentRepository.saveAll(toArchive);
        }
    }

    // ─── Builder helpers ───────────────────────────────────────────────────

    /**
     * Tạo bản sao (clone) của một bài tập gốc với trạng thái PUBLISHED.
     *
     * <p>
     * Clone sao chép toàn bộ nội dung ({@code title}, {@code description},
     * {@code content}),
     * drawings và images. {@code parentId} được set để truy ngược về bài gốc.
     * Clone <b>chưa được persist</b> — caller chịu trách nhiệm gọi
     * {@code save/saveAll}.
     *
     * <p>
     * Cascade {@code CascadeType.ALL} trên drawings/images đảm bảo chúng
     * được INSERT cùng với assignment khi gọi {@code assignmentRepository.saveAll}.
     *
     * @param original  Bài tập gốc cần clone.
     * @param teacher   Giáo viên sở hữu clone.
     * @param classroom Lớp học gắn với clone, hoặc {@code null} nếu là Master
     *                  clone.
     * @param deadline  Deadline của clone, hoặc {@code null} nếu là Master clone.
     * @return Entity chưa persist, sẵn sàng để {@code saveAll}.
     */

    /**
     * Lấy danh sách phiếu bài tập phân trang theo role của người dùng.
     *
     * <ul>
     * <li><b>TEACHER</b>: xem kho cá nhân (Master Sheets, classroom = null) hoặc
     * lọc theo lớp.</li>
     * <li><b>STUDENT</b>: xem phiếu của các lớp mình tham gia.</li>
     * </ul>
     *
     * <p>
     * Response được enrich thêm dữ liệu phụ (submission status / danh sách lớp đã
     * publish)
     * qua batch queries để tránh N+1.
     *
     * @param userId    ID người dùng hiện tại.
     * @param role      Role của người dùng: "TEACHER" hoặc "STUDENT".
     * @param keyword   Từ khóa tìm kiếm theo tiêu đề (nullable).
     * @param classCode Lọc theo mã lớp (nullable); nếu null và role là TEACHER →
     *                  hiển thị kho.
     * @param pageable  Thông tin phân trang và sắp xếp.
     * @return Trang phiếu bài tập đã được enrich theo role.
     * @throws AccessDeniedException nếu role không hợp lệ.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getAssignmentSheetsForCurrentUser(
            long userId, String role, String keyword, String classCode, String studentStatus, Pageable pageable) {

        Specification<AssignmentSheet> spec = AssignmentSheetSpecification.buildSpecForRole(userId, role, classCode)
                .and(AssignmentSheetSpecification.buildKeywordSpec(keyword))
                .and(AssignmentSheetSpecification.buildClassCodeSpec(classCode));

        if (Role.STUDENT.name().equals(role) && studentStatus != null && !studentStatus.isBlank()) {
            spec = spec.and(AssignmentSheetSpecification.hasStudentStatus(userId, studentStatus));
        }

        Page<AssignmentSheet> sheetPage = assignmentSheetRepository.findAll(spec, pageable);
        Page<AssignmentSheetResponse> responsePage = sheetPage.map(assignmentSheetMapper::toResponse);

        try {
            Role roleEnum = Role.valueOf(role);
            if (roleEnum == Role.STUDENT) {
                sheetEnrichmentHelper.enrichPageForStudent(responsePage, userId);
            } else if (roleEnum == Role.TEACHER) {
                sheetEnrichmentHelper.enrichPageForTeacher(responsePage, userId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role passed for enrichment: {}", role);
        }

        return responsePage;
    }

    /**
     * Xóa một phiếu bài tập.
     *
     * <p>
     * Cascade delete trên entity sẽ tự động xóa các {@link AssignmentSheetItem}
     * liên quan.
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

        // Set master_sheet_id to null for all cloned assignments so they are not
        // deleted
        List<Assignment> clones = assignmentRepository.findByAssignmentSheetId(sheetId);
        if (!clones.isEmpty()) {
            for (Assignment clone : clones) {
                clone.setAssignmentSheet(null);
            }
            assignmentRepository.saveAll(clones);
        }

        // Set master_sheet_id to null for all child sheets so they are not deleted
        List<AssignmentSheet> childSheets = assignmentSheetRepository.findByMasterSheetId(sheetId);
        if (!childSheets.isEmpty()) {
            for (AssignmentSheet childSheet : childSheets) {
                childSheet.setMasterSheet(null);
            }
            assignmentSheetRepository.saveAll(childSheets);
        }

        assignmentSheetRepository.delete(sheet);
    }

    /**
     * Cập nhật tiêu đề, mô tả và visibility của một phiếu bài tập.
     *
     * <p>
     * Khi tiêu đề thay đổi, toàn bộ phiếu liên quan cùng tiêu đề cũ (Master Sheet
     * và classroom clones)
     * cũng được đồng bộ tiêu đề mới. Điều này đảm bảo tất cả phiếu cùng nhóm luôn
     * nhất quán.
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

        if (request.getItemScores() != null) {
            Map<Long, Double> maxScoreMap = new HashMap<>();
            for (UpdateAssignmentSheetRequest.ItemScoreDto score : request.getItemScores()) {
                maxScoreMap.put(score.getAssignmentId(), score.getMaxScore());
            }

            for (Assignment asgn : sheet.getItems()) {
                Double maxScore = maxScoreMap.get(asgn.getId());
                if (maxScore != null) {
                    asgn.setMaxScore(maxScore);
                }
            }
        }

        sheet = assignmentSheetRepository.save(sheet);

        String searchTitle = titleChanged ? oldTitle : sheet.getTitle();
        syncRelatedSheets(teacherId, searchTitle, request, titleChanged);

        return assignmentSheetMapper.toResponse(sheet);
    }

    /**
     * Đồng bộ tiêu đề, mô tả và visibility cho tất cả sheet liên quan cùng tên.
     * Được gọi sau khi cập nhật master sheet để đảm bảo classroom clones nhất quán.
     */
    /**
     * Đồng bộ tiêu đề, mô tả và visibility cho tất cả phiếu liên quan cùng tên.
     *
     * <p>
     * Được gọi sau khi cập nhật master sheet để đảm bảo tất cả classroom clones
     * cùng nhóm (cùng tiêu đề, cùng giáo viên) phản ánh thay đổi mới nhất.
     *
     * @param searchTitle Tiêu đề dùng để tìm các phiếu liên quan
     *                    (tiêu đề cũ nếu đổi tên, tiêu đề mới nếu không đổi tên).
     * @param updateTitle {@code true} nếu cần cập nhật cả tiêu đề, {@code false}
     *                    chỉ cập nhật mô tả/visibility.
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
     * Lấy danh sách phiếu bài tập công khai (visibility = PUBLIC, không gắn lớp)
     * với phân trang.
     *
     * <p>
     * Phiếu PUBLIC là phiếu nằm trong Thư viện chia sẻ — bất kỳ giáo viên nào cũng
     * có thể xem và clone.
     * Items được load thêm thủ công nếu {@code AssignmentSheetResponse.fromEntity}
     * chưa map được,
     * và lọc bỏ các bài tập đã DELETED.
     *
     * @param keyword  Từ khóa tìm kiếm theo tiêu đề (nullable).
     * @param pageable Thông tin phân trang.
     * @return Trang phiếu bài tập công khai.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable) {
        return assignmentLibraryService.getPublicAssignmentSheets(keyword, pageable);
    }

    @Override
    @Transactional
    public AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId) {
        return assignmentLibraryService.cloneAssignmentSheetFromLibrary(sheetId, teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SheetCompletedStudentResponse> getCompletedStudentsBySheet(
            long sheetId, String classCode, Pageable pageable, long teacherId) {

        AssignmentSheet masterSheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (masterSheet.getTeacher().getId() != teacherId
                && masterSheet.getVisibility() != AssignmentVisibility.PUBLIC) {
            throw new AccessDeniedException("Không có quyền truy cập phiếu này");
        }

        AssignmentSheet targetSheet = masterSheet;
        if (classCode != null && !classCode.isEmpty()) {
            if (masterSheet.getClassroom() != null && classCode.equals(masterSheet.getClassroom().getClassCode())) {
                targetSheet = masterSheet;
            } else {
                targetSheet = assignmentSheetRepository.findFirstByTeacherIdAndTitleAndClassroomClassCode(
                        masterSheet.getTeacher().getId(), masterSheet.getTitle(), classCode)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy phiếu bài tập cho lớp " + classCode));
            }
        }

        List<Long> assignmentIds = targetSheet.getItems().stream()
                .map(Assignment::getId)
                .toList();

        if (assignmentIds.isEmpty()) {
            return Page.empty(pageable);
        }

        long totalExercises = assignmentIds.size();
        Page<CompletedStudentProjection> projections = submissionRepository.findCompletedStudentsForSheet(assignmentIds,
                pageable);

        long defaultFirstAssignmentId = targetSheet.getItems().get(0).getId();

        List<Long> studentIds = projections.getContent().stream()
                .map(CompletedStudentProjection::getStudentId)
                .toList();

        final Map<Long, List<Submission>> submissionsByStudent = studentIds.isEmpty() ? new HashMap<>()
                : submissionRepository.findAllByAssignmentIdInAndStudentIdIn(assignmentIds, studentIds)
                        .stream()
                        .filter(sub -> sub.getStatus() != SubmissionStatus.DRAFT)
                        .collect(Collectors.groupingBy(sub -> sub.getStudent().getId()));

        return projections.map(p -> {
            Submission firstSub = submissionsByStudent.getOrDefault(p.getStudentId(), new ArrayList<>())
                    .stream()
                    .findFirst()
                    .orElse(null);

            long firstAssignmentIdToReturn = firstSub != null ? firstSub.getAssignment().getId()
                    : defaultFirstAssignmentId;
            Long firstSubmissionIdToReturn = firstSub != null ? firstSub.getId() : 0L;

            return SheetCompletedStudentResponse.builder()
                    .studentId(p.getStudentId())
                    .studentName(p.getStudentName())
                    .studentEmail(p.getStudentEmail())
                    .completedExercisesCount(p.getCompletedCount())
                    .totalExercisesCount((int) totalExercises)
                    .latestSubmittedAt(p.getLatestSubmittedAt())
                    .totalScore(p.getTotalScore())
                    .firstAssignmentId(firstAssignmentIdToReturn)
                    .firstSubmissionId(firstSubmissionIdToReturn)
                    .build();
        });
    }

    @Override
    @Transactional
    public AssignmentSheetResponse updateAssignmentSheetVisibility(long sheetId, UpdateVisibilityRequest request,
            long teacherId) {
        AssignmentSheet sheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (sheet.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi visibility của phiếu bài tập này");
        }

        sheet.setVisibility(request.getVisibility());
        AssignmentSheet saved = assignmentSheetRepository.save(sheet);
        return assignmentSheetMapper.toResponse(saved);
    }
}
