package com.codegym.mathclass.chat.service.impl;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatMessage;
import com.codegym.mathclass.chat.repository.ChatMessageRepository;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.chat.service.UserPresenceRegistry;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.chat.dto.ClassroomChatUnreadSummaryResponse;
import com.codegym.mathclass.chat.entity.GroupChatReadState;
import com.codegym.mathclass.chat.repository.GroupChatReadStateRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final UserPresenceRegistry userPresenceRegistry;
    private final NotificationService notificationService;
    private final GroupChatReadStateRepository groupChatReadStateRepository;

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
            throw new AccessDeniedException("Bạn không có quyền gửi tin nhắn trong cuộc trò chuyện này");
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .classId(request.getClassId())
                .studentId(request.getStudentId())
                .sender(sender)
                .content(request.getContent().trim())
                .isRead(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        java.time.LocalDateTime msgCreatedAt = savedMessage.getCreatedAt() != null 
                ? savedMessage.getCreatedAt() 
                : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        return ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .classId(savedMessage.getClassId())
                .studentId(savedMessage.getStudentId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderAvatar(sender.getAvatarUrl())
                .content(savedMessage.getContent())
                .isRead(savedMessage.getIsRead())
                .createdAt(msgCreatedAt)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getChatHistory(String classCode, Long studentId, Long currentUserId, Pageable pageable) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Long targetStudentId = studentId;
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;

        if (!isTeacher) {
            targetStudentId = currentUserId;
        } else {
            if (targetStudentId == null) {
                throw new BadRequestException("Giảng viên cần cung cấp ID học sinh để xem tin nhắn riêng");
            }
            if (!classroomRepository.existsByIdAndStudentsId(classroom.getId(), targetStudentId)) {
                throw new BadRequestException("Học sinh không thuộc lớp học này");
            }
        }

        return chatMessageRepository.findHistoryByClassIdAndStudentId(classroom.getId(), targetStudentId, pageable);
    }

    @Override
    @Transactional
    public void markAsRead(String classCode, Long studentId, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Long targetStudentId = studentId;
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;

        if (!isTeacher) {
            targetStudentId = currentUserId;
        } else if (targetStudentId == null) {
            throw new BadRequestException("Giảng viên cần cung cấp ID học sinh");
        }

        chatMessageRepository.markMessagesAsRead(classroom.getId(), targetStudentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getOnlineUsers(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

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

        if (classroom.getTeacher().getId() != currentUserId) {
            throw new AccessDeniedException("Chỉ giảng viên phụ trách lớp học mới được lấy danh sách tin nhắn chưa đọc");
        }

        return chatMessageRepository.findUnreadStudentIds(classroom.getId(), currentUserId);
    }

    private void validateClassMember(Classroom classroom, Long currentUserId) {
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;
        boolean isStudent = classroomRepository.existsByIdAndStudentsId(classroom.getId(), currentUserId);

        if (!isTeacher && !isStudent) {
            throw new AccessDeniedException("Bạn không phải là thành viên của lớp học này");
        }
    }

    @Override
    @Transactional
    public ChatMessageResponse sendGroupMessage(com.codegym.mathclass.chat.dto.GroupChatMessageRequest request, Long currentUserId) {
        Classroom classroom = classroomRepository.findById(request.getClassId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với ID: " + request.getClassId()));

        validateClassMember(classroom, currentUserId);

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng gửi tin"));

        ChatMessage chatMessage = ChatMessage.builder()
                .classId(request.getClassId())
                .chatType(com.codegym.mathclass.chat.entity.ChatType.CLASS_GROUP)
                .sender(sender)
                .content(request.getContent().trim())
                .isRead(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        java.time.LocalDateTime msgCreatedAt = savedMessage.getCreatedAt() != null
                ? savedMessage.getCreatedAt()
                : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        return ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .classId(savedMessage.getClassId())
                .chatType(com.codegym.mathclass.chat.entity.ChatType.CLASS_GROUP)
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderAvatar(sender.getAvatarUrl())
                .content(savedMessage.getContent())
                .isRead(savedMessage.getIsRead())
                .createdAt(msgCreatedAt)
                .build();
    }

    @Override
    @Transactional
    public ChatMessageResponse sendDirectMessage(com.codegym.mathclass.chat.dto.DirectChatMessageRequest request, Long currentUserId) {
        Classroom classroom = classroomRepository.findById(request.getClassId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với ID: " + request.getClassId()));

        validateClassMember(classroom, currentUserId);

        if (currentUserId.equals(request.getRecipientId())) {
            throw new BadRequestException("Không thể gửi tin nhắn cho chính mình");
        }

        boolean isRecipientMember = classroom.getTeacher().getId() == request.getRecipientId()
                || classroomRepository.existsByIdAndStudentsId(request.getClassId(), request.getRecipientId());

        if (!isRecipientMember) {
            throw new BadRequestException("Người nhận không thuộc lớp học này");
        }

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng gửi tin"));

        ChatMessage chatMessage = ChatMessage.builder()
                .classId(request.getClassId())
                .recipientId(request.getRecipientId())
                .chatType(com.codegym.mathclass.chat.entity.ChatType.DIRECT_STUDENT)
                .sender(sender)
                .content(request.getContent().trim())
                .isRead(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        java.time.LocalDateTime msgCreatedAt = savedMessage.getCreatedAt() != null
                ? savedMessage.getCreatedAt()
                : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        return ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .classId(savedMessage.getClassId())
                .recipientId(savedMessage.getRecipientId())
                .chatType(com.codegym.mathclass.chat.entity.ChatType.DIRECT_STUDENT)
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderAvatar(sender.getAvatarUrl())
                .content(savedMessage.getContent())
                .isRead(savedMessage.getIsRead())
                .createdAt(msgCreatedAt)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getGroupChatHistory(String classCode, Long currentUserId, Pageable pageable) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Page<com.codegym.mathclass.chat.entity.ChatMessage> messages =
                chatMessageRepository.findGroupHistoryByClassId(classroom.getId(), pageable);

        return messages.map(m -> ChatMessageResponse.builder()
                .id(m.getId())
                .classId(m.getClassId())
                .studentId(m.getStudentId())
                .recipientId(m.getRecipientId())
                .chatType(m.getChatType())
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .senderName(m.getSender() != null ? m.getSender().getFullName() : "Vô danh")
                .senderAvatar(m.getSender() != null ? m.getSender().getAvatarUrl() : null)
                .content(m.getContent())
                .isRead(m.getIsRead())
                .createdAt(m.getCreatedAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getDirectChatHistory(String classCode, Long otherUserId, Long currentUserId, Pageable pageable) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Page<com.codegym.mathclass.chat.entity.ChatMessage> messages =
                chatMessageRepository.findDirectHistoryByClassIdAndUsers(classroom.getId(), currentUserId, otherUserId, pageable);

        return messages.map(m -> ChatMessageResponse.builder()
                .id(m.getId())
                .classId(m.getClassId())
                .studentId(m.getStudentId())
                .recipientId(m.getRecipientId())
                .chatType(m.getChatType())
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .senderName(m.getSender() != null ? m.getSender().getFullName() : "Vô danh")
                .senderAvatar(m.getSender() != null ? m.getSender().getAvatarUrl() : null)
                .content(m.getContent())
                .isRead(m.getIsRead())
                .createdAt(m.getCreatedAt())
                .build());
    }

    @Override
    @Transactional
    public void markDirectAsRead(String classCode, Long otherUserId, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        chatMessageRepository.markDirectMessagesAsRead(classroom.getId(), otherUserId, currentUserId);
    }

    private java.time.LocalDateTime getGroupLastReadAt(Long classId, Long userId) {
        return groupChatReadStateRepository.findByClassIdAndUserId(classId, userId)
                .map(GroupChatReadState::getLastReadAt)
                .orElse(java.time.LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Override
    @Transactional
    public void markGroupAsRead(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Long classId = classroom.getId();
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        GroupChatReadState readState = groupChatReadStateRepository.findByClassIdAndUserId(classId, currentUserId)
                .orElse(GroupChatReadState.builder()
                        .classId(classId)
                        .userId(currentUserId)
                        .lastReadAt(now)
                        .build());

        readState.setLastReadAt(now);
        groupChatReadStateRepository.save(readState);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getUnreadClassIds(Long currentUserId) {
        List<Long> directUnreadClassIds = chatMessageRepository.findUnreadDirectClassIdsForUser(currentUserId);
        Set<Long> resultSet = new HashSet<>(directUnreadClassIds);

        List<Classroom> classrooms = classroomRepository.findAll();
        for (Classroom c : classrooms) {
            boolean isTeacher = c.getTeacher().getId() == currentUserId;
            boolean isStudent = classroomRepository.existsByIdAndStudentsId(c.getId(), currentUserId);
            if (isTeacher || isStudent) {
                java.time.LocalDateTime lastReadAt = getGroupLastReadAt(c.getId(), currentUserId);
                if (chatMessageRepository.hasUnreadGroupMessage(c.getId(), currentUserId, lastReadAt)) {
                    resultSet.add(c.getId());
                }
            }
        }

        return new ArrayList<>(resultSet);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomChatUnreadSummaryResponse getUnreadSummary(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lớp học với mã: " + classCode));

        validateClassMember(classroom, currentUserId);

        Long classId = classroom.getId();
        java.time.LocalDateTime lastReadAt = getGroupLastReadAt(classId, currentUserId);
        boolean hasGroupUnread = chatMessageRepository.hasUnreadGroupMessage(classId, currentUserId, lastReadAt);

        List<Object[]> rawCounts = chatMessageRepository.findUnreadStudentCountsRaw(classId, currentUserId);
        Map<Long, Long> studentUnreadCounts = new HashMap<>();
        List<Long> unreadStudentIds = new ArrayList<>();

        for (Object[] row : rawCounts) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                Long studentId = (Long) row[0];
                Long count = (Long) row[1];
                studentUnreadCounts.put(studentId, count);
                unreadStudentIds.add(studentId);
            }
        }

        return ClassroomChatUnreadSummaryResponse.builder()
                .hasGroupUnread(hasGroupUnread)
                .unreadStudentIds(unreadStudentIds)
                .studentUnreadCounts(studentUnreadCounts)
                .build();
    }
}

