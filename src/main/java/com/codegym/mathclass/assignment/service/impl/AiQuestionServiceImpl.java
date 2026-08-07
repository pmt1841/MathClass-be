package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionServiceImpl implements AiQuestionService {

    private static final String TASK_QUESTION_GEN = "QUESTION_GEN";

    private final KeySelectionService keySelectionService;
    private final TaskConfigRepository taskConfigRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Yêu cầu câu hỏi (Prompt) không được để trống");
        }

        TaskConfig taskConfig = taskConfigRepository.findByTask(TASK_QUESTION_GEN)
                .filter(TaskConfig::getEnabled)
                .orElseThrow(() -> new AiGenerationException("Tính năng sinh đề chưa được cấu hình hoặc đã bị tắt trong AI Config."));

        Provider provider = taskConfig.getProvider();
        if (provider == null || provider.getStatus() != ProviderStatus.ACTIVE) {
            throw new AiGenerationException("Provider cấu hình cho việc sinh đề không tồn tại hoặc đã bị tắt.");
        }

        String rawModel = taskConfig.getModel();
        String modelToUse = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        String systemPrompt = buildSystemPrompt(request);

        Exception lastException = null;
        int maxKeyAttempts = 5;
        int keyAttempts = 0;

        while (keyAttempts < maxKeyAttempts) {
            keyAttempts++;
            ApiKey selectedKey = null;
            String apiKeyString = null;

            try {
                selectedKey = keySelectionService.selectKeyForProvider(provider);
                if (selectedKey != null) {
                    apiKeyString = selectedKey.getEncryptedKey();
                }
            } catch (Exception e) {
                log.warn("Không còn API Key khả dụng từ KeySelectionService: {}", e.getMessage());
            }

            if (apiKeyString == null || apiKeyString.isBlank()) {
                apiKeyString = System.getenv("GEMINI_API_KEY");
            }

            if (apiKeyString == null || apiKeyString.isBlank()) {
                break;
            }

            boolean hasQuotaError = false;

            try {
                log.info("Đang sinh đề bằng model '{}' (Protocol: {}) với Key ID: {}", 
                        modelToUse, provider.getProtocol(), (selectedKey != null ? selectedKey.getId() : "ENV"));

                String responseBody;
                AiGeneratedQuestionResponse dto;

                if (provider.getProtocol() == ProviderProtocol.OPENAI_COMPATIBLE) {
                    responseBody = callOpenAiApi(provider, taskConfig, apiKeyString, systemPrompt, request.getPrompt());
                    dto = parseOpenAiResponse(responseBody);
                } else if (provider.getProtocol() == ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE) {
                    responseBody = callGemini2Api(provider, taskConfig, apiKeyString, systemPrompt, request.getPrompt());
                    dto = parseGeminiResponse(responseBody);
                } else {
                    throw new AiGenerationException("Giao thức " + provider.getProtocol() + " chưa được hỗ trợ.");
                }

                if (dto.getGrade() == null) dto.setGrade(request.getGrade());
                if (dto.getDifficulty() == null) dto.setDifficulty(request.getDifficulty());
                if (dto.getTopic() == null) dto.setTopic(request.getTopic());
                dto.setModel(modelToUse);

                if (!hasRequestedExplanation(request.getPrompt())) {
                    dto.setExplanation("");
                }

                return dto;
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                log.warn("Model '{}' với Key ID {} gặp lỗi khi sinh đề bài: {}", 
                        modelToUse, (selectedKey != null ? selectedKey.getId() : "ENV"), errorMsg);

                if (errorMsg.contains("401")) {
                    if (selectedKey != null) {
                        keySelectionService.markKeyAsInactive(selectedKey.getId());
                    }
                } else if (errorMsg.contains("429")) {
                    hasQuotaError = true;
                }
            }

            if (hasQuotaError && selectedKey != null) {
                keySelectionService.cooldownKey(selectedKey.getId(), 300); // Tạm nghỉ key này 5 phút
            } else if (selectedKey != null && lastException != null && !hasQuotaError) {
                String errorMsg = lastException.getMessage() != null ? lastException.getMessage() : "";
                if (!errorMsg.contains("401")) {
                    keySelectionService.cooldownKey(selectedKey.getId(), 60); // Tạm nghỉ key 60s để tự động xoay key khác ở lượt thử sau
                }
            }
        }

        String detailedMsg = lastException != null ? lastException.getMessage() : "Không tìm thấy API Key khả dụng.";
        if (detailedMsg.contains("429") || detailedMsg.contains("limit: 0")) {
            throw new AiGenerationException("API Key hiện tại của bạn đã dùng hết Quota (Lỗi HTTP 429). Vui lòng cập nhật API Key mới trong trang Quản trị AI Config.");
        }

        throw new AiGenerationException("Không thể sinh đề bài toán bằng Gemini. Lỗi chi tiết: " + detailedMsg);
    }

    private String buildSystemPrompt(GenerateQuestionRequest request) {
        String canvasRequirement = Boolean.TRUE.equals(request.getIncludeCanvasDiagram())
                ? "2. CHÚ Ý: CHỈ KHI người dùng có nhắc đến việc vẽ hình (vd: yêu cầu vẽ minh họa), bạn mới sinh ra object 'canvasData' (chứa điểm, đoạn thẳng, đường tròn) theo chuẩn JSON. Nếu đề bài không yêu cầu vẽ, hãy bỏ qua 'canvasData'."
                : "2. Bài toán này KHÔNG yêu cầu hình vẽ minh họa, TUYỆT ĐỐI KHÔNG sinh ra object 'canvasData'.";

        int grade = request.getGrade() != null ? request.getGrade() : 9;
        String difficulty = request.getDifficulty() != null ? request.getDifficulty() : "THONG_HIEU";
        String topic = request.getTopic() != null ? request.getTopic() : "Toán học";

        StringBuilder infoBuilder = new StringBuilder();
        if (request.getGrade() != null) infoBuilder.append("- Khối lớp: ").append(request.getGrade()).append("\n");
        if (request.getDifficulty() != null) infoBuilder.append("- Mức độ tư duy: ").append(request.getDifficulty()).append("\n");
        if (request.getTopic() != null) infoBuilder.append("- Chủ đề: ").append(request.getTopic()).append("\n");
        if (request.getQuestionType() != null) infoBuilder.append("- Dạng bài: ").append(request.getQuestionType()).append("\n");

        return """
                Bạn là một chuyên gia Toán học và biên soạn đề thi xuất sắc.
                Nhiệm vụ của bạn là sinh ra một bài toán chuẩn sư phạm theo đúng thông tin dưới đây:
                %s
                Yêu cầu định dạng bắt buộc:
                1. Tất cả công thức toán học phải viết dạng KaTeX kẹp giữa dấu $...$ (inline) hoặc $$...$$ (block math). Ví dụ: $x^2 + 2x + 1 = 0$, $\\frac{a}{b}$.
                %s
                3. Về phần lời giải ('explanation'): CHỈ sinh ra nội dung lời giải chi tiết KHI yêu cầu (prompt) của người dùng có đề nghị/nhắc tới việc cung cấp lời giải (ví dụ: 'kèm lời giải', 'giải chi tiết', 'hướng dẫn giải', 'trình bày giải'). Nếu người dùng KHÔNG yêu cầu lời giải, hãy để trường 'explanation' là chuỗi rỗng "".
                4. Trả về ĐÚNG MỘT JSON OBJECT duy nhất, KHÔNG kèm theo văn bản giải thích ngoài JSON, KHÔNG dùng markdown block ```json.

                JSON Schema quy định:
                {
                  "title": "Tiêu đề ngắn gọn cho bài toán",
                  "content": "Nội dung đề bài chi tiết dạng Markdown + KaTeX",
                  "explanation": "Lời giải chi tiết từng bước (nếu người dùng yêu cầu, ngược lại để rỗng \"\")",
                  "grade": %d,
                  "difficulty": "%s",
                  "topic": "%s",
                  "canvasData": {
                    "width": 500,
                    "height": 400,
                    "elements": [
                      { "type": "point", "id": "O", "x": 0.0, "y": 0.0, "label": "O" },
                      { "type": "point", "id": "A", "x": 3.0, "y": 0.0, "label": "A" },
                      { "type": "point", "id": "B", "x": 1.5, "y": 2.598, "label": "B" },
                      { "type": "circle", "id": "c1", "centerId": "O", "radius": 3.0, "pointId": "A" },
                      { "type": "segment", "id": "s1", "fromId": "O", "toId": "A" },
                      { "type": "segment", "id": "s2", "fromId": "O", "toId": "B" }
                    ]
                  }
                }
                Lưu ý quan trọng cho hình vẽ (canvasData):
                - Tọa độ (x, y) của tất cả điểm BẮT BUỘC nằm trong hệ tọa độ Đề-các nhỏ chuẩn mực từ -6.0 đến 6.0 (Ví dụ: A(-2, 3), B(3, 3), C(4, -1), D(-1, -1)). TUYỆT ĐỐI KHÔNG dùng tọa độ dạng pixel (như 100..500) hay số quá lớn (> 15).
                - Khi đề toán có đường tròn, BẮT BUỘC phải tạo điểm tâm (dạng "point"), tạo các điểm trên đường tròn, và thêm phần tử "circle" với "centerId" và "radius" hoặc "pointId".
                """.formatted(infoBuilder.toString(), canvasRequirement, grade, difficulty, topic);
    }

    private String callGemini2Api(Provider provider, TaskConfig taskConfig, String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String rawModel = taskConfig.getModel();
        
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/";
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        String urlPath = rawModel.startsWith("models/") ? rawModel : "models/" + rawModel;
        if (baseUrl.endsWith("models/")) {
            urlPath = urlPath.substring(7);
        }

        String url = baseUrl + urlPath + ":generateContent";

        Map<String, Object> systemPart = Map.of("text", systemPrompt + "\n\nYêu cầu từ người dùng:\n" + userPrompt);
        Map<String, Object> contentPart = Map.of("role", "user", "parts", List.of(systemPart));

        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("responseMimeType", "application/json");
        if (taskConfig.getTemperature() != null) {
            genConfig.put("temperature", taskConfig.getTemperature());
        }
        if (taskConfig.getMaxToken() != null) {
            genConfig.put("maxOutputTokens", taskConfig.getMaxToken());
        }

        Map<String, Object> requestBodyMap = Map.of(
                "contents", List.of(contentPart),
                "generationConfig", genConfig
        );

        String jsonBody = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiGenerationException("Gemini API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private AiGeneratedQuestionResponse parseGeminiResponse(String rawResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                throw new AiGenerationException("Không nhận được phản hồi phù hợp từ Gemini");
            }

            JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiGenerationException("Nội dung phản hồi từ Gemini bị rỗng");
            }

            String jsonText = textNode.asText().trim();
            jsonText = jsonText.replaceAll("(?s)^```[a-z]*\\s*|\\s*```$", "").trim();

            return objectMapper.readValue(jsonText, AiGeneratedQuestionResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Không thể parse JSON từ Gemini: {}", e.getMessage());
            throw new AiGenerationException("Dữ liệu phản hồi từ AI không đúng định dạng JSON chuẩn", e);
        } catch (Exception e) {
            throw new AiGenerationException("Lỗi xử lý kết quả sinh bài toán từ AI: " + e.getMessage(), e);
        }
    }

    private String callOpenAiApi(Provider provider, TaskConfig taskConfig, String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String rawModel = taskConfig.getModel();
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;
        String url = provider.getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Base URL không được để trống đối với giao thức OPENAI_COMPATIBLE");
        }
        if (!url.endsWith("/chat/completions")) {
            url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
        }

        Map<String, Object> systemMessage = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> userMessage = Map.of("role", "user", "content", userPrompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", model);
        requestBodyMap.put("messages", List.of(systemMessage, userMessage));
        if (taskConfig.getTemperature() != null) {
            requestBodyMap.put("temperature", taskConfig.getTemperature());
        }
        if (taskConfig.getMaxToken() != null) {
            requestBodyMap.put("max_tokens", taskConfig.getMaxToken());
        }
        requestBodyMap.put("response_format", Map.of("type", "json_object"));

        String jsonBody = objectMapper.writeValueAsString(requestBodyMap);

        String authHeaderName = provider.getAuthHeaderName() != null && !provider.getAuthHeaderName().isBlank() ? provider.getAuthHeaderName() : "Authorization";
        String authHeaderPrefix = provider.getAuthHeaderPrefix() != null && !provider.getAuthHeaderPrefix().isBlank() ? provider.getAuthHeaderPrefix() + " " : "Bearer ";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(authHeaderName, authHeaderPrefix + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiGenerationException("OpenAI API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private AiGeneratedQuestionResponse parseOpenAiResponse(String rawResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new AiGenerationException("Không nhận được phản hồi phù hợp từ OpenAI API");
            }

            JsonNode messageNode = choices.get(0).path("message").path("content");
            if (messageNode.isMissingNode() || messageNode.asText().isBlank()) {
                throw new AiGenerationException("Nội dung phản hồi từ OpenAI bị rỗng");
            }

            String jsonText = messageNode.asText().trim();
            jsonText = jsonText.replaceAll("(?s)^```[a-z]*\\s*|\\s*```$", "").trim();

            return objectMapper.readValue(jsonText, AiGeneratedQuestionResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Không thể parse JSON từ OpenAI: {}", e.getMessage());
            throw new AiGenerationException("Dữ liệu phản hồi từ AI không đúng định dạng JSON chuẩn", e);
        } catch (Exception e) {
            throw new AiGenerationException("Lỗi xử lý kết quả sinh bài toán từ OpenAI API: " + e.getMessage(), e);
        }
    }

    private boolean hasRequestedExplanation(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("lời giải") || lower.contains("giải chi tiết") || lower.contains("hướng dẫn giải")
                || lower.contains("trình bày") || lower.contains("đáp án") || lower.contains("kèm lời giải")
                || lower.contains("có lời giải") || lower.contains("bài giải") || lower.contains("hướng dẫn");
    }
}

