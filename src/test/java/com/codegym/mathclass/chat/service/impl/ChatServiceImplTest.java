package com.codegym.mathclass.chat.service.impl;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatMessage;
import com.codegym.mathclass.chat.repository.ChatMessageRepository;
import com.codegym.mathclass.chat.service.UserPresenceRegistry;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPresenceRegistry userPresenceRegistry;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private User teacher;
    private User student;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(10L);
        teacher.setFullName("Nguyễn Văn A");
        teacher.setEmail("teacher@math.edu.vn");

        student = new User();
        student.setId(20L);
        student.setFullName("Lê Thị B");
        student.setEmail("student@math.edu.vn");

        classroom = new Classroom();
        classroom.setId(100L);
        classroom.setClassCode("MATH101");
        classroom.setClassName("Lớp Đại số");
        classroom.setTeacher(teacher);
        classroom.setStudents(Set.of(student));
    }

    @Test
    @DisplayName("Gửi tin nhắn hợp lệ từ Học sinh đến Giảng viên -> Thành công")
    void sendMessage_success_studentToTeacher() {
        ChatMessageRequest request = new ChatMessageRequest(100L, 20L, "Thầy ơi bài này làm sao ạ?");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(userRepository.findById(20L)).thenReturn(Optional.of(student));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);

        ChatMessage savedMessage = ChatMessage.builder().classId(100L).studentId(20L).sender(student).content("Thầy ơi bài này làm sao ạ?").isRead(false).build();
        savedMessage.setId(1L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ChatMessageResponse response = chatService.sendMessage(request, 20L);

        assertNotNull(response);
        assertEquals("Thầy ơi bài này làm sao ạ?", response.getContent());
        assertEquals(20L, response.getSenderId());
        verify(notificationService, times(1)).saveAndSendNotification(eq(10L), anyString(), anyString());
    }

    @Test
    @DisplayName("Gửi tin nhắn không thuộc lớp học -> ném BadRequestException")
    void sendMessage_fail_studentNotInClass() {
        ChatMessageRequest request = new ChatMessageRequest(100L, 99L, "Chào thầy");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(userRepository.findById(20L)).thenReturn(Optional.of(student));
        when(classroomRepository.existsByIdAndStudentsId(100L, 99L)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> chatService.sendMessage(request, 20L));
    }

    @Test
    @DisplayName("Lấy lịch sử tin nhắn của học sinh thuộc lớp -> Thành công")
    void getChatHistory_success() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);

        ChatMessageResponse msgDto = ChatMessageResponse.builder().id(1L).classId(100L).studentId(20L).content("Hello").build();
        Page<ChatMessageResponse> pageResult = new PageImpl<>(List.of(msgDto));
        when(chatMessageRepository.findHistoryByClassIdAndStudentId(eq(100L), eq(20L), any())).thenReturn(pageResult);

        Page<ChatMessageResponse> result = chatService.getChatHistory("MATH101", 20L, 20L, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("Học sinh ngoài lớp cố lấy lịch sử chat -> Ném AccessDeniedException")
    void getChatHistory_fail_nonMember() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 99L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> chatService.getChatHistory("MATH101", 99L, 99L, PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("Học sinh cố lấy danh sách tin nhắn chưa đọc của Giảng viên -> Ném AccessDeniedException")
    void getUnreadStudentIds_fail_studentCall() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));

        assertThrows(AccessDeniedException.class, () -> chatService.getUnreadStudentIds("MATH101", 20L));
    }

    @Test
    @DisplayName("Giảng viên lấy danh sách tin nhắn chưa đọc -> Thành công")
    void getUnreadStudentIds_success_teacherCall() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(chatMessageRepository.findUnreadStudentIds(100L, 10L)).thenReturn(List.of(20L));

        List<Long> result = chatService.getUnreadStudentIds("MATH101", 10L);

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0));
    }
}
