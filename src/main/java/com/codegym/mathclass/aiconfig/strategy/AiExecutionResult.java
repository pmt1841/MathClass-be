package com.codegym.mathclass.aiconfig.strategy;

/**
 * Kết quả thực thi prompt AI.
 *
 * @param content          nội dung phản hồi của model
 * @param completionTokens số token đầu ra (output/completion tokens) mà provider báo.
 *                         {@code null} khi provider KHÔNG trả thông tin usage
 *                         -> hệ thống fallback về phí tối thiểu (MAT-255 token-based).
 */
public record AiExecutionResult(String content, Integer completionTokens) {
}
