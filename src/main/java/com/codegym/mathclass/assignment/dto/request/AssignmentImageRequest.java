package com.codegym.mathclass.assignment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentImageRequest {
    private String imageCode;
    private String imageUrl;
}
