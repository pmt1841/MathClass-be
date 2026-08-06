package com.codegym.mathclass.assignment.config;

import com.codegym.mathclass.assignment.entity.Tag;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class TagDataInitializer {
    private final TagRepository tagRepository;

    @Bean
    ApplicationRunner initializeTags() {
        return arguments -> List.of(
                        new TagDefinition("10", TagType.GRADE), new TagDefinition("11", TagType.GRADE), new TagDefinition("12", TagType.GRADE),
                        new TagDefinition("Đại số", TagType.SUBJECT), new TagDefinition("Hình học", TagType.SUBJECT),
                        new TagDefinition("Dễ", TagType.DIFFICULTY), new TagDefinition("Vừa", TagType.DIFFICULTY), new TagDefinition("Khó", TagType.DIFFICULTY))
                .forEach(definition -> {
                    if (tagRepository.findByTypeAndName(definition.type(), definition.name()).isEmpty()) {
                        tagRepository.save(Tag.builder().name(definition.name()).type(definition.type()).build());
                    }
                });
    }

    private record TagDefinition(String name, TagType type) {
    }
}
