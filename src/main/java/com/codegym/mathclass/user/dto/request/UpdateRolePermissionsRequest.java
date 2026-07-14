package com.codegym.mathclass.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolePermissionsRequest {
    
    @NotNull(message = "Danh sách ID quyền không được để trống")
    private List<Long> permissionIds;
}
