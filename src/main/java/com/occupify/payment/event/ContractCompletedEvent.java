package com.occupify.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractCompletedEvent(
    UUID eventId,
    UUID contractId,
    UUID jobId,
    UUID projectId,
    UUID employerId,
    UUID freelancerId,
    UUID completedByUserId,
    String completedByUserRole,
    String title,
    BigDecimal budget,
    String salaryType,
    LocalDateTime finishedAt,
    LocalDateTime timestamp,
    String employerEmail,
    String employerName,
    String freelancerEmail,
    String freelancerName)
    implements Serializable {}
