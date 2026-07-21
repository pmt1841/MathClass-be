package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.ProcessJoinRequestDto;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.entity.ClassroomJoinRequest;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassroomJoinRequestServiceImpl implements ClassroomJoinRequestService {

    private final ClassroomJoinRequestRepository joinRequestRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional
    public JoinRequestResponse createJoinRequest(JoinRequestRequest request, long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("Chỉ học sinh mới có thể xin vào lớp");
        }

        Classroom classroom = classroomRepository.findByClassCode(request.getClassCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với mã này"));

        // Check if student is already in the class
        if (classroom.getStudents().stream().anyMatch(s -> s.getId() == studentId)) {
            throw new BadRequestException("Bạn đã ở trong lớp học này rồi");
        }

        // Check if there is an existing PENDING request
        Optional<ClassroomJoinRequest> existingRequest = joinRequestRepository
                .findByClassroomIdAndStudentIdAndStatus(classroom.getId(), studentId, JoinRequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            throw new BadRequestException("Bạn đã gửi yêu cầu tham gia lớp này và đang chờ duyệt");
        }

        ClassroomJoinRequest joinRequest = new ClassroomJoinRequest();
        joinRequest.setClassroom(classroom);
        joinRequest.setStudent(student);
        joinRequest.setStatus(JoinRequestStatus.PENDING);

        joinRequest = joinRequestRepository.save(joinRequest);

        // Send email to teacher
        User teacher = classroom.getTeacher();
        String subject = "Có học sinh xin vào lớp học " + classroom.getClassName();
        String classroomLink = frontendUrl + "/classes/" + classroom.getClassCode() + "/requests";
        Context context = new Context();
        context.setVariable("teacherName", teacher.getFullName());
        context.setVariable("studentName", student.getFullName());
        context.setVariable("studentEmail", student.getEmail());
        context.setVariable("className", classroom.getClassName());
        context.setVariable("classCode", classroom.getClassCode());
        context.setVariable("classroomLink", classroomLink);
        emailService.sendHtmlMailAsync(teacher.getEmail(), subject, "join-request-teacher", context);

        notificationService.saveAndSendNotification(teacher.getId(), subject,
                "/classes/" + classroom.getClassCode() + "/requests");

        return JoinRequestResponse.fromEntity(joinRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getPendingJoinRequests(String classCode, long teacherId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        if (classroom.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không phải là giáo viên phụ trách lớp học này");
        }

        List<ClassroomJoinRequest> requests = joinRequestRepository.findByClassroomClassCodeAndStatus(classCode,
                JoinRequestStatus.PENDING);
        return requests.stream()
                .map(JoinRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JoinRequestResponse processJoinRequest(Long requestId, ProcessJoinRequestDto requestDto, long teacherId) {
        ClassroomJoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu tham gia"));

        Classroom classroom = joinRequest.getClassroom();
        if (classroom.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không phải là giáo viên phụ trách lớp học này");
        }

        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("Yêu cầu này đã được xử lý");
        }

        JoinRequestStatus newStatus = requestDto.getStatus();
        if (newStatus == JoinRequestStatus.PENDING) {
            throw new BadRequestException("Không thể cập nhật trạng thái thành PENDING");
        }

        joinRequest.setStatus(newStatus);
        joinRequestRepository.save(joinRequest);

        User student = joinRequest.getStudent();

        if (newStatus == JoinRequestStatus.APPROVED) {
            // Re-use logic to add student
            if (classroom.getMaxStudents() != null && classroom.getStudents().size() >= classroom.getMaxStudents()) {
                throw new BadRequestException("Lớp học đã đạt số lượng tối đa");
            }
            classroom.getStudents().add(student);
            classroomRepository.save(classroom);

            // Email is already sent by classroomService if we used that, but we add
            // directly here to avoid sending the standard add email.
            // Better to use custom email here since they requested it.
            String subject = "Yêu cầu tham gia lớp học " + classroom.getClassName() + " đã được DUYỆT";
            String classroomLink = frontendUrl + "/classes/" + classroom.getClassCode() + "/student";
            Context context = new Context();
            context.setVariable("studentName", student.getFullName());
            context.setVariable("isApproved", true);
            context.setVariable("className", classroom.getClassName());
            context.setVariable("classCode", classroom.getClassCode());
            context.setVariable("teacherName", classroom.getTeacher().getFullName());
            context.setVariable("classroomLink", classroomLink);
            emailService.sendHtmlMailAsync(student.getEmail(), subject, "join-request-result", context);
            notificationService.saveAndSendNotification(student.getId(), subject,
                    "/classes/" + classroom.getClassCode());
        } else if (newStatus == JoinRequestStatus.REJECTED) {
            String subject = "Yêu cầu tham gia lớp học " + classroom.getClassName() + " đã BỊ TỪ CHỐI";
            Context context = new Context();
            context.setVariable("studentName", student.getFullName());
            context.setVariable("isApproved", false);
            context.setVariable("className", classroom.getClassName());
            context.setVariable("classCode", classroom.getClassCode());
            context.setVariable("teacherName", classroom.getTeacher().getFullName());
            emailService.sendHtmlMailAsync(student.getEmail(), subject, "join-request-result", context);
            notificationService.saveAndSendNotification(student.getId(), subject, null);
        }

        return JoinRequestResponse.fromEntity(joinRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getMyJoinRequests(long studentId) {
        List<ClassroomJoinRequest> requests = joinRequestRepository.findByStudentId(studentId);
        return requests.stream()
                .map(JoinRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
