package com.occupify.payment.exception.error;

import com.occupify.payment.exception.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements AppErrorCode {
    WALLET_NOT_FOUND("PAYMENT_404_001", "Ví điện tử không tồn tại", HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND("PAYMENT_404_002", "Giao dịch không tồn tại", HttpStatus.NOT_FOUND),
    CONTRACT_NOT_FOUND("PAYMENT_404_003", "Hợp đồng không tồn tại", HttpStatus.NOT_FOUND),
    INSUFFICIENT_FUNDS("PAYMENT_400_001", "Số dư khả dụng không đủ để thực hiện giao dịch", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_WALLET_ACCESS("PAYMENT_403_001", "Bạn không có quyền truy cập vào ví này", HttpStatus.FORBIDDEN),
    IDEMPOTENCY_CONFLICT("PAYMENT_409_001", "Giao dịch đang được xử lý hoặc đã hoàn tất", HttpStatus.CONFLICT);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;
}
