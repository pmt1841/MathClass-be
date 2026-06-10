package com.codegym.mathclass.assignment.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {

    private String title;

    private String description;

    private String content;
}
