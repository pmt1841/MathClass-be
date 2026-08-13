package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.TagResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentTag;
import com.codegym.mathclass.assignment.entity.Tag;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.repository.TagRepository;
import com.codegym.mathclass.assignment.service.impl.TagServiceImpl;
import com.codegym.mathclass.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TagServiceImpl tagService;

    private Tag gradeTag;
    private Tag subjectTag;
    private Tag difficultyTag;

    @BeforeEach
    void setUp() {
        gradeTag = Tag.builder().name("10").type(TagType.GRADE).active(true).build();
        gradeTag.setId(1L);

        subjectTag = Tag.builder().name("Đại số").type(TagType.SUBJECT).active(true).build();
        subjectTag.setId(2L);

        difficultyTag = Tag.builder().name("Dễ").type(TagType.DIFFICULTY).active(true).build();
        difficultyTag.setId(3L);
    }

    @Test
    @DisplayName("getActiveTags should return mapped list of active tags")
    void getActiveTags_ReturnsList() {
        when(tagRepository.findByActiveTrueOrderByTypeAscNameAsc())
                .thenReturn(List.of(gradeTag, subjectTag, difficultyTag));

        List<TagResponse> result = tagService.getActiveTags(null);

        assertEquals(3, result.size());
        assertEquals("10", result.get(0).getName());
    }

    @Test
    @DisplayName("replaceTags should update tags when valid")
    void replaceTags_ValidTags_Success() {
        Assignment assignment = Assignment.builder().assignmentTags(new ArrayList<>()).build();
        when(tagRepository.findByIdInAndActiveTrue(List.of(1L, 2L)))
                .thenReturn(List.of(gradeTag, subjectTag));

        tagService.replaceTags(assignment, List.of(1L, 2L));

        assertEquals(2, assignment.getAssignmentTags().size());
        verify(entityManager, times(1)).flush();
    }

    @Test
    @DisplayName("replaceTags should throw BadRequestException when tag count exceeds max types")
    void replaceTags_TooManyTags_ThrowsException() {
        Assignment assignment = Assignment.builder().assignmentTags(new ArrayList<>()).build();
        List<Long> invalidIds = List.of(1L, 2L, 3L, 4L);

        assertThrows(BadRequestException.class, () -> tagService.replaceTags(assignment, invalidIds));
    }

    @Test
    @DisplayName("requireCompletePublicTags should throw exception if assignment lacks 3 tag types")
    void requireCompletePublicTags_Incomplete_ThrowsException() {
        Assignment assignment = Assignment.builder().assignmentTags(new ArrayList<>()).build();
        assignment.getAssignmentTags().add(AssignmentTag.builder().tag(gradeTag).build());

        assertThrows(BadRequestException.class, () -> tagService.requireCompletePublicTags(assignment));
    }

    @Test
    @DisplayName("requireCompletePublicTags should pass if assignment has all 3 tag types")
    void requireCompletePublicTags_Complete_Success() {
        Assignment assignment = Assignment.builder().assignmentTags(new ArrayList<>()).build();
        assignment.getAssignmentTags().add(AssignmentTag.builder().tag(gradeTag).build());
        assignment.getAssignmentTags().add(AssignmentTag.builder().tag(subjectTag).build());
        assignment.getAssignmentTags().add(AssignmentTag.builder().tag(difficultyTag).build());

        assertDoesNotThrow(() -> tagService.requireCompletePublicTags(assignment));
    }

    @Test
    @DisplayName("copyTags should copy tags from source to target assignment")
    void copyTags_Success() {
        Assignment source = Assignment.builder().assignmentTags(new ArrayList<>()).build();
        source.getAssignmentTags().add(AssignmentTag.builder().tag(gradeTag).build());

        Assignment target = Assignment.builder().assignmentTags(new ArrayList<>()).build();

        tagService.copyTags(source, target);

        assertEquals(1, target.getAssignmentTags().size());
        assertEquals(gradeTag, target.getAssignmentTags().get(0).getTag());
    }

    @Test
    @DisplayName("validateTagFilters should throw exception for invalid tag type")
    void validateTagFilters_WrongType_ThrowsException() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(gradeTag));

        // Subject filter ID passed gradeTag ID
        assertThrows(BadRequestException.class, () -> tagService.validateTagFilters(null, 1L, null));
    }

    @Test
    @DisplayName("validateTagFilters should pass for valid tag filters")
    void validateTagFilters_ValidFilters_Success() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(gradeTag));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(subjectTag));
        when(tagRepository.findById(3L)).thenReturn(Optional.of(difficultyTag));

        assertDoesNotThrow(() -> tagService.validateTagFilters(1L, 2L, 3L));
    }
}
