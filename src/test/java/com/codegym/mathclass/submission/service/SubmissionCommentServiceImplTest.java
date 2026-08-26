package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.SubmissionCommentResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionComment;
import com.codegym.mathclass.submission.repository.SubmissionCommentRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.repository.SubmissionVersionRepository;
import com.codegym.mathclass.submission.service.impl.SubmissionCommentServiceImpl;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionCommentServiceImplTest {

    @Mock
    private SubmissionCommentRepository submissionCommentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionVersionRepository submissionVersionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubmissionCommentServiceImpl commentService;

    private User teacher;
    private User student;
    private Assignment assignment;
    private Submission submission;
    private SubmissionComment comment;

    private final long teacherId = 1L;
    private final long studentId = 2L;
    private final long submissionId = 100L;
    private final long commentId = 500L;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(teacherId);
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setEmail("teacher@codegym.com");

        student = new User();
        student.setId(studentId);
        student.setFullName("Tran Thi Student");
        student.setEmail("student@codegym.com");

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTeacher(teacher);

        submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignment(assignment);
        submission.setStudent(student);

        comment = SubmissionComment.builder()
                .submission(submission)
                .teacher(teacher)
                .versionNumber(1)
                .content("Good work")
                .quoteText("Quote text")
                .build();
        comment.setId(commentId);
    }

    @Nested
    @DisplayName("getCommentsBySubmissionId Tests")
    class GetCommentsBySubmissionIdTests {

        @Test
        @DisplayName("Should return comments list for student owner when versionNumber is null")
        void getCommentsBySubmissionId_StudentOwner_ReturnsList() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionCommentRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                    .thenReturn(List.of(comment));

            List<SubmissionCommentResponse> responses = commentService.getCommentsBySubmissionId(submissionId, null, "student@codegym.com");

            assertThat(responses).isNotNull().hasSize(1);
            assertThat(responses.get(0).getContent()).isEqualTo("Good work");
            assertThat(responses.get(0).getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return comments list filtered by versionNumber")
        void getCommentsBySubmissionId_FilteredByVersion_ReturnsList() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionCommentRepository.findBySubmissionIdAndVersionNumberOrderByCreatedAtAsc(submissionId, 2))
                    .thenReturn(List.of(comment));

            List<SubmissionCommentResponse> responses = commentService.getCommentsBySubmissionId(submissionId, 2, "teacher@codegym.com");

            assertThat(responses).isNotNull().hasSize(1);
            verify(submissionCommentRepository, times(1)).findBySubmissionIdAndVersionNumberOrderByCreatedAtAsc(submissionId, 2);
        }

        @Test
        @DisplayName("Should return comments list for teacher owner")
        void getCommentsBySubmissionId_TeacherOwner_ReturnsList() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionCommentRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                    .thenReturn(List.of(comment));

            List<SubmissionCommentResponse> responses = commentService.getCommentsBySubmissionId(submissionId, null, "teacher@codegym.com");

            assertThat(responses).isNotNull().hasSize(1);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when submission not found")
        void getCommentsBySubmissionId_SubmissionNotFound_ThrowsException() {
            when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.getCommentsBySubmissionId(999L, null, "student@codegym.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy bài nộp với ID");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-authorized user requests comments")
        void getCommentsBySubmissionId_Unauthorized_ThrowsException() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> commentService.getCommentsBySubmissionId(submissionId, null, "other@codegym.com"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền truy cập nhận xét của bài nộp này");
        }
    }

    @Nested
    @DisplayName("addComment Tests")
    class AddCommentTests {

        @Test
        @DisplayName("Should add comment successfully when requested by teacher with explicit versionNumber")
        void addComment_ValidRequestWithVersion_Success() {
            SubmissionCommentRequest request = new SubmissionCommentRequest();
            request.setContent("Great solution!");
            request.setVersionNumber(2);

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
            when(submissionCommentRepository.save(any(SubmissionComment.class))).thenAnswer(i -> {
                SubmissionComment c = i.getArgument(0);
                c.setId(commentId);
                return c;
            });

            SubmissionCommentResponse response = commentService.addComment(submissionId, teacherId, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(commentId);
            assertThat(response.getContent()).isEqualTo("Great solution!");
            assertThat(response.getVersionNumber()).isEqualTo(2);
            verify(submissionCommentRepository, times(1)).save(any(SubmissionComment.class));
        }

        @Test
        @DisplayName("Should add comment with fallback max version when versionNumber is omitted")
        void addComment_ValidRequestWithoutVersion_UsesMaxVersion() {
            SubmissionCommentRequest request = new SubmissionCommentRequest();
            request.setContent("Great solution!");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
            when(submissionVersionRepository.findMaxVersionNumberBySubmissionId(submissionId)).thenReturn(3);
            when(submissionCommentRepository.save(any(SubmissionComment.class))).thenAnswer(i -> {
                SubmissionComment c = i.getArgument(0);
                c.setId(commentId);
                return c;
            });

            SubmissionCommentResponse response = commentService.addComment(submissionId, teacherId, request);

            assertThat(response).isNotNull();
            assertThat(response.getVersionNumber()).isEqualTo(3);
            verify(submissionCommentRepository, times(1)).save(any(SubmissionComment.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-teacher tries to comment")
        void addComment_NotTeacher_ThrowsException() {
            SubmissionCommentRequest request = new SubmissionCommentRequest();
            request.setContent("Great solution!");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> commentService.addComment(submissionId, 999L, request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không phải giáo viên phụ trách bài tập của bài nộp này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when content contains dangerous LaTeX")
        void addComment_DangerousLaTeX_ThrowsException() {
            SubmissionCommentRequest request = new SubmissionCommentRequest();
            request.setContent("Check this: \\write18{rm -rf /}");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> commentService.addComment(submissionId, teacherId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nội dung nhận xét chứa lệnh LaTeX không hợp lệ");
        }
    }

    @Nested
    @DisplayName("deleteComment Tests")
    class DeleteCommentTests {

        @Test
        @DisplayName("Should delete comment successfully when requested by author teacher")
        void deleteComment_AuthorTeacher_Success() {
            when(submissionCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            commentService.deleteComment(submissionId, commentId, teacherId);

            verify(submissionCommentRepository, times(1)).delete(comment);
        }

        @Test
        @DisplayName("Should throw BadRequestException when comment does not belong to submissionId")
        void deleteComment_MismatchSubmissionId_ThrowsException() {
            when(submissionCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(999L, commentId, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nhận xét không thuộc về bài nộp này");

            verify(submissionCommentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-author teacher tries to delete")
        void deleteComment_NotAuthor_ThrowsException() {
            when(submissionCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(submissionId, commentId, 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xóa nhận xét này");

            verify(submissionCommentRepository, never()).delete(any());
        }
    }
}
