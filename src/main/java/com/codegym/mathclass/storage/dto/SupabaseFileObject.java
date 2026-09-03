package com.codegym.mathclass.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupabaseFileObject {

    private String name;
    private String id;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("last_accessed_at")
    private String lastAccessedAt;

    private Map<String, Object> metadata;

    public Instant getCreatedInstant() {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
    }
}
