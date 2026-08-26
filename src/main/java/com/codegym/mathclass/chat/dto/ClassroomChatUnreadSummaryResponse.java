package com.codegym.mathclass.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomChatUnreadSummaryResponse {
    private boolean hasGroupUnread;
    private List<Long> unreadStudentIds;
    private Map<Long, Long> studentUnreadCounts;
}
