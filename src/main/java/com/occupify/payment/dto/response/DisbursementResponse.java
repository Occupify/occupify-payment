package com.occupify.payment.dto.response;

import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
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
public class DisbursementResponse {
    private UUID transactionId;
    private UUID contractId;
    private UUID employerId;
    private UUID freelancerId;
    private SalaryType salaryType;
    private BigDecimal amount;
    private BigDecimal remainingLockedEscrow;
    private BigDecimal freelancerAvailableBalance;
    private TransactionStatus status;
    private String description;
    private Instant disbursedAt;
}
