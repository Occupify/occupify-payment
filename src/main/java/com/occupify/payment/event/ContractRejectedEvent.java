package com.occupify.payment.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractRejectedEvent(
    UUID eventId,
    UUID contractId,
    UUID jobId,
    UUID employerId,
    UUID freelancerId,
    String reason,
    LocalDateTime timestamp)
    implements Serializable {}
