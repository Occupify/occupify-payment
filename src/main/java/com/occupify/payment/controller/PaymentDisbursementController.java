package com.occupify.payment.controller;

import com.occupify.payment.dto.request.HourlyDisbursementRequest;
import com.occupify.payment.dto.response.DisbursementResponse;
import com.occupify.payment.dto.response.TransactionResponse;
import com.occupify.payment.dto.response.base.SingleResponse;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.repository.TransactionRepository;
import com.occupify.payment.service.EscrowDisbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentDisbursementController extends AbstractBaseController {

    private final EscrowDisbursementService escrowDisbursementService;
    private final TransactionRepository transactionRepository;
    private final com.occupify.payment.scheduler.ContractPayoutSchedulerManager contractPayoutSchedulerManager;

    @GetMapping("/scheduler/active")
    public ResponseEntity<SingleResponse<List<com.occupify.payment.scheduler.ContractPayoutSchedulerManager.ScheduleInfo>>> getActiveSchedules() {
        return success(contractPayoutSchedulerManager.getActiveSchedules(), "Active payout schedules retrieved successfully");
    }

    @PostMapping("/contracts/{contractId}/release/weekly")
    public ResponseEntity<SingleResponse<DisbursementResponse>> releaseWeekly(@PathVariable UUID contractId) {
        DisbursementResponse response = escrowDisbursementService.releaseWeeklyPayout(contractId);
        return success(response, "Weekly escrow released successfully to freelancer");
    }

    @PostMapping("/contracts/{contractId}/release/monthly")
    public ResponseEntity<SingleResponse<DisbursementResponse>> releaseMonthly(@PathVariable UUID contractId) {
        DisbursementResponse response = escrowDisbursementService.releaseMonthlyPayout(contractId);
        return success(response, "Monthly escrow released successfully to freelancer");
    }

    @PostMapping("/contracts/{contractId}/release/hourly")
    public ResponseEntity<SingleResponse<DisbursementResponse>> releaseHourly(
            @PathVariable UUID contractId,
            @Valid @RequestBody(required = false) HourlyDisbursementRequest request) {
        DisbursementResponse response = escrowDisbursementService.releaseHourlyPayout(contractId, request);
        return success(response, "Hourly escrow released successfully to freelancer");
    }

    @PostMapping("/scheduler/run-payouts")
    public ResponseEntity<SingleResponse<List<DisbursementResponse>>> runScheduledPayouts() {
        List<DisbursementResponse> results = escrowDisbursementService.processScheduledPayouts();
        return success(results, "Executed scheduled payouts for active contracts");
    }

    @GetMapping("/contracts/{contractId}/transactions")
    public ResponseEntity<SingleResponse<List<TransactionResponse>>> getContractTransactions(@PathVariable UUID contractId) {
        List<Transaction> txs = transactionRepository.findByContractId(contractId);
        List<TransactionResponse> dtoList = txs.stream()
                .map(t -> TransactionResponse.builder()
                        .id(t.getId())
                        .walletId(t.getWallet().getId())
                        .contractId(t.getContract().getId())
                        .amount(t.getAmount())
                        .transactionType(t.getTransactionType())
                        .paymentMethod(t.getPaymentMethod())
                        .status(t.getStatus())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return success(dtoList, "Contract transactions retrieved successfully");
    }
}
