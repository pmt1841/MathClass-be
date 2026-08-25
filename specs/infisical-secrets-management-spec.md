# Specification: Quản lý Khóa Bí mật AI qua Infisical Secret Management (`MathClass-service`)

> **Task Jira:** [MAT-289]  
> **Áp dụng tại:** `com.codegym.mathclass.aiconfig.security` & `com.codegym.mathclass.config`  
> **Đối tượng:** System Administrator, Backend Developers, DevOps, AI Subsystem  

---

## 1. Feature Overview
- **Feature Name:** Quản lý và Nạp Khóa Bí mật (Master Secret Key) Giải mã AI API Key bằng Dịch vụ Bên Thứ Ba (Infisical Secret Management).
- **Target Subsystem:** `MathClass-service` (Backend Spring Boot 4.x / Java 21)
- **Target Components:** `aiconfig/security` (`EncryptionService`, `MasterKeyProvider`, `InfisicalClient`, `ApiKeyCryptoConverter`), `config` (`InfisicalConfigProperties`, `MasterKeyProviderConfig`).

---

## 2. Business Goal & Core Objectives

Bảo vệ an toàn tuyệt đối cho **Master Encryption Key** (khóa chủ 256-bit dùng mã hóa/giải mã API Key các nhà cung cấp OpenAI, Gemini, Claude, DeepSeek... trong CSDL), loại bỏ hoàn toàn việc lưu trữ secret key tĩnh trong file `.env` hoặc mã nguồn:

1. **Tách biệt Hoàn toàn Bí mật khỏi Mã nguồn (12-Factor App & Secret Management):** Chuyển việc quản lý Master Key từ file `.env` cục bộ sang nền tảng quản lý bí mật doanh nghiệp **Infisical**.
2. **Xác thực Định danh Máy chủ Chuẩn Doanh nghiệp (Universal Auth):** Backend xác thực với Infisical API thông qua cặp khóa Machine Identity (`clientId` và `clientSecret`) để lấy Bearer Access Token.
3. **Hiệu năng Tối ưu với In-Memory Caching (Zero Latency Overhead):** Nạp Master Key **1 lần duy nhất lúc khởi động ứng dụng (Application Startup)** và lưu an toàn trong RAM của tiến trình JVM. Tuyệt đối không gửi request mạng sang Infisical khi thực hiện các thao tác giải mã API key runtime.
4. **Kiến trúc Linh hoạt & Đa Nhà Cung Cấp (Pluggable Provider Pattern):** Sử dụng Interface `MasterKeyProvider` cho phép chuyển đổi linh hoạt giữa `InfisicalMasterKeyProvider` và `EnvVarMasterKeyProvider` (fallback/local dev) qua cấu hình `mathclass.infisical.enabled`.
5. **Tuân thủ Chuẩn Bảo mật Zero Key Exposure:** Master Key không bao giờ được ghi ra file Log, System Console, hay trả về bất kỳ API Response DTO nào.

---

## 3. Potential Logic Loopholes & Mitigations (5 Key Edge Cases)

### 3.1. Case 1: Lỗi Mạng / Timeout tới Infisical Cloud khi Khởi động Ứng dụng
- **Vấn đề:** Khi backend khởi động, nếu mạng bị nghẽn hoặc Infisical API bị gián đoạn, ứng dụng có thể bị treo vô hạn (hang) hoặc crash không rõ nguyên nhân.
- **Khắc phục:** 
  - Cấu hình Timeout nghiêm ngặt cho HTTP Client (`connectTimeout = 5000ms`, `readTimeout = 10000ms`).
  - Nếu ở môi trường `production` (`mathclass.infisical.enabled=true`) mà không lấy được key, ứng dụng sẽ ném `IllegalStateException` với thông báo lỗi rõ ràng để DevOps/K8s phát hiện và ngăn chặn deploy phiên bản lỗi (Fail-fast Principle).
  - Nếu ở môi trường local development (`mathclass.infisical.enabled=false`), tự động chuyển sang dùng `EnvVarMasterKeyProvider`.

### 3.2. Case 2: Lộ Master Secret Key trong Log, Exception Stacktrace hoặc Actuator
- **Vấn đề:** Khi xảy ra lỗi xác thực hoặc lỗi lấy secret từ Infisical, nếu in trực tiếp object request/response hoặc URL có chứa query param/token vào console, chuỗi secret hoặc token xác thực có thể bị ghi vào file log.
- **Khắc phục:**
  - Tùy biến `InfisicalClient` chỉ ghi log thông tin meta: trạng thái kết nối, tên biến (`AI_ENCRYPTION_MASTER_KEY`), độ dài key (ví dụ: `32 bytes`).
  - Tuyệt đối không log body chứa `clientSecret`, `accessToken` hay giá trị plaintext của Secret Key.
  - Các Exception ném ra chỉ chứa mã lỗi HTTP và thông điệp chung (ví dụ: `Xác thực Infisical thất bại: HTTP 401 Unauthorized`).

### 3.3. Case 3: Suy giảm Hiệu năng do Gọi Infisical API trên Mỗi Thao Tác Giải Mã (Runtime Latency)
- **Vấn đề:** Nếu mỗi lần học sinh gửi câu hỏi AI, giáo viên tạo bài tập hay chấm điểm lại gửi HTTP request sang Infisical để lấy secret key, độ trễ sẽ tăng thêm 100-300ms và dễ bị Rate-limit từ Infisical API.
- **Khắc phục:**
  - Áp dụng cơ chế **Lazy / Startup In-Memory Caching**: `InfisicalMasterKeyProvider` nạp key vào biến `volatile private String cachedKey` ngay khi khởi tạo Bean hoặc lần gọi đầu tiên.
  - Các lần giải mã sau đó lấy trực tiếp từ RAM trong tiến trình JVM (chi phí 0ms mạng).

### 3.4. Case 4: Xoay Khóa Bí Mật (Secret Rotation) và Xung Đột Dữ Liệu Cũ
- **Vấn đề:** Khi Admin đổi giá trị `AI_ENCRYPTION_MASTER_KEY` trên Infisical Dashboard, các API Key đã mã hóa bằng khóa cũ trong CSDL PostgreSQL sẽ bị lỗi giải mã (`AEADBadTagException`).
- **Khắc phục:**
  - Cung cấp tài liệu quy trình xoay khóa chuẩn (Secret Rotation Workflow): Thông báo khởi chạy quy trình Re-encryption hoặc cập nhật lại API keys sau khi xoay khóa.
  - Bắt lỗi `AEADBadTagException` trong `EncryptionService.decrypt()` và chuyển đổi thành ngoại lệ rõ nghĩa `InvalidApiKeyEncryptionException` thay vì để crash JVM.

### 3.5. Case 5: Rào cản Môi trường Local Dev cho Thành viên Mới (Developer Friction)
- **Vấn đề:** Thành viên mới clone project chưa có tài khoản Infisical hoặc không có kết nối internet sẽ không thể khởi chạy được Backend để làm việc với các chức năng khác.
- **Khắc phục:**
  - Cung cấp cờ cấu hình `mathclass.infisical.enabled=false` (mặc định trong `application.properties` và `.env.example`).
  - Khi tắt Infisical, hệ thống tự động kích hoạt `EnvVarMasterKeyProvider` đọc khóa từ biến `${AI_ENCRYPTION_MASTER_KEY}` trong `.env` cục bộ.

---

## 4. Functional Requirements

- **FR-1 (Universal Auth Authentication):**
  - Gửi yêu cầu `POST /api/v1/auth/universal-auth/login` tới Infisical API với `clientId` và `clientSecret`.
  - Nhận Bearer Token (`accessToken`) có hiệu lực để thực hiện các yêu cầu tiếp theo.
- **FR-2 (Raw Secret Retrieval):**
  - Gửi yêu cầu `GET /api/v3/secrets/raw/{secretName}` với header `Authorization: Bearer <accessToken>`.
  - Truyền các query params: `workspaceId` (Project ID), `environment` (`dev`/`staging`/`prod`), và `secretPath` (`/`).
  - Nhận chuỗi plaintext của `AI_ENCRYPTION_MASTER_KEY`.
- **FR-3 (In-Memory Key Caching):**
  - Lưu trữ khóa Master Key an toàn trong bộ nhớ RAM của Spring Bean `InfisicalMasterKeyProvider`.
  - Cung cấp phương thức `getMasterKey()` trả về khóa tức thì cho `EncryptionService`.
- **FR-4 (Pluggable MasterKeyProvider Architecture):**
  - Cung cấp Interface `MasterKeyProvider` với 2 triển khai:
    - `InfisicalMasterKeyProvider`: Kích hoạt khi `mathclass.infisical.enabled=true`.
    - `EnvVarMasterKeyProvider`: Kích hoạt khi `mathclass.infisical.enabled=false` hoặc thiếu Infisical config.
- **FR-5 (Integration with AES-256-GCM Encryption):**
  - Refactor `EncryptionService` để nhận `MasterKeyProvider` qua Constructor Injection.
  - Đảm bảo `ApiKeyCryptoConverter` (JPA Converter) tự động mã hóa/giải mã API Key của thực thể `AiApiKey` một cách liền mạch.
- **FR-6 (Safe Logging & Diagnostics):**
  - In thông tin khởi tạo thành công: `[Infisical] Nạp Secret Key 'AI_ENCRYPTION_MASTER_KEY' thành công (length: 32 bytes). Khóa đã được cache an toàn trong bộ nhớ.`
  - Không bao giờ in nội dung chuỗi khóa ra log.
- **FR-7 (Full Environment Auto-Injection & Local Override):**
  - Cung cấp `InfisicalEnvironmentInitializer` tự động nạp toàn bộ danh sách secrets từ Infisical (`GET /api/v3/secrets/raw`) vào Spring Environment (`infisicalProperties`).
  - Thiết lập phân cấp độ ưu tiên: `dotenvProperties` (Local `.env`) > `infisicalProperties` (Infisical Shared) > `application.properties` (Defaults).
  - Cho phép lập trình viên tự do định nghĩa biến cá nhân (như tài khoản/mật khẩu Database cục bộ) trong `.env` mà không bị ghi đè bởi cấu hình chung trên Infisical.

---

## 5. Business Rules

- **BR-1 (Key Specification):** Master Secret Key phải đảm bảo độ dài 256-bit (32 bytes). Nếu chuỗi nhận được từ Infisical ngắn hơn hoặc dài hơn, hệ thống tự động chuẩn hóa (pad/trim UTF-8 bytes) đảm bảo chuẩn mã hóa AES-256.
- **BR-2 (Zero Key Exposure):** Master Key tuyệt đối không xuất hiện trong log console, log file, lỗi Exception message hay API response.
- **BR-3 (Production Fail-Fast):** Khi `mathclass.infisical.enabled=true`, nếu không thể kết nối tới Infisical hoặc không tìm thấy biến `AI_ENCRYPTION_MASTER_KEY`, ứng dụng bắt buộc phải dừng khởi động (Fail-fast) để cảnh báo sự cố hạ tầng.
- **BR-4 (Seamless Local Dev):** Khi `mathclass.infisical.enabled=false`, hệ thống bắt buộc phải nạp khóa từ biến môi trường `AI_ENCRYPTION_MASTER_KEY` hoặc fallback key mặc định mà không ném lỗi kết nối.
- **BR-5 (Zero Runtime Overhead):** Quá trình giao tiếp với Infisical API chỉ được phép diễn ra trong giai đoạn khởi động (Startup Phase). Trong suốt quá trình Runtime, mọi thao tác mã hóa/giải mã đều là thao tác in-memory trên CPU.
- **BR-6 (Stateless Client Design):** `InfisicalClient` sử dụng Spring `RestClient` tiêu chuẩn (Spring 6 / Spring Boot 4), không sử dụng thư viện SDK ngoài dạng native JNI để đảm bảo tương thích 100% trên Windows, Linux và Docker ARM64/AMD64.

---

## 6. Data Model & Configuration Properties

### 6.1. Spring Configuration Properties: `InfisicalConfigProperties`
- **Java Class:** `com.codegym.mathclass.config.InfisicalConfigProperties`

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "mathclass.infisical")
public class InfisicalConfigProperties {
    private boolean enabled = false;
    private String host = "https://app.infisical.com";
    private String clientId;
    private String clientSecret;
    private String projectId;
    private String environment = "dev";
    private String secretPath = "/";
    private String secretName = "AI_ENCRYPTION_MASTER_KEY";
}
```

### 6.2. Bảng Tham Số Biến Môi Trường (`.env` & `application.properties`)

| Thuộc Tính Spring | Biến Môi Trường | Mặc Định | Mô Tả |
| :--- | :--- | :--- | :--- |
| `mathclass.infisical.enabled` | `INFISICAL_ENABLED` | `false` | Bật/tắt chế độ lấy key từ Infisical |
| `mathclass.infisical.host` | `INFISICAL_HOST` | `https://app.infisical.com` | Base URL của máy chủ Infisical |
| `mathclass.infisical.client-id` | `INFISICAL_CLIENT_ID` | `""` | Client ID của Machine Identity (Universal Auth) |
| `mathclass.infisical.client-secret` | `INFISICAL_CLIENT_SECRET` | `""` | Client Secret của Machine Identity |
| `mathclass.infisical.project-id` | `INFISICAL_PROJECT_ID` | `""` | Project / Workspace ID trên Infisical |
| `mathclass.infisical.environment` | `INFISICAL_ENV` | `dev` | Môi trường (`dev`, `staging`, `prod`) |
| `mathclass.infisical.secret-path` | `INFISICAL_SECRET_PATH` | `/` | Thư mục chứa secret trên Infisical |
| `mathclass.infisical.secret-name` | `INFISICAL_SECRET_NAME` | `AI_ENCRYPTION_MASTER_KEY` | Tên secret key cần nạp |
| `app.security.ai-encryption-key` | `AI_ENCRYPTION_MASTER_KEY` | *(fallback key)* | Khóa fallback dùng khi `INFISICAL_ENABLED=false` |

---

## 7. Component Contracts & Interfaces

### 7.1. Interface `MasterKeyProvider` (Mẫu Thiết Kế Strategy / Pluggable Provider)
```java
package com.codegym.mathclass.aiconfig.security;

/**
 * Interface trừu tượng hoá việc cung cấp Master Encryption Key.
 * Tuân thủ nguyên lý OCP (Open/Closed Principle) và DIP (Dependency Inversion Principle),
 * cho phép mở rộng hỗ trợ bất kỳ dịch vụ quản lý bí mật nào (Infisical, HashiCorp Vault,
 * AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, Local Env) mà không làm thay đổi EncryptionService.
 */
public interface MasterKeyProvider {
    /**
     * Lấy chuỗi Master Secret Key 256-bit (32 bytes) dùng cho mã hóa AES-256-GCM.
     * @return Chuỗi Master Key plaintext
     */
    String getMasterKey();
}
```

> 🔌 **Khả năng mở rộng Đa Nhà Cung Cấp (Vendor-Agnostic Extensibility):**
> Khi cần bổ sung nhà cung cấp mới, lập trình viên chỉ cần tạo thêm class cài đặt `MasterKeyProvider`:
> - `InfisicalMasterKeyProvider` (Infisical API qua Universal Auth)
> - `VaultMasterKeyProvider` (HashiCorp Vault KV Secrets Engine)
> - `AwsSecretsManagerMasterKeyProvider` (AWS Secrets Manager)
> - `GcpSecretManagerMasterKeyProvider` (Google Cloud Secret Manager)
> - `EnvVarMasterKeyProvider` (Local `.env` / System Properties fallback)


### 7.2. Infisical External API Contract

#### A. Universal Auth Login: `POST /api/v1/auth/universal-auth/login`
- **Request Headers:** `Content-Type: application/json`
- **Request Body:**
```json
{
  "clientId": "your_machine_identity_client_id",
  "clientSecret": "your_machine_identity_client_secret"
}
```
- **Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "expiresIn": 7200,
  "tokenType": "Bearer"
}
```

#### B. Get Raw Secret: `GET /api/v3/secrets/raw/{secretName}`
- **Request Headers:**
  - `Authorization: Bearer <accessToken>`
- **Query Parameters:**
  - `workspaceId`: `<projectId>`
  - `environment`: `<dev|staging|prod>`
  - `secretPath`: `<path, default: />`
- **Response `200 OK`:**
```json
{
  "secret": {
    "secretKey": "AI_ENCRYPTION_MASTER_KEY",
    "secretValue": "MathClassSecretMasterKeyForAI2026!",
    "version": 1
  }
}
```

### 7.3. Refactored `EncryptionService` Contract
```java
package com.codegym.mathclass.aiconfig.security;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    // Primary constructor for Spring DI
    public EncryptionService(MasterKeyProvider masterKeyProvider) {
        String keyString = masterKeyProvider.getMasterKey();
        byte[] keyBytes = new byte[32];
        byte[] rawBytes = keyString.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    // Overload constructor for standalone Unit Testing
    public EncryptionService(String secretKeyString) {
        byte[] keyBytes = new byte[32];
        byte[] rawBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) { ... }
    public String decrypt(String encryptedText) { ... }
}
```

---

## 8. Non-Functional Requirements & Security Constraints

- **Encryption Algorithm:** AES-256-GCM (`AES/GCM/NoPadding`), 128-bit Authentication Tag, 12-byte secure random IV.
- **Transport Security:** Mọi giao tiếp với Infisical API bắt buộc qua giao thức HTTPS (TLS 1.3/1.2).
- **HTTP Client Timeouts:** Connect Timeout: 5 giây, Read Timeout: 10 giây.
- **Resource Footprint:** Tiêu tốn bộ nhớ < 1KB cho in-memory key cache. 0 overhead I/O mạng trong giai đoạn runtime.
- **Zero Third-Party JNI Binaries:** Sử dụng Spring `RestClient` thuần Java, không phụ thuộc vào native shared libraries (`.so`, `.dll`, `.dylib`).

---

## 9. Acceptance Criteria Checklist

- [ ] **AC-1 (Universal Auth Login):** `InfisicalClient` gửi đúng `clientId` và `clientSecret` tới `/api/v1/auth/universal-auth/login` và bóc tách thành công `accessToken`.
- [ ] **AC-2 (Fetch Raw Secret):** `InfisicalClient` gửi đúng token và params tới `/api/v3/secrets/raw/AI_ENCRYPTION_MASTER_KEY` để nhận giá trị secret.
- [ ] **AC-3 (In-Memory Caching):** `InfisicalMasterKeyProvider` chỉ gọi API 1 lần lúc khởi tạo, các lần gọi `getMasterKey()` tiếp theo lấy trực tiếp từ biến cache trong RAM.
- [ ] **AC-4 (Offline Fallback):** Khi `mathclass.infisical.enabled=false`, hệ thống tự động kích hoạt `EnvVarMasterKeyProvider` và nạp key từ file `.env`/properties mà không lỗi.
- [ ] **AC-5 (Encryption Compatibility):** `EncryptionService` mã hóa và giải mã chính xác dữ liệu với key được cung cấp bởi `MasterKeyProvider`.
- [ ] **AC-6 (JPA Converter Integration):** `ApiKeyCryptoConverter` tự động mã hóa API Key khi lưu vào Postgres và giải mã khi đọc từ Postgres.
- [ ] **AC-7 (Fail-fast in Production):** Khi `mathclass.infisical.enabled=true` nhưng thông tin xác thực sai (HTTP 401) hoặc secret không tồn tại (HTTP 404), ứng dụng ném ngoại lệ rõ ràng và dừng khởi động.
- [ ] **AC-8 (Zero Key Leakage in Logs):** Không có bất kỳ dòng log nào chứa plaintext của `clientSecret`, `accessToken` hay `AI_ENCRYPTION_MASTER_KEY`.

---

## 10. Unit & Integration Test Cases Checklist

### 10.1. Backend Unit Tests
- [ ] **UT-BE-01:** `InfisicalClientTest.login_Success()` — Mock API trả về 200 OK với accessToken, client parse đúng token.
- [ ] **UT-BE-02:** `InfisicalClientTest.login_Unauthorized_ThrowsException()` — Mock API trả về 401 Unauthorized, client ném exception an toàn không lộ secret.
- [ ] **UT-BE-03:** `InfisicalClientTest.getSecret_Success()` — Mock API trả về 200 OK với secretValue, client bóc tách đúng chuỗi key.
- [ ] **UT-BE-04:** `InfisicalClientTest.getSecret_NotFound_ThrowsException()` — Mock API trả về 404 Not Found, client ném `IllegalStateException`.
- [ ] **UT-BE-05:** `InfisicalMasterKeyProviderTest.getMasterKey_CachesInMemory()` — Gọi `getMasterKey()` 5 lần nhưng `InfisicalClient` chỉ được gọi duy nhất 1 lần.
- [ ] **UT-BE-06:** `EnvVarMasterKeyProviderTest.getMasterKey_ReturnsConfiguredKey()` — Trả về đúng chuỗi key được cấu hình trong property.
- [ ] **UT-BE-07:** `EncryptionServiceTest.encryptAndDecrypt_WithMasterKeyProvider_Success()` — Mã hóa và giải mã thành công bảo toàn dữ liệu ban đầu.
- [ ] **UT-BE-08:** `EncryptionServiceTest.decrypt_CorruptedTag_ThrowsException()` — Giải mã chuỗi hỏng ném exception.
- [ ] **UT-BE-09:** `ApiKeyCryptoConverterTest.convertToDatabaseAndEntity_Success()` — AttributeConverter hoạt động chính xác qua `EncryptionService`.

### 10.2. Backend Integration & Configuration Tests
- [ ] **IT-BE-01:** `MasterKeyProviderConfigTest.whenInfisicalDisabled_injectsEnvVarProvider()` — Đặt `mathclass.infisical.enabled=false`, Spring context inject Bean `EnvVarMasterKeyProvider`.
- [ ] **IT-BE-02:** `MasterKeyProviderConfigTest.whenInfisicalEnabled_injectsInfisicalProvider()` — Đặt `mathclass.infisical.enabled=true`, Spring context inject Bean `InfisicalMasterKeyProvider`.

---

## 11. Implementation Checklist

- [ ] Tạo `InfisicalConfigProperties.java` tại `com.codegym.mathclass.config`.
- [ ] Tạo Interface `MasterKeyProvider.java` tại `com.codegym.mathclass.aiconfig.security`.
- [ ] Tạo `InfisicalClient.java` (sử dụng Spring `RestClient`) tại `com.codegym.mathclass.aiconfig.security`.
- [ ] Tạo `InfisicalMasterKeyProvider.java` implement `MasterKeyProvider`.
- [ ] Tạo `EnvVarMasterKeyProvider.java` implement `MasterKeyProvider`.
- [ ] Tạo `MasterKeyProviderConfig.java` với `@ConditionalOnProperty` để quản lý việc tạo Bean.
- [ ] Refactor `EncryptionService.java` để inject `MasterKeyProvider`.
- [ ] Cập nhật `application.properties` khai báo các thuộc tính `mathclass.infisical.*`.
- [ ] Viết toàn bộ Unit Tests và Integration Tests đạt 100% checklist mục 10.
- [ ] Chạy `./gradlew compileJava` và `./gradlew test` kiểm tra xác thực.
