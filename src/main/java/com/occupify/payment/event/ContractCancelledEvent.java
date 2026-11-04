package com.occupify.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractCancelledEvent(
    UUID eventId,
    UUID contractId,
    UUID jobId,
    UUID projectId,
    UUID employerId,
    UUID freelancerId,
    UUID cancelledByUserId,
    String cancelledByRole,
    String reason,
    String title,
    BigDecimal budget,
    String salaryType,
    LocalDateTime timestamp,
    String employerEmail,
    String employerName,
    String freelancerEmail,
    String freelancerName)
    implements Serializable {}
