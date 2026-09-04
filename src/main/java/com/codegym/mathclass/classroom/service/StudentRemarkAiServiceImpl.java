package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluateRequest;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluationResponse;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentRemarkAiServiceImpl implements StudentRemarkAiService {

    public static final String TASK_STUDENT_REMARK = "STUDENT_REMARK";
    public static final String PROMPT_CODE = "PROMPT_STUDENT_REMARK";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SystemPromptRepository systemPromptRepository;
    private final AiPromptExecutionService aiPromptExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiStudentRemarkEvaluationResponse evaluateStudentProgress(
            String classCode,
            Long studentId,
            Long currentUserId,
            AiStudentRemarkEvaluateRequest request) {
        return evaluateStudentProgress(classCode, studentId, currentUserId, request, true);
    }

    @Override
    public AiStudentRemarkEvaluationResponse evaluateStudentProgress(
            String classCode,
            Long studentId,
            Long currentUserId,
            AiStudentRemarkEvaluateRequest request,
            boolean chargeCredits) {

        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        if (!Objects.equals(classroom.getTeacher().getId(), currentUserId)) {
            throw new AccessDeniedException("Chỉ giáo viên phụ trách lớp mới có quyền yêu cầu AI đánh giá học sinh");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));

        boolean isMember = classroom.getStudents().stream().anyMatch(s -> Objects.equals(s.getId(), studentId));
        if (!isMember) {
            throw new BadRequestException("Học sinh không thuộc lớp học này");
        }

        // Xác định khoảng thời gian quét
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        LocalDate startDate = request.getStartDate();
        if (startDate == null) {
            int days = (request.getDays() != null && request.getDays() > 0) ? request.getDays() : 7;
            startDate = endDate.minusDays(days);
        }

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. Quét danh sách bài tập đã giao trong lớp trong khoảng thời gian này
        List<Assignment> assignments = assignmentRepository.findPublishedAssignmentsByClassCodeAndDateRange(
                classCode, startDateTime, endDateTime);

        int totalAssignments = assignments.size();
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();

        // 2. Quét danh sách bài nộp của học sinh
        List<Submission> submissions = assignmentIds.isEmpty()
                ? Collections.emptyList()
                : submissionRepository.findAllByAssignmentIdInAndStudentId(assignmentIds, studentId);

        Map<Long, Submission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), s -> s, (s1, s2) -> s1));

        int completedAssignments = 0;
        int overdueAssignments = 0;
        int activeIncompleteAssignments = 0;
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Double> scores = new ArrayList<>();
        StringBuilder detailsBuilder = new StringBuilder();

        if (assignments.isEmpty()) {
            detailsBuilder.append("Không có bài tập nào được giao trong lớp trong khoảng thời gian này.\n");
        } else {
            for (int i = 0; i < assignments.size(); i++) {
                Assignment a = assignments.get(i);
                Submission s = submissionMap.get(a.getId());
                boolean isCompleted = s != null && s.getStatus() != SubmissionStatus.DRAFT;
                boolean hasDeadline = a.getDeadline() != null;
                boolean isOverdue = hasDeadline && now.isAfter(a.getDeadline());

                String deadlineStr = hasDeadline ? a.getDeadline().format(dateTimeFormatter) : "Không giới hạn";
                String deadlineStatus = !hasDeadline
                        ? "Không giới hạn thời gian"
                        : (isOverdue ? "ĐÃ HẾT HẠN NỘP" : "VẪN CÒN HẠN LÀM");

                detailsBuilder.append(String.format("Bài %d: \"%s\" (Tối đa %.1f điểm)\n",
                        i + 1, a.getTitle(), a.getMaxScore() != null ? a.getMaxScore() : 10.0));
                detailsBuilder.append(String.format("  - Hạn nộp: %s (%s)\n", deadlineStr, deadlineStatus));

                if (isCompleted) {
                    completedAssignments++;
                    if (s.getScore() != null) {
                        scores.add(s.getScore());
                    }
                    detailsBuilder.append(String.format("  - Trạng thái: ĐÃ NỘP BÀI (%s)\n", s.getStatus().name()));
                    detailsBuilder.append(String.format("  - Điểm số đạt được: %s\n", s.getScore() != null ? s.getScore() : "Chưa chấm"));
                    if (s.getTeacherFeedback() != null && !s.getTeacherFeedback().isBlank()) {
                        detailsBuilder.append(String.format("  - Nhận xét của giáo viên: %s\n", s.getTeacherFeedback().trim()));
                    }
                    if (s.getContent() != null && !s.getContent().isBlank()) {
                        String snippet = s.getContent().trim();
                        if (snippet.length() > 300) {
                            snippet = snippet.substring(0, 300) + "...";
                        }
                        detailsBuilder.append(String.format("  - Trích đoạn bài làm: %s\n", snippet));
                    }
                } else {
                    if (isOverdue) {
                        overdueAssignments++;
                        detailsBuilder.append(String.format("  - Trạng thái: CHƯA NỘP BÀI (ĐÃ QUÁ HẠN từ %s)\n", deadlineStr));
                    } else {
                        activeIncompleteAssignments++;
                        detailsBuilder.append(String.format("  - Trạng thái: CHƯA NỘP BÀI (VẪN CÒN HẠN ĐẾN %s)\n", deadlineStr));
                    }
                }
                detailsBuilder.append("\n");
            }
        }

        Double averageScore = scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // 3. Xây dựng prompt AI từ SystemPrompt
        String promptTemplate = getPromptTemplate();
        String formattedStartDate = startDate.format(DATE_FORMATTER);
        String formattedEndDate = endDate.format(DATE_FORMATTER);

        String fullPrompt = promptTemplate
                .replace("{{student_name}}", student.getFullName() != null ? student.getFullName() : "Học sinh")
                .replace("{{class_name}}", classroom.getClassName() != null ? classroom.getClassName() : classCode)
                .replace("{{start_date}}", formattedStartDate)
                .replace("{{end_date}}", formattedEndDate)
                .replace("{{total_assignments}}", String.valueOf(totalAssignments))
                .replace("{{completed_assignments}}", String.valueOf(completedAssignments))
                .replace("{{overdue_assignments}}", String.valueOf(overdueAssignments))
                .replace("{{active_incomplete_assignments}}", String.valueOf(activeIncompleteAssignments))
                .replace("{{submission_details}}", detailsBuilder.toString().trim());

        // 4. Gọi thực thi AI qua AiPromptExecutionService (tự trừ credit nếu chargeCredits = true, tối thiểu 5 credit / 1000 tokens)
        String rawAiOutput = chargeCredits
                ? aiPromptExecutionService.executePrompt(TASK_STUDENT_REMARK, fullPrompt, currentUserId)
                : aiPromptExecutionService.executePrompt(TASK_STUDENT_REMARK, fullPrompt, currentUserId, false);

        // 5. Parse kết quả từ AI
        AiRemarkJsonResult parsed = parseAiOutput(rawAiOutput, formattedStartDate, formattedEndDate, totalAssignments, completedAssignments);

        return AiStudentRemarkEvaluationResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalAssignments(totalAssignments)
                .completedAssignments(completedAssignments)
                .overdueAssignments(overdueAssignments)
                .activeIncompleteAssignments(activeIncompleteAssignments)
                .averageScore(averageScore != null ? Math.round(averageScore * 100.0) / 100.0 : null)
                .strengths(parsed.getStrengths())
                .weaknesses(parsed.getWeaknesses())
                .generalAssessment(parsed.getGeneralAssessment())
                .build();
    }

    private String getPromptTemplate() {
        return systemPromptRepository.findByCode(PROMPT_CODE)
                .map(SystemPrompt::getCurrentContent)
                .filter(content -> content != null && !content.isBlank())
                .orElse(getDefaultPrompt());
    }

    private String getDefaultPrompt() {
        return """
                Bạn là một trợ lý AI sư phạm môn Toán chuyên nghiệp, tinh tế và tận tâm.
                Nhiệm vụ của bạn là phân tích dữ liệu học tập của học sinh trong lớp học dựa trên các bài tập đã giao, hạn nộp và kết quả làm bài của học sinh trong khoảng thời gian xác định, từ đó đưa ra nhận xét, đánh giá toàn diện, chính xác, khích lệ và mang tính xây dựng.

                THÔNG TIN HỌC SINH & LỚP HỌC:
                - Tên học sinh: {{student_name}}
                - Lớp học: {{class_name}}
                - Khoảng thời gian quét: từ ngày {{start_date}} đến ngày {{end_date}}
                - Thống kê bài tập:
                  + Tổng số bài tập được giao: {{total_assignments}}
                  + Số bài đã hoàn thành: {{completed_assignments}}
                  + Số bài chưa nộp nhưng ĐÃ QUÁ HẠN: {{overdue_assignments}}
                  + Số bài chưa nộp nhưng VẪN CÒN HẠN LÀM: {{active_incomplete_assignments}}

                CHI TIẾT TỪNG BÀI TẬP VÀ TRẠNG THÁI:
                {{submission_details}}

                QUY TẮC ĐÁNH GIÁ SƯ PHẠM (RẤT QUAN TRỌNG):
                1. Phân biệt rõ giữa bài tập ĐÃ HẾT HẠN và bài tập CÒN HẠN LÀM:
                   - Nếu học sinh mới hoàn thành một phần bài tập (ví dụ 1/5 bài) nhưng các bài còn lại VẪN CÒN TRONG HẠN NỘP:
                     + Hãy đánh giá với giọng điệu nhẹ nhàng, ghi nhận những bài đã làm và nhắc nhở khéo léo, động viên: "Em chú ý sắp xếp thời gian hợp lý để hoàn thành các bài tập còn lại trước thời hạn quy định nhé."
                     + TUYỆT ĐỐI KHÔNG dùng từ ngữ nặng nề, không vội phán xét học sinh lười biếng hay lơ là vì các bài tập này vẫn đang trong thời hạn làm bài hợp lệ.
                   - Nếu học sinh có bài tập ĐÃ QUÁ HẠN mà chưa nộp:
                     + Nhắc nhở nghiêm túc hơn về tính tự giác và tuân thủ thời hạn, khuyên học sinh chủ động hoàn thành hoặc liên hệ thầy/cô nếu gặp vướng mắc.
                2. Điểm mạnh (strengths): Nêu rõ các điểm học sinh làm tốt (tư duy logic, giải đúng các dạng toán nào, tiến độ nộp bài, tính cẩn thận...).
                3. Điểm yếu & Cần cải thiện (weaknesses): Chỉ ra các lỗ hổng kiến thức từ các bài đã làm hoặc lưu ý về quản lý thời gian nếu có bài quá hạn.
                4. Đánh giá chung & Phương pháp cải thiện (generalAssessment):
                   - BẮT BUỘC mở đầu bằng câu tóm tắt: "Trong khoảng thời gian từ {{start_date}} đến {{end_date}}, học sinh đã hoàn thành {{completed_assignments}}/{{total_assignments}} bài tập được giao."
                   - Kèm theo nhận xét tổng quan về tiến độ (nêu rõ số bài còn hạn nộp hoặc đã quá hạn nếu có), lời khuyên và phương pháp luyện tập cụ thể giúp học sinh tiến bộ.

                YÊU CẦU ĐỊNH DẠNG:
                Trả về DUY NHẤT một khối JSON hợp lệ theo cấu trúc sau (không kèm lời giải thích bên ngoài):
                {
                  "strengths": "Điểm mạnh và ưu điểm của học sinh...",
                  "weaknesses": "Điểm yếu và các nội dung cần cải thiện...",
                  "generalAssessment": "Trong khoảng thời gian từ {{start_date}} đến {{end_date}}, học sinh đã hoàn thành {{completed_assignments}}/{{total_assignments}} bài tập được giao. ..."
                }
                """;
    }

    private AiRemarkJsonResult parseAiOutput(String rawOutput, String startDateStr, String endDateStr, int total, int completed) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return fallbackResult(startDateStr, endDateStr, total, completed, "AI không trả về nội dung đánh giá.");
        }
        try {
            String cleanJson = AiResponseUtils.extractCleanJson(rawOutput);
            return objectMapper.readValue(cleanJson, AiRemarkJsonResult.class);
        } catch (Exception e) {
            log.warn("Không parse được JSON từ AI response, sử dụng raw text làm fallback: {}", e.getMessage());
            String cleanText = AiResponseUtils.stripMarkdownFences(rawOutput);
            return fallbackResult(startDateStr, endDateStr, total, completed, cleanText);
        }
    }

    private AiRemarkJsonResult fallbackResult(String startDateStr, String endDateStr, int total, int completed, String defaultText) {
        AiRemarkJsonResult res = new AiRemarkJsonResult();
        res.setStrengths("Chưa có thông tin điểm mạnh cụ thể.");
        res.setWeaknesses("Chưa có thông tin điểm yếu cụ thể.");
        res.setGeneralAssessment(String.format(
                "Trong khoảng thời gian từ %s đến %s, học sinh đã hoàn thành %d/%d bài tập được giao. %s",
                startDateStr, endDateStr, completed, total, defaultText
        ));
        return res;
    }

    @Data
    @NoArgsConstructor
    public static class AiRemarkJsonResult {
        @JsonProperty("strengths")
        private String strengths;

        @JsonProperty("weaknesses")
        private String weaknesses;

        @JsonProperty("generalAssessment")
        private String generalAssessment;
    }
}
