package com.codegym.mathclass.assignment.dto;

import com.codegym.mathclass.assignment.entity.Tag;
import com.codegym.mathclass.assignment.entity.TagType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TagResponse {
    private long id;
    private String name;
    private TagType type;

    public static TagResponse fromEntity(Tag tag) {
        return TagResponse.builder().id(tag.getId()).name(tag.getName()).type(tag.getType()).build();
    }
}
