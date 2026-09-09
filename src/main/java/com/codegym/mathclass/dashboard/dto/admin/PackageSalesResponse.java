package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageSalesResponse {
    private Long packageId;
    private String packageName;
    private Integer credits;
    private Integer price;
    private long salesCount;
}
