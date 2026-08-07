package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasDataResponse {
    @Builder.Default
    private Integer width = 500;

    @Builder.Default
    private Integer height = 400;

    @Builder.Default
    private List<CanvasElementResponse> elements = new ArrayList<>();
}
