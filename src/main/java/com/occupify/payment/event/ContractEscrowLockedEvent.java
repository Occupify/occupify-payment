package com.occupify.payment.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ContractEscrowLockedEvent(
    UUID eventId,
    UUID contractId,
    LocalDateTime timestamp
) {}
