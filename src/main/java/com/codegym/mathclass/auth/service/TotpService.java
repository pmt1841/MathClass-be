package com.codegym.mathclass.auth.service;

import java.util.List;

public interface TotpService {
    String generateSecretKey();
    String generateQrCodeDataUrl(String email, String secretKey);
    boolean verifyCode(String secretKey, int code);
    List<String> generateBackupCodes(int count);
}
