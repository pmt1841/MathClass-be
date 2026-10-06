package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.common.lock.DistributedLockService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.GradeRequest;
import com.codegym.mathclass.submission.dto.request.SubmissionRequest;
import com.codegym.mathclass.submission.dto.response.SubmissionResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionVersionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.entity.SubmissionVersion;
import com.codegym.mathclass.submission.helper.SubmissionNotificationHelper;
import com.codegym.mathclass.submission.mapper.SubmissionMapper;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.repository.SubmissionVersionRepository;
import com.codegym.mathclass.submission.service.SubmissionService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    private static final int MAX_SUBMISSION_VERSIONS = 3;

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository submissionVersionRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final DistributedLockService distributedLockService;
    private final SubmissionMapper submissionMapper;
    private final SubmissionNotificationHelper submissionNotificationHelper;

    @Override
    @Transactional
    public SubmissionResponse createSubmission(long studentId, SubmissionRequest request) {
        if (request.getAssignmentId() == null) {
            throw new BadRequestException("Thiếu assignmentId");
        }
        String lockKey = String.format("lock:submission:assignment:%d:student:%d", request.getAssignmentId(), studentId);
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doCreateSubmission(studentId, request));
    }

    private SubmissionResponse doCreateSubmission(long studentId, SubmissionRequest request) {
        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));

        Submission submission = submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), studentId)
                .orElseGet(() -> Submission.builder()
                        .assignment(assignment)
                        .student(student)
                        .status(SubmissionStatus.DRAFT)
                        .content("")
                        .build());

        return applySubmissionChanges(submission, assignment, request);
    }

    @Override
    @Transactional
    public SubmissionResponse updateSubmission(long submissionId, long studentId, SubmissionRequest request) {
        String lockKey = "lock:submission:" + submissionId;
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doUpdateSubmission(submissionId, studentId, request));
    }

    private SubmissionResponse doUpdateSubmission(long submissionId, long studentId, SubmissionRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài nộp này");
        }

        if (submission.getStatus() == SubmissionStatus.SUBMITTED && request.getStatus() == SubmissionStatus.DRAFT) {
            request.setStatus(SubmissionStatus.SUBMITTED);
        }

        return applySubmissionChanges(submission, submission.getAssignment(), request);
    }

    private SubmissionResponse applySubmissionChanges(Submission submission, Assignment assignment, SubmissionRequest request) {
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        if (submission.getId() > 0 && submission.getScore() != null) {
            throw new BadRequestException("Giáo viên đã chấm điểm, không thể sửa bài");
        }

        boolean isNewlySubmitted = (submission.getStatus() != SubmissionStatus.SUBMITTED
                && request.getStatus() == SubmissionStatus.SUBMITTED);

        String content = Objects.requireNonNullElse(request.getContent(), "");
        validateLatexContent(content, "Nội dung bài làm");

        if (request.getStatus() == SubmissionStatus.SUBMITTED) {
            if (content.trim().isEmpty()) {
                throw new BadRequestException("Nội dung bài làm không được để trống khi nộp bài");
            }
            if (submission.getSubmittedAt() == null) {
                submission.setSubmittedAt(LocalDateTime.now());
            }
        }

        submission.setContent(content);
        submission.setStatus(request.getStatus());

        Submission savedSubmission = submissionRepository.save(submission);

        if (isNewlySubmitted) {
            ensureInitialVersionCreated(savedSubmission);
            submissionNotificationHelper.sendSubmissionNotification(savedSubmission, assignment, 1);
        }

        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional
    public SubmissionResponse unsubmitSubmission(long submissionId, long studentId) {
        String lockKey = "lock:submission:" + submissionId;
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doUnsubmitSubmission(submissionId, studentId));
    }

    private SubmissionResponse doUnsubmitSubmission(long submissionId, long studentId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền hủy bài nộp này");
        }

        Assignment assignment = submission.getAssignment();
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập, không thể hủy nộp");
        }

        if (submission.getScore() != null) {
            throw new BadRequestException("Giáo viên đã chấm điểm, không thể hủy nộp");
        }

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Bài làm chưa được nộp");
        }

        submission.setStatus(SubmissionStatus.DRAFT);
        // Có thể reset submittedAt nếu muốn, nhưng giữ lại cũng không sao để biết lần
        // nộp gần nhất

        Submission savedSubmission = submissionRepository.save(submission);
        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional
    public SubmissionResponse gradeSubmission(long submissionId, long teacherId, GradeRequest request) {
        String lockKey = "lock:submission:" + submissionId;
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doGradeSubmission(submissionId, teacherId, request));
    }

    private SubmissionResponse doGradeSubmission(long submissionId, long teacherId, GradeRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền chấm bài nộp này");
        }

        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new BadRequestException("Học sinh chưa nộp bài");
        }

        Double maxScore = assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0;
        if (request.getScore() != null && request.getScore() > maxScore) {
            throw new BadRequestException("Điểm số không được vượt quá điểm tối đa (" + maxScore + ")");
        }

        validateLatexContent(request.getTeacherFeedback(), "Nội dung phản hồi");

        submission.setScore(request.getScore());
        submission.setTeacherFeedback(request.getTeacherFeedback());
        submission.setStatus(SubmissionStatus.GRADED);

        Submission savedSubmission = submissionRepository.save(submission);

        submissionVersionRepository.findFirstBySubmissionIdOrderByVersionNumberDesc(savedSubmission.getId())
                .ifPresent(v -> {
                    v.setScore(savedSubmission.getScore());
                    v.setTeacherFeedback(savedSubmission.getTeacherFeedback());
                    submissionVersionRepository.save(v);
                });

        submissionNotificationHelper.sendGradingNotification(savedSubmission, assignment);

        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional
    public SubmissionResponse resubmitSubmission(long submissionId, long studentId, SubmissionRequest request) {
        String lockKey = "lock:submission:" + submissionId;
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doResubmitSubmission(submissionId, studentId, request));
    }

    private SubmissionResponse doResubmitSubmission(long submissionId, long studentId, SubmissionRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền nộp lại bài này");
        }

        Assignment assignment = submission.getAssignment();
        if (!assignment.isAllowResubmit()) {
            throw new BadRequestException("Bài tập này không cho phép nộp lại");
        }

        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập, không thể nộp lại");
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("Nội dung bài làm không được để trống khi nộp bài");
        }

        validateLatexContent(content, "Nội dung bài làm");

        int maxVer = submissionVersionRepository.findMaxVersionNumberBySubmissionId(submission.getId());
        if (maxVer >= MAX_SUBMISSION_VERSIONS) {
            throw new BadRequestException("Bạn đã sử dụng hết " + MAX_SUBMISSION_VERSIONS + " lần nộp bài cho bài tập này (tối đa " + MAX_SUBMISSION_VERSIONS + " lần nộp)");
        }

        if (maxVer == 0) {
            SubmissionVersion v1 = SubmissionVersion.builder()
                    .submission(submission)
                    .versionNumber(1)
                    .content(submission.getContent())
                    .score(submission.getScore())
                    .teacherFeedback(submission.getTeacherFeedback())
                    .submittedAt(submission.getSubmittedAt() != null ? submission.getSubmittedAt() : LocalDateTime.now())
                    .build();
            submissionVersionRepository.save(v1);
            maxVer = 1;
        } else {
            submissionVersionRepository.findFirstBySubmissionIdOrderByVersionNumberDesc(submission.getId())
                    .ifPresent(v -> {
                        if (submission.getScore() != null) v.setScore(submission.getScore());
                        if (submission.getTeacherFeedback() != null) v.setTeacherFeedback(submission.getTeacherFeedback());
                        submissionVersionRepository.save(v);
                    });
        }

        int nextVer = maxVer + 1;
        LocalDateTime now = LocalDateTime.now();

        SubmissionVersion newVersion = SubmissionVersion.builder()
                .submission(submission)
                .versionNumber(nextVer)
                .content(content)
                .score(null)
                .teacherFeedback(null)
                .submittedAt(now)
                .build();
        submissionVersionRepository.save(newVersion);

        submission.setContent(content);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setScore(null);
        submission.setTeacherFeedback(null);
        submission.setSubmittedAt(now);

        Submission savedSubmission = submissionRepository.save(submission);

        submissionNotificationHelper.sendSubmissionNotification(savedSubmission, assignment, nextVer);

        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionVersionResponse> getSubmissionVersions(long submissionId, long userId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        boolean isStudent = submission.getStudent().getId() == userId;
        boolean isTeacher = submission.getAssignment().getTeacher().getId() == userId;

        if (!isStudent && !isTeacher) {
            throw new AccessDeniedException("Bạn không có quyền xem lịch sử bài nộp này");
        }

        List<SubmissionVersion> versions = submissionVersionRepository.findBySubmissionIdOrderByVersionNumberAsc(submissionId);
        
        if (versions.isEmpty() && submission.getStatus() != SubmissionStatus.DRAFT) {
            SubmissionVersionResponse v1 = SubmissionVersionResponse.builder()
                    .id(0)
                    .submissionId(submission.getId())
                    .versionNumber(1)
                    .content(submission.getContent())
                    .score(submission.getScore())
                    .teacherFeedback(submission.getTeacherFeedback())
                    .submittedAt(submission.getSubmittedAt())
                    .createdAt(submission.getCreatedAt())
                    .build();
            return List.of(v1);
        }

        return versions.stream()
                .map(submissionMapper::toSubmissionVersionResponse)
                .toList();
    }

    @Override
    public SubmissionResponse getMySubmission(long assignmentId, long studentId) {
        return submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubmissionResponse> getSubmissionsByAssignment(
            long assignmentId,
            long teacherId,
            SubmissionStatus status,
            String keyword,
            Pageable pageable) {

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách bài nộp này");
        }

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        Page<Submission> submissionPage = submissionRepository
                .findSubmissionsByAssignment(
                        assignmentId, status, searchKeyword, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream().map(Submission::getId).toList();
        Map<Long, Integer> maxVersionMap = submissionIds.isEmpty() ? Collections.emptyMap() :
                submissionVersionRepository.findMaxVersionNumbersBySubmissionIds(submissionIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()
                        ));

        return submissionMapper.toSubmissionResponsePage(submissionPage, maxVersionMap);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResponse getSubmissionDetail(long submissionId, long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem bài nộp này");
        }

        return mapToDto(submission);
    }

    private SubmissionResponse mapToDto(Submission submission) {
        int totalVersions = submissionVersionRepository.findMaxVersionNumberBySubmissionId(submission.getId());
        return submissionMapper.toSubmissionResponse(submission, totalVersions);
    }

    private void validateLatexContent(String content, String fieldDesc) {
        if (content != null && !LaTeXSanitizer.isSafe(content)) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(content);
            throw new BadRequestException(fieldDesc + " chứa lệnh LaTeX không hợp lệ: " + dangerous);
        }
    }

    private void ensureInitialVersionCreated(Submission submission) {
        if (submissionVersionRepository.findMaxVersionNumberBySubmissionId(submission.getId()) == 0) {
            SubmissionVersion v1 = SubmissionVersion.builder()
                    .submission(submission)
                    .versionNumber(1)
                    .content(submission.getContent())
                    .submittedAt(submission.getSubmittedAt())
                    .build();
            submissionVersionRepository.save(v1);
        }
    }
}
