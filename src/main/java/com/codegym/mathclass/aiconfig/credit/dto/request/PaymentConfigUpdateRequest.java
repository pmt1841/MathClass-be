package com.codegym.mathclass.aiconfig.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfigUpdateRequest {

    @NotBlank(message = "Mã ngân hàng không được để trống")
    @Size(max = 20, message = "Mã ngân hàng tối đa 20 ký tự")
    private String bankCode;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 100, message = "Tên chủ tài khoản tối đa 100 ký tự")
    private String accountHolderName;

    private String sepayApiKey;

    @NotBlank(message = "Tiền tố cú pháp không được để trống")
    @Size(min = 2, max = 10, message = "Tiền tố cú pháp phải từ 2 đến 10 ký tự")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]*$", message = "Tiền tố phải bắt đầu bằng chữ cái và chỉ chứa chữ cái không dấu hoặc chữ số")
    private String transferSyntaxPrefix;

    @NotBlank(message = "Mẫu giao diện VietQR không được để trống")
    private String qrTemplate;

    private Boolean isActive;
}
