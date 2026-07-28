package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SheetSiblingDto {
    private Long id;
    private String title;
    private String submissionStatus;

    public SheetSiblingDto(Long id, String title) {
        this.id = id;
        this.title = title;
    }
}
