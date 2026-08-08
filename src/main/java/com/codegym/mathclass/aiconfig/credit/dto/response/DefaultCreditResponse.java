package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultCreditResponse {
    private String role;
    private Integer defaultCredits;

    public static DefaultCreditResponse from(AiCreditDefault def) {
        return DefaultCreditResponse.builder()
                .role(def.getRole().name())
                .defaultCredits(def.getDefaultCredits())
                .build();
    }
}
