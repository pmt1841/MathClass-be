package com.codegym.mathclass.submission.mapper;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.submission.dto.response.*;
import com.codegym.mathclass.submission.entity.*;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionMapperTest {

    private SubmissionMapper mapper;
    private User student;
    private User teacher;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        mapper = new SubmissionMapper();

        student = new User();
        student.setId(10L);
        student.setFullName("Nguyen Van Hoc Sinh");

        teacher = new User();
        teacher.setId(20L);
        teacher.setFullName("Tran Thi Giao Vien");

        assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTitle("Bài tập Đại số");
        assignment.setAllowResubmit(true);
        assignment.setTeacher(teacher);
    }

    @Test
    @DisplayName("toSubmissionResponse: Should return null when input is null")
    void toSubmissionResponse_Null_ReturnsNull() {
        assertThat(mapper.toSubmissionResponse(null)).isNull();
        assertThat(mapper.toSubmissionResponse(null, 2)).isNull();
    }

    @Test
    @DisplayName("toSubmissionResponse: Should map fields correctly with default version logic")
    void toSubmissionResponse_Valid_MapsCorrectly() {
        Submission submission = Submission.builder()
                .assignment(assignment)
                .student(student)
                .content("Answer content")
                .teacherFeedback("Good job")
                .status(SubmissionStatus.SUBMITTED)
                .score(9.0)
                .submittedAt(LocalDateTime.now())
                .build();
        submission.setId(500L);

        SubmissionResponse response = mapper.toSubmissionResponse(submission);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getAssignmentId()).isEqualTo(100L);
        assertThat(response.getStudentId()).isEqualTo(10L);
        assertThat(response.getStudentName()).isEqualTo("Nguyen Van Hoc Sinh");
        assertThat(response.getContent()).isEqualTo("Answer content");
        assertThat(response.getTeacherFeedback()).isEqualTo("Good job");
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getScore()).isEqualTo(9.0);
        assertThat(response.getAllowResubmit()).isTrue();
        assertThat(response.getVersionNumber()).isEqualTo(1);
        assertThat(response.getTotalVersions()).isEqualTo(1);
    }

    @Test
    @DisplayName("toSubmissionResponse: Should handle explicit maxVer")
    void toSubmissionResponse_ExplicitMaxVer() {
        Submission submission = Submission.builder()
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .build();
        submission.setId(501L);

        SubmissionResponse response = mapper.toSubmissionResponse(submission, 3);

        assertThat(response).isNotNull();
        assertThat(response.getVersionNumber()).isEqualTo(3);
        assertThat(response.getTotalVersions()).isEqualTo(3);
    }

    @Test
    @DisplayName("toSubmissionResponsePage: Should map Page of submissions with version map")
    void toSubmissionResponsePage_MapsPageCorrectly() {
        Submission s1 = Submission.builder().assignment(assignment).student(student).status(SubmissionStatus.SUBMITTED).build();
        s1.setId(1L);
        Submission s2 = Submission.builder().assignment(assignment).student(student).status(SubmissionStatus.DRAFT).build();
        s2.setId(2L);

        Page<Submission> page = new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 10), 2);
        Map<Long, Integer> versionMap = Map.of(1L, 2);

        Page<SubmissionResponse> result = mapper.toSubmissionResponsePage(page, versionMap);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTotalVersions()).isEqualTo(2);
        assertThat(result.getContent().get(1).getTotalVersions()).isEqualTo(0);
    }

    @Test
    @DisplayName("toSubmissionResponseList: Should handle null and empty lists")
    void toSubmissionResponseList_NullOrEmpty() {
        assertThat(mapper.toSubmissionResponseList(null, null)).isEmpty();
        assertThat(mapper.toSubmissionResponseList(Collections.emptyList(), null)).isEmpty();
    }

    @Test
    @DisplayName("toSubmissionVersionResponse: Should map SubmissionVersion correctly")
    void toSubmissionVersionResponse_MapsCorrectly() {
        assertThat(mapper.toSubmissionVersionResponse(null)).isNull();

        Submission submission = new Submission();
        submission.setId(50L);

        SubmissionVersion version = SubmissionVersion.builder()
                .submission(submission)
                .versionNumber(2)
                .content("Version 2 answer")
                .score(8.5)
                .teacherFeedback("Improved")
                .submittedAt(LocalDateTime.now())
                .build();
        version.setId(200L);

        SubmissionVersionResponse response = mapper.toSubmissionVersionResponse(version);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getSubmissionId()).isEqualTo(50L);
        assertThat(response.getVersionNumber()).isEqualTo(2);
        assertThat(response.getContent()).isEqualTo("Version 2 answer");
        assertThat(response.getScore()).isEqualTo(8.5);
        assertThat(response.getTeacherFeedback()).isEqualTo("Improved");
    }

    @Test
    @DisplayName("toSubmissionCommentResponse: Should map SubmissionComment correctly")
    void toSubmissionCommentResponse_MapsCorrectly() {
        assertThat(mapper.toSubmissionCommentResponse(null)).isNull();

        Submission submission = new Submission();
        submission.setId(70L);

        SubmissionComment comment = SubmissionComment.builder()
                .submission(submission)
                .teacher(teacher)
                .versionNumber(1)
                .quoteText("x + 1")
                .occurrenceIndex(0)
                .imageCode("IMG_01")
                .content("Xem lại dấu")
                .build();
        comment.setId(300L);

        SubmissionCommentResponse response = mapper.toSubmissionCommentResponse(comment);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(300L);
        assertThat(response.getSubmissionId()).isEqualTo(70L);
        assertThat(response.getTeacherId()).isEqualTo(20L);
        assertThat(response.getTeacherName()).isEqualTo("Tran Thi Giao Vien");
        assertThat(response.getQuoteText()).isEqualTo("x + 1");
        assertThat(response.getContent()).isEqualTo("Xem lại dấu");
    }

    @Test
    @DisplayName("toSubmissionDrawingResponse: Should map SubmissionDrawing correctly")
    void toSubmissionDrawingResponse_MapsCorrectly() {
        assertThat(mapper.toSubmissionDrawingResponse(null)).isNull();

        Submission submission = new Submission();
        submission.setId(80L);

        SubmissionDrawing drawing = SubmissionDrawing.builder()
                .submission(submission)
                .shapeCode("TRIANGLE_ABC")
                .jsxGraphData(Map.of("points", List.of("A", "B", "C")))
                .metadata(Map.of("scale", 1))
                .build();
        drawing.setId(400L);

        SubmissionDrawingResponse response = mapper.toSubmissionDrawingResponse(drawing);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(400L);
        assertThat(response.getSubmissionId()).isEqualTo(80L);
        assertThat(response.getShapeCode()).isEqualTo("TRIANGLE_ABC");
        assertThat(response.getJsxGraphData()).containsEntry("points", List.of("A", "B", "C"));
        assertThat(response.getMetadata()).containsEntry("scale", 1);
    }

    @Test
    @DisplayName("toSubmissionHintItemResponse and toHintHistoryResponse: Should map hints correctly")
    void toHintHistoryResponse_MapsCorrectly() {
        assertThat(mapper.toSubmissionHintItemResponse(null)).isNull();

        SubmissionHint hint1 = SubmissionHint.builder()
                .hintNumber(1)
                .studentSnapshotContent("x = ?")
                .aiHintContent("Hãy chuyển vế")
                .build();
        hint1.setId(101L);

        SubmissionHint hint2 = SubmissionHint.builder()
                .hintNumber(2)
                .studentSnapshotContent("x + 2 = 5")
                .aiHintContent("Trừ 2 ở hai vế")
                .build();
        hint2.setId(102L);

        HintHistoryResponse history = mapper.toHintHistoryResponse(999L, List.of(hint1, hint2), 3);

        assertThat(history).isNotNull();
        assertThat(history.getSubmissionId()).isEqualTo(999L);
        assertThat(history.getTotalUsed()).isEqualTo(2);
        assertThat(history.getMaxHints()).isEqualTo(3);
        assertThat(history.getRemainingHints()).isEqualTo(1);
        assertThat(history.getHints()).hasSize(2);
        assertThat(history.getHints().get(0).getHintNumber()).isEqualTo(1);
        assertThat(history.getHints().get(1).getHintNumber()).isEqualTo(2);
    }
}
