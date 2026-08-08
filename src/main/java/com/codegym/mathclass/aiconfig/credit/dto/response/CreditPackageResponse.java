package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPackageResponse {
    private Long id;
    private String name;
    private Integer credits;
    private Integer price;
    private Boolean enabled;
    private Integer sortOrder;

    public static CreditPackageResponse from(CreditPackage pkg) {
        return CreditPackageResponse.builder()
                .id(pkg.getId())
                .name(pkg.getName())
                .credits(pkg.getCredits())
                .price(pkg.getPrice())
                .enabled(pkg.getEnabled())
                .sortOrder(pkg.getSortOrder())
                .build();
    }
}
