package com.occupify.payment.dto.response;

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
public class WalletResponse {
    private UUID walletId;
    private UUID userId;
    private String userEmail;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private Instant updatedAt;
}
