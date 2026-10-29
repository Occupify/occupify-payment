package com.occupify.payment.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractCompensationRequestedEvent(
    UUID eventId,
    UUID contractId,
    String failedService,
    String failureType,
    String reason,
    LocalDateTime timestamp)
    implements Serializable {}
