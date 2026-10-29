package com.occupify.payment.service;

import com.occupify.payment.dto.request.HourlyDisbursementRequest;
import com.occupify.payment.dto.response.DisbursementResponse;

import java.util.List;
import java.util.UUID;

public interface EscrowDisbursementService {

    DisbursementResponse releaseWeeklyPayout(UUID contractId);

    DisbursementResponse releaseMonthlyPayout(UUID contractId);

    DisbursementResponse releaseHourlyPayout(UUID contractId, HourlyDisbursementRequest request);

    DisbursementResponse disburseScheduledPayoutForContract(UUID contractId);

    List<DisbursementResponse> processScheduledPayouts();
}
