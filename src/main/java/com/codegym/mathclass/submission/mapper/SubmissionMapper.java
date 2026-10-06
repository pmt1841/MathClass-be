package com.codegym.mathclass.submission.mapper;

import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionCommentResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionHintItemResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionVersionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionComment;
import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import com.codegym.mathclass.submission.entity.SubmissionHint;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.entity.SubmissionVersion;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class SubmissionMapper {

    public SubmissionResponse toSubmissionResponse(Submission submission) {
        return toSubmissionResponse(submission, 0);
    }

    public SubmissionResponse toSubmissionResponse(Submission submission, int maxVer) {
        if (submission == null) {
            return null;
        }

        int totalVersions = maxVer;
        if (totalVersions == 0 && submission.getStatus() != SubmissionStatus.DRAFT) {
            totalVersions = 1;
        }

        return SubmissionResponse.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment() != null ? submission.getAssignment().getId() : 0)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : 0)
                .studentName(submission.getStudent() != null ? submission.getStudent().getFullName() : null)
                .content(submission.getContent())
                .teacherFeedback(submission.getTeacherFeedback())
                .status(submission.getStatus())
                .score(submission.getScore())
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .allowResubmit(submission.getAssignment() != null && submission.getAssignment().isAllowResubmit())
                .versionNumber(totalVersions > 0 ? totalVersions : 1)
                .totalVersions(totalVersions)
                .build();
    }

    public Page<SubmissionResponse> toSubmissionResponsePage(Page<Submission> submissionPage, Map<Long, Integer> maxVersionMap) {
        if (submissionPage == null) {
            return Page.empty();
        }
        Map<Long, Integer> versionMap = maxVersionMap != null ? maxVersionMap : Collections.emptyMap();
        return submissionPage.map(sub -> toSubmissionResponse(sub, versionMap.getOrDefault(sub.getId(), 0)));
    }

    public List<SubmissionResponse> toSubmissionResponseList(List<Submission> submissions, Map<Long, Integer> maxVersionMap) {
        if (submissions == null) {
            return Collections.emptyList();
        }
        Map<Long, Integer> versionMap = maxVersionMap != null ? maxVersionMap : Collections.emptyMap();
        return submissions.stream()
                .map(sub -> toSubmissionResponse(sub, versionMap.getOrDefault(sub.getId(), 0)))
                .toList();
    }

    public SubmissionVersionResponse toSubmissionVersionResponse(SubmissionVersion version) {
        if (version == null) {
            return null;
        }

        return SubmissionVersionResponse.builder()
                .id(version.getId())
                .submissionId(version.getSubmission() != null ? version.getSubmission().getId() : 0)
                .versionNumber(version.getVersionNumber())
                .content(version.getContent())
                .score(version.getScore())
                .teacherFeedback(version.getTeacherFeedback())
                .submittedAt(version.getSubmittedAt())
                .createdAt(version.getCreatedAt())
                .build();
    }

    public SubmissionCommentResponse toSubmissionCommentResponse(SubmissionComment comment) {
        if (comment == null) {
            return null;
        }

        return SubmissionCommentResponse.builder()
                .id(comment.getId())
                .submissionId(comment.getSubmission() != null ? comment.getSubmission().getId() : null)
                .versionNumber(comment.getVersionNumber() != null ? comment.getVersionNumber() : 1)
                .teacherId(comment.getTeacher() != null ? comment.getTeacher().getId() : null)
                .teacherName(comment.getTeacher() != null ? comment.getTeacher().getFullName() : null)
                .quoteText(comment.getQuoteText())
                .occurrenceIndex(comment.getOccurrenceIndex())
                .imageCode(comment.getImageCode())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    public SubmissionDrawingResponse toSubmissionDrawingResponse(SubmissionDrawing drawing) {
        if (drawing == null) {
            return null;
        }

        return SubmissionDrawingResponse.builder()
                .id(drawing.getId())
                .submissionId(drawing.getSubmission() != null ? drawing.getSubmission().getId() : 0)
                .shapeCode(drawing.getShapeCode())
                .jsxGraphData(drawing.getJsxGraphData())
                .metadata(drawing.getMetadata())
                .createdAt(drawing.getCreatedAt())
                .updatedAt(drawing.getUpdatedAt())
                .build();
    }

    public SubmissionHintItemResponse toSubmissionHintItemResponse(SubmissionHint hint) {
        if (hint == null) {
            return null;
        }

        return SubmissionHintItemResponse.builder()
                .id(hint.getId())
                .hintNumber(hint.getHintNumber())
                .studentSnapshotContent(hint.getStudentSnapshotContent())
                .aiHintContent(hint.getAiHintContent())
                .createdAt(hint.getCreatedAt())
                .build();
    }

    public HintHistoryResponse toHintHistoryResponse(Long submissionId, List<SubmissionHint> hints, int maxHints) {
        List<SubmissionHint> hintList = hints != null ? hints : Collections.emptyList();
        int totalUsed = hintList.size();

        List<SubmissionHintItemResponse> items = hintList.stream()
                .map(this::toSubmissionHintItemResponse)
                .toList();

        return HintHistoryResponse.builder()
                .submissionId(submissionId)
                .totalUsed(totalUsed)
                .maxHints(maxHints)
                .remainingHints(Math.max(0, maxHints - totalUsed))
                .hints(items)
                .build();
    }
}
