package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentTransactionResponse {
    private Long orderId;
    private Long userId;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String packageName;
    private Integer price;
    private Integer credits;
    private String status;
    private LocalDateTime paidAt;
}
