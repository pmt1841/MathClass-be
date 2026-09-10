package com.codegym.mathclass.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsResponse {
    private boolean masterEmail;
    private boolean teacherJoinRequest;
    private boolean teacherNewSubmission;
    private boolean studentNewAssignment;
    private boolean studentGraded;
    private boolean studentDeadlineReminder;
}
