package com.codegym.mathclass.config;

import com.codegym.mathclass.aiconfig.credit.repository.AiCreditConfigRepository;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditDefaultRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.repository.SystemPromptHistoryRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.assignment.repository.AssignmentDrawingRepository;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.notification.repository.NotificationRepository;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.submission.repository.SubmissionCommentRepository;
import com.codegym.mathclass.submission.repository.SubmissionDrawingRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private ClassroomJoinRequestRepository classroomJoinRequestRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentDrawingRepository assignmentDrawingRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionCommentRepository submissionCommentRepository;
    @Mock
    private SubmissionDrawingRepository submissionDrawingRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private AiCreditService aiCreditService;
    @Mock
    private AiCreditDefaultRepository aiCreditDefaultRepository;
    @Mock
    private AiCreditConfigRepository aiCreditConfigRepository;
    @Mock
    private CreditPackageRepository creditPackageRepository;
    @Mock
    private SystemPromptRepository systemPromptRepository;
    @Mock
    private SystemPromptHistoryRepository systemPromptHistoryRepository;

    @InjectMocks
    private DatabaseSeeder databaseSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(databaseSeeder, "isSeedEnabled", true);
        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        lenient().when(permissionRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(aiCreditDefaultRepository.findByRole(any())).thenReturn(Optional.empty());
        lenient().when(aiCreditConfigRepository.findByTask(anyString())).thenReturn(Optional.empty());
        lenient().when(creditPackageRepository.count()).thenReturn(0L);
        lenient().when(systemPromptRepository.findByCode(anyString())).thenReturn(Optional.empty());
        lenient().when(systemPromptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(systemPromptHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should skip seeding if seed is disabled")
    void shouldSkipSeedingIfDisabled() throws Exception {
        ReflectionTestUtils.setField(databaseSeeder, "isSeedEnabled", false);

        databaseSeeder.run();

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should skip seeding if database is not empty")
    void shouldSkipSeedingIfDatabaseNotEmpty() throws Exception {
        when(userRepository.count()).thenReturn(5L);

        databaseSeeder.run();

        verify(userRepository, times(1)).count();
    }

    @Test
    @DisplayName("Should seed database successfully when empty")
    void shouldSeedDatabaseSuccessfullyWhenEmpty() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classroomJoinRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentDrawingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionCommentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionDrawingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        databaseSeeder.run();

        verify(userRepository, times(1)).count();
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(classroomRepository, atLeastOnce()).save(any());
        verify(classroomJoinRequestRepository, atLeastOnce()).save(any());
        verify(assignmentRepository, atLeastOnce()).save(any());
        verify(assignmentDrawingRepository, atLeastOnce()).save(any());
        verify(submissionRepository, atLeastOnce()).save(any());
        verify(submissionCommentRepository, atLeastOnce()).save(any());
        verify(submissionDrawingRepository, atLeastOnce()).save(any());
        verify(notificationRepository, atLeastOnce()).save(any());
        verify(notificationSettingsRepository, atLeastOnce()).save(any());
    }
}
