package com.codegym.mathclass.assignment.service.helper;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.service.TagService;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Helper chuyên trách xử lý nghiệp vụ sao chép (clone) đối tượng {@link Assignment}.
 *
 * <p>Phân tách rõ ràng giữa 4 trường hợp nghiệp vụ độc lập:
 * <ul>
 *     <li>Clone giao bài tập đơn lẻ tới lớp học (kế thừa allowResubmit, maxScore, gắn classroom, parentId).</li>
 *     <li>Clone bài tập từ Thư viện cộng đồng về kho cá nhân (DRAFT, PRIVATE, ghi nhận originalAuthor, parentId null).</li>
 *     <li>Clone câu hỏi vào Phiếu bài tập (allowResubmit = false, maxScore theo cấu hình sheet item).</li>
 *     <li>Clone câu hỏi khi sao chép toàn bộ Phiếu bài tập từ Thư viện về kho cá nhân.</li>
 * </ul>
 * Đồng thời chuẩn hóa và tái sử dụng logic deep copy (drawings, images, tags).
 */
@Component
@RequiredArgsConstructor
public class AssignmentCloneHelper {

    private final TagService tagService;

    /**
     * Kịch bản 1: Clone bài tập đơn lẻ để giao cho một lớp học cụ thể.
     *
     * <p>Đặc điểm nghiệp vụ:
     * <ul>
     *     <li>Trạng thái: {@link AssignmentStatus#PUBLISHED}.</li>
     *     <li>Gắn {@code classroom} và {@code deadline}.</li>
     *     <li>Giữ nguyên {@code teacher} từ bài gốc (giáo viên giao bài).</li>
     *     <li>Gắn {@code parentId} trỏ về bài gốc để liên kết truy vết.</li>
     *     <li>Bảo toàn {@code allowResubmit} và {@code maxScore} từ bài gốc.</li>
     *     <li>{@code originalAuthor} để {@code null} vì học sinh chỉ tương tác với giáo viên đứng lớp.</li>
     * </ul>
     *
     * @param original  Bài tập gốc
     * @param classroom Lớp học được giao bài
     * @param deadline  Thời hạn nộp bài
     * @return Bản clone chưa persist
     */
    public Assignment cloneForClassroom(Assignment original, Classroom classroom, LocalDateTime deadline) {
        Assignment clone = Assignment.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .content(original.getContent())
                .teacher(original.getTeacher())
                .classroom(classroom)
                .parentId(original.getId())
                .deadline(deadline)
                .status(AssignmentStatus.PUBLISHED)
                .allowResubmit(original.isAllowResubmit())
                .maxScore(original.getMaxScore())
                .originalAuthor(null)
                .build();

        copyDrawings(original, clone);
        copyImages(original, clone);
        copyTags(original, clone);
        return clone;
    }

    /**
     * Kịch bản 2: Clone bài tập từ Thư viện cộng đồng về kho bài tập cá nhân của giáo viên.
     *
     * <p>Đặc điểm nghiệp vụ:
     * <ul>
     *     <li>Trạng thái ban đầu: {@link AssignmentStatus#DRAFT}.</li>
     *     <li>Tầm nhìn: {@link AssignmentVisibility#PRIVATE}.</li>
     *     <li>Giáo viên sở hữu mới: {@code teacher}.</li>
     *     <li>Ghi nhận {@code originalAuthor} (ưu tiên author gốc nếu bài này trước đó cũng là clone).</li>
     *     <li>Không gắn lớp học ({@code classroom = null}) và {@code parentId = null} (tồn tại độc lập như bài mới).</li>
     *     <li>{@code deadline = null}, {@code allowResubmit = false}.</li>
     *     <li>Kế thừa {@code maxScore} từ bài gốc.</li>
     * </ul>
     *
     * @param original Bài tập gốc trong Thư viện
     * @param teacher  Giáo viên sao chép bài về kho cá nhân
     * @return Bản clone chưa persist
     */
    public Assignment cloneFromLibrary(Assignment original, User teacher) {
        User originalAuthor = original.getOriginalAuthor() != null
                ? original.getOriginalAuthor()
                : original.getTeacher();

        Assignment clone = Assignment.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .content(original.getContent())
                .teacher(teacher)
                .originalAuthor(originalAuthor)
                .classroom(null)
                .parentId(null)
                .deadline(null)
                .status(AssignmentStatus.DRAFT)
                .visibility(AssignmentVisibility.PRIVATE)
                .allowResubmit(false)
                .maxScore(original.getMaxScore())
                .build();

        copyDrawings(original, clone);
        copyImages(original, clone);
        copyTags(original, clone);
        return clone;
    }

    /**
     * Kịch bản 3: Clone câu hỏi để đưa vào Phiếu bài tập (khi tạo master sheet hoặc publish tới lớp).
     *
     * <p>Đặc điểm nghiệp vụ:
     * <ul>
     *     <li>Trạng thái: {@link AssignmentStatus#PUBLISHED}.</li>
     *     <li>Gắn {@code classroom} và {@code deadline} (nếu giao cho lớp), hoặc {@code null} (nếu là master clone).</li>
     *     <li>{@code parentId} trỏ về bài gốc.</li>
     *     <li>{@code allowResubmit = false} vì cơ chế nộp/làm lại được quản lý tập trung ở cấp độ Phiếu bài tập.</li>
     *     <li>{@code maxScore} ưu tiên theo cấu hình của sheet item, nếu {@code null} fallback về bài gốc.</li>
     *     <li>{@code originalAuthor} để {@code null}.</li>
     * </ul>
     *
     * @param original  Bài tập gốc
     * @param teacher   Giáo viên sở hữu
     * @param classroom Lớp học gắn với phiếu (hoặc null nếu là master clone)
     * @param deadline  Thời hạn nộp (hoặc null nếu là master clone)
     * @param maxScore  Điểm tối đa của câu hỏi trong phiếu
     * @return Bản clone chưa persist
     */
    public Assignment cloneForSheet(Assignment original, User teacher, Classroom classroom,
                                    LocalDateTime deadline, Double maxScore) {
        Double resolvedMaxScore = maxScore != null ? maxScore : original.getMaxScore();

        Assignment clone = Assignment.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .content(original.getContent())
                .teacher(teacher)
                .classroom(classroom)
                .parentId(original.getId())
                .deadline(deadline)
                .status(AssignmentStatus.PUBLISHED)
                .allowResubmit(false)
                .maxScore(resolvedMaxScore)
                .originalAuthor(null)
                .build();

        copyDrawings(original, clone);
        copyImages(original, clone);
        copyTags(original, clone);
        return clone;
    }

    /**
     * Kịch bản 4: Clone câu hỏi khi sao chép toàn bộ Phiếu bài tập từ Thư viện về kho cá nhân.
     *
     * <p>Đặc điểm nghiệp vụ:
     * <ul>
     *     <li>Trạng thái: {@link AssignmentStatus#DRAFT}.</li>
     *     <li>Tầm nhìn: {@link AssignmentVisibility#PRIVATE}.</li>
     *     <li>Giáo viên sở hữu mới: {@code teacher}.</li>
     *     <li>Ghi nhận {@code originalAuthor}.</li>
     *     <li>Không gắn lớp ({@code classroom = null}) và {@code parentId = null}.</li>
     *     <li>{@code deadline = null}, {@code allowResubmit = false}.</li>
     *     <li>Bảo toàn {@code maxScore} từ bài gốc.</li>
     * </ul>
     *
     * @param original Bài tập gốc trong Thư viện
     * @param teacher  Giáo viên sao chép phiếu về kho cá nhân
     * @return Bản clone chưa persist
     */
    public Assignment cloneSheetItemFromLibrary(Assignment original, User teacher) {
        return cloneFromLibrary(original, teacher);
    }

    /**
     * Kịch bản 4 (mở rộng): Clone câu hỏi với tác giả gốc được chỉ định rõ ràng.
     *
     * @param original       Bài tập gốc trong Thư viện
     * @param teacher        Giáo viên sao chép phiếu về kho cá nhân
     * @param originalAuthor Tác giả gốc được chỉ định
     * @return Bản clone chưa persist
     */
    public Assignment cloneSheetItemFromLibrary(Assignment original, User teacher, User originalAuthor) {
        Assignment clone = cloneFromLibrary(original, teacher);
        if (originalAuthor != null) {
            clone.setOriginalAuthor(originalAuthor);
        }
        return clone;
    }

    // ==========================================
    // DRY Helpers: Deep copy drawings, images, tags
    // ==========================================

    private void copyDrawings(Assignment original, Assignment clone) {
        if (original.getDrawings() == null || original.getDrawings().isEmpty()) {
            return;
        }
        for (AssignmentDrawing src : original.getDrawings()) {
            AssignmentDrawing drawing = AssignmentDrawing.builder()
                    .shapeCode(src.getShapeCode())
                    .jsxGraphData(src.getJsxGraphData())
                    .assignment(clone)
                    .build();
            clone.getDrawings().add(drawing);
        }
    }

    private void copyImages(Assignment original, Assignment clone) {
        if (original.getImages() == null || original.getImages().isEmpty()) {
            return;
        }
        for (AssignmentImage src : original.getImages()) {
            AssignmentImage image = new AssignmentImage();
            image.setImageCode(src.getImageCode());
            image.setImageUrl(src.getImageUrl());
            image.setAssignment(clone);
            clone.getImages().add(image);
        }
    }

    private void copyTags(Assignment original, Assignment clone) {
        tagService.copyTags(original, clone);
    }
}
