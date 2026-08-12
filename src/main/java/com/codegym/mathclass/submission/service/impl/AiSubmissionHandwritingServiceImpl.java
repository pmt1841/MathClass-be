package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSubmissionHandwritingServiceImpl implements AiSubmissionHandwritingService {

    public static final String HANDWRITING_TASK_CODE = "CANVAS_LATEX";
    public static final String SKETCH_TASK_CODE = "CANVAS_LATEX";

    private final AiPromptExecutionService aiPromptExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId) {
        String prompt = "Bạn là trợ lý OCR nhận diện chữ viết tay công thức toán học chuyên nghiệp.\n"
                + "Nhiệm vụ: Phân tích hình ảnh chữ viết tay/công thức toán này và chuyển đổi thành mã LaTeX tương ứng.\n"
                + "QUY TẮC BẮT BỘC:\n"
                + "1. Nếu hình ảnh KHÔNG chứa chữ viết tay, công thức toán hoặc không có văn bản nào, BẮT BỘC chỉ trả về duy nhất chuỗi: NO_HANDWRITING_DETECTED\n"
                + "2. Nếu hình ảnh có nhiều dòng chữ hoặc công thức toán, BẮT BỘC bọc toàn bộ các dòng trong môi trường \\begin{aligned} ... \\end{aligned} và dùng \\\\ để xuống dòng.\n"
                + "3. Chỉ trả về chuỗi mã LaTeX nguyên bản (ví dụ: \\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}), KHÔNG kèm theo bất kỳ văn bản giải thích hay Markdown code block (như ```latex) nào khác.";

        String aiOutput = aiPromptExecutionService.executePromptWithImage(
                HANDWRITING_TASK_CODE,
                prompt,
                request.getImageData(),
                request.getMimeType(),
                userId
        );

        String cleanLatex = extractCleanLatex(aiOutput);

        return HandwritingLatexResponse.builder()
                .latex(cleanLatex)
                .rawAiOutput(aiOutput)
                .build();
    }

    @Override
    public SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId) {
        String prompt = "Bạn là chuyên gia AI phân tích nét vẽ phác thảo hình học và đồ thị hàm số, chuyển thành dữ liệu JSXGraph JSON.\n"
                + "QUY TẮC BẮT BỘC:\n"
                + "1. Nếu hình ảnh KHÔNG chứa bất kỳ hình phác thảo hình học hoặc đồ thị hàm số nào (ví dụ: chỉ chứa văn bản chữ viết, ảnh màu linh tinh, hoặc không có hình vẽ), BẮT BỘC chỉ trả về duy nhất 1 chuỗi JSON: {\"error\": \"NO_GEOMETRY_DETECTED\", \"shapeType\": \"NO_GEOMETRY\", \"elements\": []}\n"
                + "2. Nếu tìm thấy nét vẽ phác thảo hình học hoặc đồ thị hàm số, hãy nắn chỉnh thành cấu trúc hình học chuẩn JSXGraph và trả về duy nhất 1 đối tượng JSON nguyên bản có cấu trúc chuẩn như sau (KHÔNG bọc trong markdown codeblock):\n"
                + "{\n"
                + "  \"shapeType\": \"TRIANGLE_RIGHT\" | \"TRIANGLE_EQUAL\" | \"CIRCLE\" | \"RECTANGLE\" | \"POLYGON\" | \"FUNCTION_GRAPH\",\n"
                + "  \"boundingbox\": [-5, 5, 5, -5],\n"
                + "  \"axis\": true,\n"
                + "  \"grid\": true,\n"
                + "  \"elements\": [\n"
                + "    {\"type\": \"point\", \"id\": \"A\", \"label\": \"A\", \"x\": 0, \"y\": 4},\n"
                + "    {\"type\": \"point\", \"id\": \"B\", \"label\": \"B\", \"x\": 0, \"y\": 0},\n"
                + "    {\"type\": \"point\", \"id\": \"C\", \"label\": \"C\", \"x\": 3, \"y\": 0},\n"
                + "    {\"type\": \"segment\", \"from\": \"A\", \"to\": \"B\"},\n"
                + "    {\"type\": \"segment\", \"from\": \"B\", \"to\": \"C\"},\n"
                + "    {\"type\": \"segment\", \"from\": \"C\", \"to\": \"A\"},\n"
                + "    {\"type\": \"functiongraph\", \"id\": \"fg1\", \"parsedFunc\": \"-(x-2)**2 + 2\"}\n"
                + "  ]\n"
                + "}\n"
                + "3. QUY TẮC NGUYÊN TẮC KHAI BÁO ĐIỂM: Với mọi đối tượng segment, line, circle, polygon, BẮT BỘC mọi điểm (như from, to, center, pointOnCircle, vertices) được tham chiếu PHẢI được định nghĩa trước dưới dạng phần tử `{\"type\": \"point\", \"id\": \"...\", \"label\": \"...\", \"x\": ..., \"y\": ...}`. Tuyệt đối không để điểm tham chiếu bị thiếu tọa độ x, y.\n"
                + "LƯU Ý DÀNH CHO ĐỒ THỊ HÀM SỐ (PARABOL, ĐƯỜNG THẲNG, HÀM BẬC HAI...):\n"
                + "- Đặt shapeType là \"FUNCTION_GRAPH\".\n"
                + "- Thêm phần tử có `\"type\": \"functiongraph\"`, `\"id\": \"fg1\"`, và `\"parsedFunc\"` là biểu thức JavaScript tính theo biến x (ví dụ: `-(x-2)**2 + 2`, `x**2 - 4*x + 3`, `-x**2 + 4`, `2*x - 1`).\n"
                + "- Thêm các điểm đặc biệt nếu có trên đồ thị (đỉnh parabol, giao điểm các trục) dưới dạng `{\"type\": \"point\", \"id\": \"I\", \"label\": \"I\", \"x\": 2, \"y\": 2}`.\n"
                + "- Tọa độ các điểm và miền vẽ phải nằm trong hệ tọa độ Đề-các chuẩn [-6, 6].";

        String aiOutput = aiPromptExecutionService.executePromptWithImage(
                SKETCH_TASK_CODE,
                prompt,
                request.getCanvasImageData(),
                request.getMimeType(),
                userId
        );

        String cleanJson = extractCleanJson(aiOutput);
        String shapeType = "CUSTOM_GEOMETRY";
        try {
            JsonNode node = objectMapper.readTree(cleanJson);
            if (node.has("shapeType")) {
                shapeType = node.path("shapeType").asText("CUSTOM_GEOMETRY");
            }
        } catch (Exception e) {
            log.warn("Không thể parse JSON shapeType từ AI output: {}", e.getMessage());
        }

        return SketchGeometryResponse.builder()
                .shapeType(shapeType)
                .geometryJson(cleanJson)
                .build();
    }

    private String extractCleanLatex(String output) {
        if (output == null) return "";
        String clean = output.trim();
        if (clean.contains("```")) {
            clean = clean.replaceAll("(?s)^.*?```(?:latex)?\\s*", "")
                         .replaceAll("(?s)\\s*```.*$", "");
        }
        clean = clean.trim();
        if (clean.startsWith("$$") && clean.endsWith("$$") && clean.length() >= 4) {
            clean = clean.substring(2, clean.length() - 2).trim();
        } else if (clean.startsWith("\\[") && clean.endsWith("\\]") && clean.length() >= 4) {
            clean = clean.substring(2, clean.length() - 2).trim();
        }
        return clean;
    }

    private String extractCleanJson(String output) {
        if (output == null) return "{}";
        String clean = output.trim();
        if (clean.contains("```")) {
            clean = clean.replaceAll("(?s)^.*?```(?:json)?\\s*", "")
                         .replaceAll("(?s)\\s*```.*$", "");
        }
        int firstBrace = clean.indexOf('{');
        int lastBrace = clean.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1);
        }
        return clean.trim();
    }
}
