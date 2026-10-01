package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.mapper.AssignmentSheetMapper;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.service.helper.AssignmentCloneHelper;
import com.codegym.mathclass.assignment.service.impl.AssignmentLibraryServiceImpl;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentLibraryServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSheetRepository assignmentSheetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private AssignmentSheetMapper assignmentSheetMapper;

    @Mock
    private AssignmentCloneHelper assignmentCloneHelper;

    @InjectMocks
    private AssignmentLibraryServiceImpl assignmentLibraryService;

    private User teacher;
    private Assignment publicAssignment;
    private AssignmentSheet publicSheet;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(1L);
        teacher.setFullName("Nguyen Van Teacher");

        publicAssignment = new Assignment();
        publicAssignment.setId(10L);
        publicAssignment.setTitle("Public Assignment");
        publicAssignment.setVisibility(AssignmentVisibility.PUBLIC);
        publicAssignment.setStatus(AssignmentStatus.PUBLISHED);
        publicAssignment.setClassroom(null);

        publicSheet = AssignmentSheet.builder()
                .title("Public Sheet")
                .teacher(teacher)
                .visibility(AssignmentVisibility.PUBLIC)
                .classroom(null)
                .items(new ArrayList<>())
                .build();
        publicSheet.setId(20L);
    }

    @Nested
    @DisplayName("getPublicAssignments Tests")
    class GetPublicAssignmentsTests {

        @Test
        @DisplayName("Should return paginated public assignments")
        void getPublicAssignments_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Assignment> page = new PageImpl<>(List.of(publicAssignment), pageable, 1);
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);

            when(assignmentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(assignmentMapper.toAssignmentResponseWithoutContent(publicAssignment)).thenReturn(response);

            Page<AssignmentResponse> result = assignmentLibraryService.getPublicAssignments("math", pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("getPublicAssignmentDetail Tests")
    class GetPublicAssignmentDetailTests {

        @Test
        @DisplayName("Should return public assignment detail successfully")
        void getPublicAssignmentDetail_Success() {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Public Assignment");

            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));
            when(assignmentMapper.toAssignmentResponse(publicAssignment)).thenReturn(response);

            AssignmentResponse result = assignmentLibraryService.getPublicAssignmentDetail(10L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment not found")
        void getPublicAssignmentDetail_NotFound() {
            when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentLibraryService.getPublicAssignmentDetail(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment belongs to classroom")
        void getPublicAssignmentDetail_BelongsToClassroom_ThrowsException() {
            publicAssignment.setClassroom(new Classroom());
            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));

            assertThatThrownBy(() -> assignmentLibraryService.getPublicAssignmentDetail(10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment is not PUBLIC")
        void getPublicAssignmentDetail_Private_ThrowsException() {
            publicAssignment.setVisibility(AssignmentVisibility.PRIVATE);
            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));

            assertThatThrownBy(() -> assignmentLibraryService.getPublicAssignmentDetail(10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment is DELETED")
        void getPublicAssignmentDetail_Deleted_ThrowsException() {
            publicAssignment.setStatus(AssignmentStatus.DELETED);
            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));

            assertThatThrownBy(() -> assignmentLibraryService.getPublicAssignmentDetail(10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("cloneAssignmentFromLibrary Tests")
    class CloneAssignmentFromLibraryTests {

        @Test
        @DisplayName("Should clone assignment from library successfully")
        void cloneAssignmentFromLibrary_Success() {
            Assignment cloned = new Assignment();
            cloned.setId(11L);
            AssignmentResponse response = new AssignmentResponse();
            response.setId(11L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));
            when(assignmentCloneHelper.cloneFromLibrary(publicAssignment, teacher)).thenReturn(cloned);
            when(assignmentRepository.save(cloned)).thenReturn(cloned);
            when(assignmentMapper.toAssignmentResponse(cloned)).thenReturn(response);

            AssignmentResponse result = assignmentLibraryService.cloneAssignmentFromLibrary(10L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(11L);
            verify(assignmentRepository).save(cloned);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when teacher not found")
        void cloneAssignmentFromLibrary_TeacherNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentFromLibrary(10L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment not found")
        void cloneAssignmentFromLibrary_AssignmentNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentFromLibrary(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw BadRequestException when assignment is not PUBLIC")
        void cloneAssignmentFromLibrary_NotPublic_ThrowsException() {
            publicAssignment.setVisibility(AssignmentVisibility.PRIVATE);
            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentRepository.findById(10L)).thenReturn(Optional.of(publicAssignment));

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentFromLibrary(10L, 1L))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("getPublicAssignmentSheets Tests")
    class GetPublicAssignmentSheetsTests {

        @Test
        @DisplayName("Should return paginated public assignment sheets")
        void getPublicAssignmentSheets_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AssignmentSheet> page = new PageImpl<>(List.of(publicSheet), pageable, 1);
            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(20L);

            when(assignmentSheetRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(assignmentSheetMapper.toResponse(publicSheet)).thenReturn(response);

            Page<AssignmentSheetResponse> result = assignmentLibraryService.getPublicAssignmentSheets("sheet", pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("cloneAssignmentSheetFromLibrary Tests")
    class CloneAssignmentSheetFromLibraryTests {

        @Test
        @DisplayName("Should clone assignment sheet from library successfully")
        void cloneAssignmentSheetFromLibrary_Success() {
            Assignment item = new Assignment();
            item.setId(50L);
            item.setStatus(AssignmentStatus.PUBLISHED);
            publicSheet.getItems().add(item);

            Assignment clonedItem = new Assignment();
            clonedItem.setId(51L);

            AssignmentSheet savedSheet = AssignmentSheet.builder()
                    .title("Public Sheet")
                    .teacher(teacher)
                    .items(new ArrayList<>())
                    .build();
            savedSheet.setId(21L);

            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(21L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentSheetRepository.findById(20L)).thenReturn(Optional.of(publicSheet));
            when(assignmentSheetRepository.save(any(AssignmentSheet.class))).thenReturn(savedSheet);
            when(assignmentCloneHelper.cloneSheetItemFromLibrary(eq(item), eq(teacher), any())).thenReturn(clonedItem);
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(clonedItem));
            when(assignmentSheetMapper.toResponse(any(AssignmentSheet.class))).thenReturn(response);

            AssignmentSheetResponse result = assignmentLibraryService.cloneAssignmentSheetFromLibrary(20L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(21L);
            verify(assignmentSheetRepository).save(any(AssignmentSheet.class));
            verify(assignmentRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when teacher not found")
        void cloneAssignmentSheetFromLibrary_TeacherNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentSheetFromLibrary(20L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when sheet not found")
        void cloneAssignmentSheetFromLibrary_SheetNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentSheetRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentSheetFromLibrary(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw BadRequestException when sheet is not PUBLIC")
        void cloneAssignmentSheetFromLibrary_NotPublic_ThrowsException() {
            publicSheet.setVisibility(AssignmentVisibility.PRIVATE);
            when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
            when(assignmentSheetRepository.findById(20L)).thenReturn(Optional.of(publicSheet));

            assertThatThrownBy(() -> assignmentLibraryService.cloneAssignmentSheetFromLibrary(20L, 1L))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
