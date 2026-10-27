package com.occupify.payment.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractAcceptedEvent(
    UUID eventId,
    UUID contractId,
    UUID jobId,
    UUID projectId,
    UUID employerId,
    UUID freelancerId,
    String title,
    BigDecimal budget,
    String salaryType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime timestamp)
    implements Serializable {}
