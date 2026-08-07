package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasElementResponse {
    private String type;          // "point", "segment", "circle", "angle"
    private String id;
    private Double x;             // Tọa độ X (dành cho point)
    private Double y;             // Tọa độ Y (dành cho point)
    private String label;         // Nhãn (vd: "A", "B", "O")
    private String labelPosition; // "top", "bottom", "left", "right", "top-left"...
    private String centerId;      // Id điểm tâm (dành cho circle)
    private Double radius;        // Bán kính (dành cho circle)
    @JsonAlias({"fromId", "startId"})
    private String fromId;        // Id điểm bắt đầu (dành cho segment)
    @JsonAlias({"toId", "endId"})
    private String toId;          // Id điểm kết thúc (dành cho segment)
    private String style;         // "solid", "dashed"
}
