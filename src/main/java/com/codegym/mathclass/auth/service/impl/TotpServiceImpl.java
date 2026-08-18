package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.exception.BadRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TotpServiceImpl implements TotpService {

    private final GoogleAuthenticator gAuth;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    public TotpServiceImpl() {
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                .setWindowSize(3) // Current step, +1, -1 window tolerance
                .build();
        this.gAuth = new GoogleAuthenticator(config);
    }

    @Override
    public String generateSecretKey() {
        GoogleAuthenticatorKey credentials = gAuth.createCredentials();
        return credentials.getKey();
    }

    @Override
    public String generateQrCodeDataUrl(String email, String secretKey) {
        try {
            String otpAuthUrl = String.format("otpauth://totp/MathClass:%s?secret=%s&issuer=MathClass", email, secretKey);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 260, 260, hints);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            log.error("Error generating QR code for email {}: {}", email, e.getMessage());
            throw new BadRequestException("Không thể tạo mã QR cho xác thực 2 bước: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyCode(String secretKey, int code) {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            return false;
        }
        try {
            return gAuth.authorize(secretKey, code);
        } catch (Exception e) {
            log.warn("Error authorizing TOTP code: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                int index = secureRandom.nextInt(CODE_CHARS.length());
                sb.append(CODE_CHARS.charAt(index));
            }
            codes.add(sb.toString());
        }
        return codes;
    }
}
