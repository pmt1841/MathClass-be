package com.codegym.mathclass.chat.service.impl;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatMessage;
import com.codegym.mathclass.chat.repository.ChatMessageRepository;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.chat.service.UserPresenceRegistry;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final UserPresenceRegistry userPresenceRegistry;

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, Long currentUserId) {
        Classroom classroom = classroomRepository.findById(request.getClassId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với ID: " + request.getClassId()));

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng gửi tin"));

        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;
        boolean isTargetStudent = request.getStudentId().equals(currentUserId);
        boolean isStudentInClass = classroomRepository.existsByIdAndStudentsId(request.getClassId(), request.getStudentId());

        if (!isStudentInClass) {
            throw new BadRequestException("Học sinh này không thuộc lớp học");
        }

        if (!isTeacher && !isTargetStudent) {
            throw new BadRequestException("Bạn không có quyền gửi tin nhắn trong cuộc trò chuyện này");
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .classId(request.getClassId())
                .studentId(request.getStudentId())
                .sender(sender)
                .content(request.getContent().trim())
                .isRead(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        return ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .classId(savedMessage.getClassId())
                .studentId(savedMessage.getStudentId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderAvatar(sender.getAvatarUrl())
                .content(savedMessage.getContent())
                .isRead(savedMessage.getIsRead())
                .createdAt(savedMessage.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getChatHistory(String classCode, Long studentId, Long currentUserId, Pageable pageable) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        Long targetStudentId = studentId;
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;

        if (!isTeacher) {
            targetStudentId = currentUserId;
        } else if (targetStudentId == null) {
            throw new BadRequestException("Giảng viên cần cung cấp ID học sinh để xem tin nhắn riêng");
        }

        return chatMessageRepository.findHistoryByClassIdAndStudentId(classroom.getId(), targetStudentId, pageable);
    }

    @Override
    @Transactional
    public void markAsRead(String classCode, Long studentId, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        Long targetStudentId = studentId;
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;

        if (!isTeacher) {
            targetStudentId = currentUserId;
        } else if (targetStudentId == null) {
            throw new BadRequestException("Giảng viên cần cung cấp ID học sinh");
        }

        chatMessageRepository.markMessagesAsRead(classroom.getId(), targetStudentId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getOnlineUsers(String classCode) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        Set<Long> allOnlineUserIds = userPresenceRegistry.getOnlineUserIds();
        if (allOnlineUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> onlineMembers = new HashSet<>();
        Long teacherId = classroom.getTeacher().getId();
        if (allOnlineUserIds.contains(teacherId)) {
            onlineMembers.add(teacherId);
        }

        for (User student : classroom.getStudents()) {
            if (allOnlineUserIds.contains(student.getId())) {
                onlineMembers.add(student.getId());
            }
        }
        return onlineMembers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getUnreadStudentIds(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        return chatMessageRepository.findUnreadStudentIds(classroom.getId(), currentUserId);
    }
}
