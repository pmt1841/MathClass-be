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

    @Mock
    private com.codegym.mathclass.chat.repository.GroupChatReadStateRepository groupChatReadStateRepository;

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

    @Test
    @DisplayName("Lấy tổng quan tin nhắn chưa đọc bao gồm số lượng chat lớp và chat 1-1 -> Thành công")
    void getUnreadSummary_success_returnsGroupUnreadCountAndStudentCounts() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);
        when(groupChatReadStateRepository.findByClassIdAndUserId(100L, 20L)).thenReturn(Optional.empty());
        when(chatMessageRepository.countUnreadGroupMessages(eq(100L), eq(20L), any())).thenReturn(5L);

        Object[] rawCountRow = new Object[]{30L, 2L};
        List<Object[]> rawCountsList = java.util.Collections.singletonList(rawCountRow);
        when(chatMessageRepository.findUnreadStudentCountsRaw(100L, 20L)).thenReturn(rawCountsList);

        com.codegym.mathclass.chat.dto.ClassroomChatUnreadSummaryResponse response =
                chatService.getUnreadSummary("MATH101", 20L);

        assertNotNull(response);
        assertTrue(response.isHasGroupUnread());
        assertEquals(5L, response.getGroupUnreadCount());
        assertEquals(1, response.getUnreadStudentIds().size());
        assertEquals(30L, response.getUnreadStudentIds().get(0));
        assertEquals(2L, response.getStudentUnreadCounts().get(30L));
    }

    @Test
    @DisplayName("Thành viên lớp gửi tin nhắn nhóm thành công -> sendGroupMessage_success")
    void sendGroupMessage_success() {
        com.codegym.mathclass.chat.dto.GroupChatMessageRequest req =
                new com.codegym.mathclass.chat.dto.GroupChatMessageRequest(100L, "Chào cả lớp!");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);
        when(userRepository.findById(20L)).thenReturn(Optional.of(student));

        ChatMessage saved = ChatMessage.builder()
                .classId(100L)
                .chatType(com.codegym.mathclass.chat.entity.ChatType.CLASS_GROUP)
                .sender(student)
                .content("Chào cả lớp!")
                .isRead(false)
                .build();
        saved.setId(10L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageResponse res = chatService.sendGroupMessage(req, 20L);

        assertNotNull(res);
        assertEquals(100L, res.getClassId());
        assertEquals("Chào cả lớp!", res.getContent());
        assertEquals(com.codegym.mathclass.chat.entity.ChatType.CLASS_GROUP, res.getChatType());
    }

    @Test
    @DisplayName("Người ngoài lớp cố gửi tin nhắn nhóm -> Ném AccessDeniedException (sendGroupMessage_fail_nonMember)")
    void sendGroupMessage_fail_nonMember() {
        com.codegym.mathclass.chat.dto.GroupChatMessageRequest req =
                new com.codegym.mathclass.chat.dto.GroupChatMessageRequest(100L, "Xin chào");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 99L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> chatService.sendGroupMessage(req, 99L));
    }

    @Test
    @DisplayName("Học sinh A gửi tin 1-1 cho Học sinh B cùng lớp thành công -> sendDirectMessage_success_studentToStudent")
    void sendDirectMessage_success_studentToStudent() {
        User studentB = new User();
        studentB.setId(30L);
        studentB.setFullName("Nguyễn Văn C");

        com.codegym.mathclass.chat.dto.DirectChatMessageRequest req =
                new com.codegym.mathclass.chat.dto.DirectChatMessageRequest(100L, 30L, "Chào B nhé");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);
        when(classroomRepository.existsByIdAndStudentsId(100L, 30L)).thenReturn(true);
        when(userRepository.findById(20L)).thenReturn(Optional.of(student));

        ChatMessage saved = ChatMessage.builder()
                .classId(100L)
                .recipientId(30L)
                .chatType(com.codegym.mathclass.chat.entity.ChatType.DIRECT_STUDENT)
                .sender(student)
                .content("Chào B nhé")
                .isRead(false)
                .build();
        saved.setId(11L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageResponse res = chatService.sendDirectMessage(req, 20L);

        assertNotNull(res);
        assertEquals(30L, res.getRecipientId());
        assertEquals("Chào B nhé", res.getContent());
    }

    @Test
    @DisplayName("Học sinh cố tự gửi tin nhắn 1-1 cho chính mình -> Ném BadRequestException (sendDirectMessage_fail_selfMessage)")
    void sendDirectMessage_fail_selfMessage() {
        com.codegym.mathclass.chat.dto.DirectChatMessageRequest req =
                new com.codegym.mathclass.chat.dto.DirectChatMessageRequest(100L, 20L, "Tự gửi mình");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> chatService.sendDirectMessage(req, 20L));
    }

    @Test
    @DisplayName("Gửi tin nhắn 1-1 cho người nhận không thuộc lớp -> Ném BadRequestException (sendDirectMessage_fail_recipientNotMember)")
    void sendDirectMessage_fail_recipientNotMember() {
        com.codegym.mathclass.chat.dto.DirectChatMessageRequest req =
                new com.codegym.mathclass.chat.dto.DirectChatMessageRequest(100L, 99L, "Chào người ngoài");
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);
        when(classroomRepository.existsByIdAndStudentsId(100L, 99L)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> chatService.sendDirectMessage(req, 20L));
    }

    @Test
    @DisplayName("Đánh dấu Chat Lớp nhóm là đã đọc -> markGroupAsRead_success")
    void markGroupAsRead_success() {
        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(classroomRepository.existsByIdAndStudentsId(100L, 20L)).thenReturn(true);
        when(groupChatReadStateRepository.findByClassIdAndUserId(100L, 20L)).thenReturn(Optional.empty());

        chatService.markGroupAsRead("MATH101", 20L);

        verify(groupChatReadStateRepository, times(1)).save(any(com.codegym.mathclass.chat.entity.GroupChatReadState.class));
    }
}
