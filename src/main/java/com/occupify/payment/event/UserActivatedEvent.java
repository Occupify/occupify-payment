package com.occupify.payment.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record UserActivatedEvent(
    UUID userId,
    String email,
    String fullName,
    String role,
    String status,
    Instant occurredAt)
    implements Serializable {

  public UserActivatedEvent {
    if (occurredAt == null) {
      occurredAt = Instant.now();
    }
  }
}
