package com.occupify.payment.dto.response;

import com.occupify.payment.enums.PaymentMethod;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private UUID walletId;
    private UUID contractId;
    private BigDecimal amount;
    private TransactionType transactionType;
    private PaymentMethod paymentMethod;
    private TransactionStatus status;
    private Instant createdAt;
}
