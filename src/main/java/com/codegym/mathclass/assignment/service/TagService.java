package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.TagResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.TagType;

import java.util.List;

public interface TagService {
    List<TagResponse> getActiveTags(TagType type);
    void replaceTags(Assignment assignment, List<Long> tagIds);
    void requireCompletePublicTags(Assignment assignment);
    void copyTags(Assignment source, Assignment target);
}
