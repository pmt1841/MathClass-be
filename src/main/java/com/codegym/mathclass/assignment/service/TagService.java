package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.TagResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.TagType;

import java.util.List;

public interface TagService {
    List<TagResponse> getActiveTags(TagType type);
    List<TagResponse> searchTags(String query);
    void replaceTags(Assignment assignment, List<Long> tagIds);
    void replaceTagsByName(Assignment assignment, List<String> tagNames);
    void requireCompletePublicTags(Assignment assignment);
    void copyTags(Assignment source, Assignment target);
    void validateTagFilters(Long gradeTagId, Long subjectTagId, Long difficultyTagId);
}
