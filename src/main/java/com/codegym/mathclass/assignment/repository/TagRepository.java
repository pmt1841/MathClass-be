package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Tag;
import com.codegym.mathclass.assignment.entity.TagType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByActiveTrueOrderByTypeAscNameAsc();
    List<Tag> findByActiveTrueAndTypeOrderByNameAsc(TagType type);
    List<Tag> findByIdInAndActiveTrue(Collection<Long> ids);
    Optional<Tag> findByTypeAndName(TagType type, String name);
}
