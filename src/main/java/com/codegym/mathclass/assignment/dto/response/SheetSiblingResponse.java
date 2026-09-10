package com.codegym.mathclass.assignment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SheetSiblingResponse {
    private Long id;
    private String title;
    private String submissionStatus;

    public SheetSiblingResponse(Long id, String title) {
        this.id = id;
        this.title = title;
    }
}
