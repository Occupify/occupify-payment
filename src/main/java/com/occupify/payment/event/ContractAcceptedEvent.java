package com.occupify.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
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
