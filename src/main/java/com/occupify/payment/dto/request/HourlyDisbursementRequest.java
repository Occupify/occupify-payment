package com.occupify.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyDisbursementRequest {
    @DecimalMin(value = "0.01", message = "Hours worked must be greater than 0")
    private Double hoursWorked;

    private BigDecimal customAmount;
    private String note;
}
