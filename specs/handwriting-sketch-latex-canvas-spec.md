# Technical Specification: MAT-248 - Handwriting to LaTeX & Sketch to Normalized Geometry Canvas

## 1. Overview & Objectives

**Jira Ticket:** MAT-248  
**Feature Name:** Handwriting Math OCR & Freehand Geometry Sketch to Canvas Normalization  

### Business Objectives:
1. Allow students to sketch math formulas by hand or upload images of handwritten formulas. The system automatically converts handwritten math to LaTeX code (`>= 90%` accuracy) with a live KaTeX preview.
2. Allow students to draw freehand sketches of geometric shapes (imperfect circles, skewed triangles, rough right angles). The AI analyzes the sketch and normalizes it into clean geometric primitives (points, lines, right angles, circles) for rendering on `<JsxGraphBoard />` or Canvas.

---

## 2. Architecture & Design Principles

### Architectural Rules:
- **No Hardcoding:** Must use the central `AiPromptExecutionService` gateway, which delegates to `AiProviderStrategyFactory` (Strategy Pattern) and `KeySelectionService` (Round-Robin / Priority API Key Rotation).
- **Package Scoping:** All new controllers, services, and DTOs for this feature must reside in `com.codegym.mathclass.submission` (e.g. `submission.service`, `submission.controller`, `submission.dto`).
- **No Inline Package Imports:** All imports must be declared at the top of Java source files.
- **DTO Separation:** Request and Response data must use dedicated DTO classes with Bean Validation annotations (`@NotNull`, `@NotBlank`).
- **Admin Configuration & Credit System:** Use admin-configured task settings (`TaskConfig`) and deduct credits via `AiCreditService` (reserve -> settle/refund pattern).

---

## 3. System Tasks & Admin Configuration

| Task Code | Feature Description | Default Cost (Credits) | Tokens Per Credit |
| :--- | :--- | :--- | :--- |
| `HANDWRITING_LATEX` | Chuyển chữ viết tay/ảnh chụp công thức sang mã LaTeX | 2 credits | 1000 tokens |
| `SKETCH_GEOMETRY` | Nắn chỉnh nét vẽ phác thảo thành hình vẽ Canvas chuẩn hóa | 2 credits | 1000 tokens |

---

## 4. API Endpoints Specification

### 4.1. Handwriting to LaTeX API
- **Endpoint:** `POST /api/v1/submissions/ai/handwriting-to-latex`
- **Security:** `@PreAuthorize("isAuthenticated()")`
- **Request Body (`HandwritingLatexRequest`):**
  ```json
  {
    "imageData": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
    "mimeType": "image/png"
  }
  ```
- **Response Body (`ApiResponse<HandwritingLatexResponse>`):**
  ```json
  {
    "success": true,
    "message": "Nhận diện chữ viết tay thành công",
    "data": {
      "latex": "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
      "rawAiOutput": "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"
    }
  }
  ```

### 4.2. Freehand Sketch to Geometry API
- **Endpoint:** `POST /api/v1/submissions/ai/sketch-to-geometry`
- **Security:** `@PreAuthorize("isAuthenticated()")`
- **Request Body (`SketchGeometryRequest`):**
  ```json
  {
    "canvasImageData": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
    "mimeType": "image/png"
  }
  ```
- **Response Body (`ApiResponse<SketchGeometryResponse>`):**
  ```json
  {
    "success": true,
    "message": "Nắn chỉnh hình phác thảo thành công",
    "data": {
      "shapeType": "TRIANGLE_RIGHT",
      "geometryJson": "{\"shapeType\":\"TRIANGLE_RIGHT\",\"points\":[{\"label\":\"A\",\"x\":0,\"y\":4},{\"label\":\"B\",\"x\":0,\"y\":0},{\"label\":\"C\",\"x\":3,\"y\":0}],\"elements\":[{\"type\":\"line\",\"from\":\"A\",\"to\":\"B\"},{\"type\":\"line\",\"from\":\"B\",\"to\":\"C\"},{\"type\":\"line\",\"from\":\"C\",\"to\":\"A\"},{\"type\":\"rightAngle\",\"at\":\"B\"}]}"
    }
  }
  ```

---

## 5. Multimodal Strategy Support (`AiProviderStrategy`)

Extend `AiProviderStrategy` & `AiPromptExecutionService` to support vision/multimodal prompts:
```java
public interface AiProviderStrategy {
    boolean supports(ProviderProtocol protocol);
    AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception;
    
    // Multimodal Vision overload
    default AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey, String prompt, String base64Image, String mimeType) throws Exception {
        return executePrompt(provider, config, apiKey, prompt);
    }
}
```

Implement `executePromptWithImage` in `GoogleGeminiProviderStrategy` and `OpenAiProviderStrategy` to attach inline image data (`inline_data` for Gemini, `image_url` for OpenAI).

---

## 6. Frontend Integration (`MathClass-fe`)

1. **Components:**
   - `<HandwritingLatexModal />`: Canvas drawing pad for handwriting / Image upload file input + KaTeX Preview & insert button.
   - `<SketchGeometryModal />` or `<SketchPadCanvas />`: Sketch pad for geometry + auto-normalization button -> feeds normalized geometry JSON into `<JsxGraphBoard />`.
2. **Services & Hooks:**
   - `submissionAiGradingService.ts` / `submissionHandwritingService.ts`: REST API client calls.
   - `useAiFeatures.ts`: Feature flags for `HANDWRITING_LATEX` & `SKETCH_GEOMETRY`.
