package com.codegym.mathclass.aiconfig.security;

/**
 * Interface trừu tượng hoá việc cung cấp Master Encryption Key.
 * Tuân thủ nguyên lý Strategy Pattern, OCP và DIP, cho phép hỗ trợ bất kỳ dịch vụ
 * quản lý bí mật nào (Infisical, Vault, AWS Secrets Manager, GCP, Local Env)
 * mà không làm thay đổi logic mã hóa trong EncryptionService.
 */
public interface MasterKeyProvider {

    /**
     * Lấy chuỗi Master Secret Key 256-bit (32 bytes) dùng cho mã hóa AES-256-GCM.
     *
     * @return Chuỗi Master Key plaintext
     */
    String getMasterKey();
}
