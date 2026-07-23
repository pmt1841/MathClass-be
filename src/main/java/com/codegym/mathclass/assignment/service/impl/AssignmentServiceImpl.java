package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.codegym.mathclass.assignment.repository.AssignmentSpecification;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import org.springframework.data.jpa.domain.Specification;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import com.codegym.mathclass.assignment.dto.AssignmentDrawingRequest;
import com.codegym.mathclass.assignment.dto.AssignmentImageRequest;
import com.codegym.mathclass.utils.EmailService;
import org.thymeleaf.context.Context;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentMapper assignmentMapper;
    private final SupabaseStorageService supabaseStorageService;
    private final EmailService emailService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional
    public AssignmentResponse createAssignment(CreateAssignmentRequest request, long teacherId) {
        // 1. Tìm giáo viên
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Chỉ giáo viên mới có quyền tạo bài tập");
        }

        // 3. Validate LaTeX trong nội dung bài tập
        if (request.getContent() != null && !LaTeXSanitizer.isSafe(request.getContent())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getContent());
            throw new IllegalArgumentException(
                    "Nội dung chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 4. Tạo bài tập với trạng thái DRAFT, chưa gán lớp và chưa có deadline
        Assignment assignment = Assignment.builder()
                .title(request.getTitle() != null ? request.getTitle() : "")
                .description(request.getDescription() != null ? request.getDescription() : "")
                .content(request.getContent() != null ? request.getContent() : "")
                .status(AssignmentStatus.DRAFT)
                .teacher(teacher)
                .classroom(null)
                .build();
        // deadline = null cho đến khi giáo viên publish

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());

        Assignment saved = assignmentRepository.save(assignment);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional
    public void publishAssignment(long assignmentId, PublishAssignmentRequest request, long teacherId) {
        // 1. Tìm bài tập
        Assignment originalAssignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        validateTeacherOwnership(originalAssignment, teacherId, "Bạn không có quyền publish bài tập này");

        // 3. Kiểm tra trạng thái – chỉ publish được khi đang là DRAFT
        if (originalAssignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BadRequestException("Bài tập đã được publish hoặc archive trước đó");
        }

        // 3.1 Validate đầy đủ thông tin trước khi publish
        if (originalAssignment.getTitle() == null || originalAssignment.getTitle().trim().isEmpty() ||
                originalAssignment.getDescription() == null || originalAssignment.getDescription().trim().isEmpty() ||
                originalAssignment.getContent() == null || originalAssignment.getContent().trim().isEmpty()) {
            throw new BadRequestException("Vui lòng điền đầy đủ Tiêu đề, Mô tả và Nội dung trước khi Giao bài");
        }

        List<Assignment> clones = new ArrayList<>();

        // 4. Lặp qua các lớp đích và clone bài tập
        for (PublishAssignmentRequest.TargetClass target : request.getTargets()) {
            String classCode = target.getClassCode();
            Classroom classroom = classroomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy lớp học với mã: " + classCode));

            if (classroom.getTeacher().getId() != teacherId) {
                throw new AccessDeniedException(
                        "Bạn không có quyền giao bài tập cho lớp: " + classCode);
            }

            Assignment clone = cloneAssignmentForClassroom(originalAssignment, classroom, target.getDeadline());
            clones.add(clone);
        }

        // 5. Lưu tất cả bản clone
        assignmentRepository.saveAll(clones);

        // Gửi email cho từng học sinh trong lớp học
        for (Assignment clone : clones) {
            sendAssignmentNotificationToClassroom(clone, clone.getClassroom());
        }

        // 6. Cập nhật trạng thái bản nháp thành ARCHIVED nếu như đang là DRAFT
        if (originalAssignment.getStatus() == AssignmentStatus.DRAFT) {
            originalAssignment.setStatus(AssignmentStatus.ARCHIVED);
        }
        assignmentRepository.save(originalAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, long userId, String keyword,
            AssignmentStatus status, Pageable pageable) {
        // 1. Tìm lớp học
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với mã: " + classCode));

        // 2. Kiểm tra quyền truy cập (giáo viên hoặc học sinh của lớp)
        boolean isTeacher = classroom.getTeacher().getId() == userId;
        boolean isStudent = classroom.getStudents().stream().anyMatch(student -> student.getId() == userId);

        if (!isTeacher && !isStudent) {
            throw new AccessDeniedException("Bạn không có quyền xem bài tập của lớp này");
        }

        Specification<Assignment> spec = Specification.where((root, query, cb) -> {
            Join<Assignment, Classroom> classroomJoin = root.join("classroom",
                    JoinType.LEFT);
            // Lấy các bài tập của lớp này
            Predicate isClassCode = cb.equal(classroomJoin.get("classCode"), classCode);

            if (isTeacher) {
                // Giáo viên thấy bài tập của lớp HOẶC các bản nháp của chính họ
                Predicate isDraftAndMyTeacher = cb.and(
                        cb.equal(root.get("status"), AssignmentStatus.DRAFT),
                        cb.equal(root.get("teacher").get("id"), userId));
                return cb.or(isClassCode, isDraftAndMyTeacher);
            } else {
                // Học sinh chỉ thấy bài tập của lớp đó
                return isClassCode;
            }
        });

        // Lọc theo keyword (tiêu đề)
        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasTitleContaining(keyword));
        }

        // Lọc theo status
        if (status != null) {
            if (isStudent && status != AssignmentStatus.PUBLISHED) {
                return Page.empty(pageable);
            }
            spec = spec.and(AssignmentSpecification.hasStatus(status));
        } else {
            if (isStudent) {
                spec = spec.and(AssignmentSpecification.hasStatus(AssignmentStatus.PUBLISHED));
            }
        }

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(
                    Sort.Order.asc("status"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("createdAt"));
        }
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), sort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (isStudent) {
                submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                        .ifPresent(sub -> {
                            response.setSubmissionStatus(sub.getStatus().name());
                            response.setSubmissionCreatedAt(sub.getCreatedAt());
                            response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                        });
            }
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsForCurrentUser(long userId, String role, String keyword,
            String classCode, AssignmentStatus status, Pageable pageable) {
        Specification<Assignment> spec = Specification.where((root, query, cb) -> cb.conjunction());

        // 1. Phân quyền truy cập cơ bản theo Role
        if (Role.TEACHER.name().equals(role)) {
            spec = spec.and(AssignmentSpecification.isTeacher(userId));
        } else if (Role.STUDENT.name().equals(role)) {
            // Học sinh chỉ xem được bài tập PUBLISHED
            if (status != null && status != AssignmentStatus.PUBLISHED) {
                // Trả về rỗng nếu cố tình lọc các trạng thái không được phép
                return Page.empty(pageable);
            }
            spec = spec.and(AssignmentSpecification.isStudent(userId))
                    .and(AssignmentSpecification.hasStatus(AssignmentStatus.PUBLISHED));
        } else {
            throw new AccessDeniedException("Role không hợp lệ");
        }

        // 2. Lọc theo keyword (tiêu đề)
        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasTitleContaining(keyword));
        }

        // 3. Lọc theo classCode
        if (classCode != null && !classCode.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasClassCode(classCode));
        }

        // 4. Lọc theo status (nếu là TEACHER thì có thể filter tùy ý, STUDENT thì
        // status luôn là PUBLISHED đã set ở trên)
        if (status != null && Role.TEACHER.name().equals(role)) {
            spec = spec.and(AssignmentSpecification.hasStatus(status));
        }

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(
                    Sort.Order.asc("status"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("createdAt"));
        }
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), sort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (Role.STUDENT.name().equals(role)) {
                submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                        .ifPresent(sub -> {
                            response.setSubmissionStatus(sub.getStatus().name());
                            response.setSubmissionCreatedAt(sub.getCreatedAt());
                            response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                        });
            }
            return response;
        });
    }

    @Override
    @Transactional
    public void deleteAssignment(long assignmentId, long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài tập này");
        }

        // Kiểm tra xem đã có submission hay chưa
        if (submissionRepository.existsByAssignmentId(assignmentId)) {
            throw new BadRequestException("Đã có học sinh nộp bài, không thể xóa bài tập này");
        }

        // 3. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            // Bản nháp -> xóa cứng
            assignmentRepository.delete(assignment);
        } else {
            // Không phải nháp -> xóa mềm
            // Nếu là bài gốc (ARCHIVED), các bản clone không bị xóa/ẩn mà đổi parentId =
            // null
            if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
                List<Assignment> clones = assignmentRepository.findByParentId(assignment.getId());
                for (Assignment clone : clones) {
                    clone.setParentId(null);
                }
                assignmentRepository.saveAll(clones);
            }

            assignment.setStatus(AssignmentStatus.DELETED);
            assignmentRepository.save(assignment);
        }
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignment(long assignmentId, UpdateAssignmentRequest request, long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        validateTeacherOwnership(assignment, teacherId, "Bạn không có quyền sửa bài tập này");

        // 3. Từ chối nếu đã bị xóa
        if (assignment.getStatus() == AssignmentStatus.DELETED) {
            throw new BadRequestException("Không thể sửa bài tập đã bị xóa");
        }

        // 4. Validate LaTeX trong nội dung mới
        if (request.getContent() != null && !LaTeXSanitizer.isSafe(request.getContent())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getContent());
            throw new IllegalArgumentException(
                    "Nội dung chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 4.1 Validate bắt buộc nếu không phải DRAFT
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            if (request.getTitle() == null || request.getTitle().trim().isEmpty() ||
                    request.getDescription() == null || request.getDescription().trim().isEmpty() ||
                    request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new BadRequestException(
                        "Tiêu đề, Mô tả và Nội dung không được để trống khi bài tập đã được Giao");
            }
        }

        // 5. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            updateDraftAssignment(assignment, request);
            assignmentRepository.save(assignment);
        } else if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            updateArchivedAssignment(assignment, request);
            assignmentRepository.save(assignment);
        } else if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            updatePublishedAssignment(assignment, request);
            assignmentRepository.save(assignment);
        }

        return assignmentMapper.toAssignmentResponse(assignment);
    }

    @Override
    public AssignmentImageDto uploadImageForAssignment(org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        String publicUrl = supabaseStorageService.uploadImage(file, "assignment_image");
        String imageCode = "[IMAGE_" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";
        return new AssignmentImageDto(imageCode, publicUrl);
    }

    @Override
    public java.util.Map<String, Object> extractTextFromFile(org.springframework.web.multipart.MultipartFile file)
            throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BadRequestException("Tên file không hợp lệ");
        }

        filename = filename.toLowerCase();

        if (filename.endsWith(".txt")) {
            return java.util.Map.of("content", new String(file.getBytes(), StandardCharsets.UTF_8), "images",
                    new ArrayList<>());
        } else if (filename.endsWith(".docx")) {
            try (java.io.InputStream is = file.getInputStream();
                    XWPFDocument document = new XWPFDocument(is)) {
                List<AssignmentImageDto> extractedImages = new ArrayList<>();
                String content = convertDocxToMarkdown(document, extractedImages);
                return java.util.Map.of("content", content, "images", extractedImages);
            }
        } else if (filename.endsWith(".pdf")) {
            try (java.io.InputStream is = file.getInputStream();
                    PDDocument document = PDDocument.load(is)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String content = stripper.getText(document);

                // Normalize newlines and replace single newlines with double newlines
                // so Markdown parses them as separate paragraphs/lines instead of collapsing
                // them.
                if (content != null) {
                    content = content.replaceAll("\\r\\n?", "\n");
                    content = content.replaceAll("\\n+", "\n\n");
                }

                return java.util.Map.of("content", content != null ? content : "", "images", new ArrayList<>());
            }
        } else {
            throw new BadRequestException("Chỉ hỗ trợ file .txt, .docx, hoặc .pdf");
        }
    }

    private String convertDocxToMarkdown(XWPFDocument document, List<AssignmentImageDto> extractedImages) {
        StringBuilder md = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                md.append(processParagraph((XWPFParagraph) element, extractedImages));
            } else if (element instanceof XWPFTable) {
                md.append(processTable((XWPFTable) element, extractedImages));
            }
        }
        return md.toString().trim();
    }

    private String processParagraph(XWPFParagraph p, List<AssignmentImageDto> extractedImages) {
        if (p.isEmpty() || (p.getText().trim().isEmpty()
                && p.getRuns().stream().noneMatch(r -> !r.getEmbeddedPictures().isEmpty()))) {
            return "\n";
        }

        String style = p.getStyleID();
        String prefix = "";
        if (style != null) {
            if (style.contains("Heading1") || "1".equals(style))
                prefix = "# ";
            else if (style.contains("Heading2") || "2".equals(style))
                prefix = "## ";
            else if (style.contains("Heading3") || "3".equals(style))
                prefix = "### ";
            else if (style.contains("Heading4") || "4".equals(style))
                prefix = "#### ";
            else if (style.contains("Heading5") || "5".equals(style))
                prefix = "##### ";
            else if (style.contains("Heading6") || "6".equals(style))
                prefix = "###### ";
        }

        String listPrefix = "";
        if (p.getNumID() != null) {
            // Determine indentation based on level
            int level = p.getNumIlvl() != null ? p.getNumIlvl().intValue() : 0;
            listPrefix = "  ".repeat(level) + "- ";
        }

        StringBuilder paraMd = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String runText = run.text();
            if (runText != null && !runText.isEmpty()) {
                boolean bold = run.isBold();
                boolean italic = run.isItalic();

                runText = runText.replace("\n", " ");

                if (bold || italic) {
                    // Extract leading spaces
                    String leadingSpaces = "";
                    while (runText.startsWith(" ")) {
                        leadingSpaces += " ";
                        runText = runText.substring(1);
                    }
                    // Extract trailing spaces
                    String trailingSpaces = "";
                    while (runText.endsWith(" ")) {
                        trailingSpaces += " ";
                        runText = runText.substring(0, runText.length() - 1);
                    }

                    paraMd.append(leadingSpaces);
                    if (!runText.isEmpty()) {
                        if (bold && italic)
                            paraMd.append("***").append(runText).append("***");
                        else if (bold)
                            paraMd.append("**").append(runText).append("**");
                        else if (italic)
                            paraMd.append("*").append(runText).append("*");
                    }
                    paraMd.append(trailingSpaces);
                } else {
                    paraMd.append(runText);
                }
            }

            // Xử lý ảnh nhúng
            List<XWPFPicture> pictures = run.getEmbeddedPictures();
            if (pictures != null && !pictures.isEmpty()) {
                for (XWPFPicture pic : pictures) {
                    try {
                        XWPFPictureData picData = pic.getPictureData();
                        byte[] byteData = picData.getData();
                        String ext = picData.suggestFileExtension();
                        String originalName = picData.getFileName();
                        if (originalName == null || originalName.isEmpty()) {
                            originalName = "image." + ext;
                        }
                        String contentType = picData.getPackagePart().getContentType();

                        // Upload to Supabase
                        String publicUrl = supabaseStorageService.uploadImage(byteData, originalName, contentType,
                                "assignment_image");

                        // Generate unique code
                        String imageCode = "[IMAGE_"
                                + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";

                        // Add to list and markdown
                        extractedImages.add(new AssignmentImageDto(imageCode, publicUrl));
                        paraMd.append(" ").append(imageCode).append(" ");
                    } catch (Exception e) {
                        System.err.println("Failed to extract and upload image: " + e.getMessage());
                    }
                }
            }
        }

        if (!prefix.isEmpty()) {
            return prefix + paraMd.toString() + "\n\n";
        } else if (!listPrefix.isEmpty()) {
            return listPrefix + paraMd.toString() + "\n";
        } else {
            return paraMd.toString() + "\n\n";
        }
    }

    private String processTable(XWPFTable table, List<AssignmentImageDto> extractedImages) {
        StringBuilder tableMd = new StringBuilder("\n");
        int rowIndex = 0;
        for (XWPFTableRow row : table.getRows()) {
            tableMd.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellContent = new StringBuilder();
                for (IBodyElement element : cell.getBodyElements()) {
                    if (element instanceof XWPFParagraph) {
                        String pText = processParagraph((XWPFParagraph) element, extractedImages).trim();
                        if (!pText.isEmpty()) {
                            if (cellContent.length() > 0)
                                cellContent.append("<br>");
                            cellContent.append(pText);
                        }
                    } else if (element instanceof XWPFTable) {
                        cellContent.append("[Nested Table]");
                    }
                }
                // escape pipe char if exists in cell content
                tableMd.append(" ").append(cellContent.toString().replace("|", "\\|")).append(" |");
            }
            tableMd.append("\n");

            // Add separator after first row
            if (rowIndex == 0) {
                tableMd.append("|");
                for (int i = 0; i < row.getTableCells().size(); i++) {
                    tableMd.append("---|");
                }
                tableMd.append("\n");
            }
            rowIndex++;
        }
        return tableMd.toString() + "\n";
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(long assignmentId, long userId, String role) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (Role.TEACHER.name().equals(role)) {
            if (assignment.getTeacher().getId() != userId) {
                throw new AccessDeniedException("Bạn không có quyền xem bài tập này");
            }
        } else if (Role.STUDENT.name().equals(role)) {
            if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
                throw new AccessDeniedException("Bạn không thể xem bài tập này");
            }
            if (assignment.getClassroom() == null) {
                throw new BadRequestException("Bài tập chưa được giao cho lớp nào");
            }
            boolean isStudentInClass = assignment.getClassroom().getStudents().stream()
                    .anyMatch(student -> student.getId() == userId);
            if (!isStudentInClass) {
                throw new AccessDeniedException("Bạn không có quyền xem bài tập này");
            }
        } else {
            throw new AccessDeniedException("Vai trò không hợp lệ");
        }

        AssignmentResponse response = assignmentMapper.toAssignmentResponse(assignment);

        if (Role.STUDENT.name().equals(role)) {
            submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                    .ifPresent(sub -> {
                        response.setSubmissionStatus(sub.getStatus().name());
                        response.setSubmissionCreatedAt(sub.getCreatedAt());
                        response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                    });
        }

        return response;
    }

    private void validateTeacherOwnership(Assignment assignment, long teacherId, String errorMessage) {
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException(errorMessage);
        }
    }

    private void updateDrawings(Assignment assignment, List<AssignmentDrawingRequest> drawingReqs) {
        if (drawingReqs != null) {
            assignment.getDrawings().clear();
            for (var drawingReq : drawingReqs) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(drawingReq.getShapeCode());
                drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                drawing.setAssignment(assignment);
                assignment.getDrawings().add(drawing);
            }
        }
    }

    private void updateImages(Assignment assignment, List<AssignmentImageRequest> imageReqs) {
        if (imageReqs != null) {
            assignment.getImages().clear();
            for (var imageReq : imageReqs) {
                AssignmentImage img = new AssignmentImage();
                img.setImageCode(imageReq.getImageCode());
                img.setImageUrl(imageReq.getImageUrl());
                img.setAssignment(assignment);
                assignment.getImages().add(img);
            }
        }
    }

    private void updateDraftAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        assignment.setTitle(request.getTitle() != null ? request.getTitle() : "");
        assignment.setDescription(request.getDescription() != null ? request.getDescription() : "");
        assignment.setContent(request.getContent() != null ? request.getContent() : "");

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());
    }

    private void updateArchivedAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setContent(request.getContent());

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());

        List<Assignment> publishedClones = assignmentRepository.findByParentId(assignment.getId());
        for (Assignment clone : publishedClones) {
            if (submissionRepository.existsByAssignmentId(clone.getId())) {
                continue;
            }
            clone.setTitle(request.getTitle());
            clone.setDescription(request.getDescription());
            clone.setContent(request.getContent());

            updateDrawings(clone, request.getDrawings());
            updateImages(clone, request.getImages());
        }
        assignmentRepository.saveAll(publishedClones);
    }

    private void updatePublishedAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        boolean hasSubmissions = submissionRepository.existsByAssignmentId(assignment.getId());

        if (hasSubmissions) {
            if (!assignment.getTitle().equals(request.getTitle()) ||
                    !assignment.getDescription().equals(request.getDescription()) ||
                    !assignment.getContent().equals(request.getContent())) {
                throw new BadRequestException("Bài tập đã có học sinh nộp bài, bạn chỉ có thể thay đổi hạn nộp");
            }
            if (request.getDeadline() != null) {
                assignment.setDeadline(request.getDeadline());
            }
        } else {
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            assignment.setContent(request.getContent());
            if (request.getDeadline() != null) {
                assignment.setDeadline(request.getDeadline());
            }
            updateDrawings(assignment, request.getDrawings());
            updateImages(assignment, request.getImages());
        }
    }

    private Assignment cloneAssignmentForClassroom(Assignment original, Classroom classroom,
            java.time.LocalDateTime deadline) {
        Assignment clone = new Assignment();
        clone.setTitle(original.getTitle());
        clone.setDescription(original.getDescription());
        clone.setContent(original.getContent());
        clone.setTeacher(original.getTeacher());
        clone.setParentId(original.getId());
        clone.setClassroom(classroom);
        clone.setDeadline(deadline);
        clone.setStatus(AssignmentStatus.PUBLISHED);

        if (original.getDrawings() != null) {
            for (AssignmentDrawing originalDrawing : original.getDrawings()) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(originalDrawing.getShapeCode());
                drawing.setJsxGraphData(originalDrawing.getJsxGraphData());
                drawing.setAssignment(clone);
                clone.getDrawings().add(drawing);
            }
        }

        if (original.getImages() != null) {
            for (AssignmentImage originalImage : original.getImages()) {
                AssignmentImage image = new AssignmentImage();
                image.setImageCode(originalImage.getImageCode());
                image.setImageUrl(originalImage.getImageUrl());
                image.setAssignment(clone);
                clone.getImages().add(image);
            }
        }
        return clone;
    }

    private void sendAssignmentNotificationToClassroom(Assignment clone, Classroom classroom) {
        if (classroom != null && classroom.getStudents() != null) {
            for (User student : classroom.getStudents()) {
                Context context = new Context();
                context.setVariable("studentName", student.getFullName());
                context.setVariable("assignmentName", clone.getTitle());
                context.setVariable("link", frontendUrl + "/assignments/" + clone.getId());

                emailService.sendHtmlMailAsync(
                        student.getEmail(),
                        "Bài tập mới: " + clone.getTitle(),
                        "assignment-notification",
                        context);
            }
        }
    }
}
