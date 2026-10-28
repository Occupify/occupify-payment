package com.occupify.payment.service.impl;

import com.occupify.payment.dto.request.HourlyDisbursementRequest;
import com.occupify.payment.dto.response.DisbursementResponse;
import com.occupify.payment.entity.Contract;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.PaymentMethod;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import com.occupify.payment.exception.BaseAppException;
import com.occupify.payment.exception.error.PaymentErrorCode;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.repository.TransactionRepository;
import com.occupify.payment.repository.WalletRepository;
import com.occupify.payment.service.EscrowDisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowDisbursementServiceImpl implements EscrowDisbursementService {

    private final ContractRepository contractRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Value("${app.payout.scheduler.weekly-cycle-days:7}")
    private int weeklyCycleDays;

    @Value("${app.payout.scheduler.monthly-cycle-days:30}")
    private int monthlyCycleDays;

    @Override
    @Transactional
    public DisbursementResponse releaseWeeklyPayout(UUID contractId) {
        log.info("[Escrow Disbursement] Processing WEEKLY payout for contractId: {}", contractId);
        Contract contract = getActiveContract(contractId);

        if (contract.getSalaryType() != SalaryType.WEEKLY) {
            throw new BaseAppException(PaymentErrorCode.INVALID_SALARY_TYPE,
                    "Contract " + contractId + " salary type is not WEEKLY (actual: " + contract.getSalaryType() + ")");
        }

        BigDecimal rate = contract.getAgreedRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_LOCKED_ESCROW,
                    "Contract does not have a valid weekly rate configured");
        }

        return executeEscrowRelease(contract, rate, "Weekly payroll escrow disbursement");
    }

    @Override
    @Transactional
    public DisbursementResponse releaseMonthlyPayout(UUID contractId) {
        log.info("[Escrow Disbursement] Processing MONTHLY payout for contractId: {}", contractId);
        Contract contract = getActiveContract(contractId);

        if (contract.getSalaryType() != SalaryType.MONTHLY) {
            throw new BaseAppException(PaymentErrorCode.INVALID_SALARY_TYPE,
                    "Contract " + contractId + " salary type is not MONTHLY (actual: " + contract.getSalaryType() + ")");
        }

        BigDecimal rate = contract.getAgreedRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_LOCKED_ESCROW,
                    "Contract does not have a valid monthly rate configured");
        }

        return executeEscrowRelease(contract, rate, "Monthly payroll escrow disbursement");
    }

    @Override
    @Transactional
    public DisbursementResponse releaseHourlyPayout(UUID contractId, HourlyDisbursementRequest request) {
        log.info("[Escrow Disbursement] Processing HOURLY payout for contractId: {}, request: {}", contractId, request);
        Contract contract = getActiveContract(contractId);

        if (contract.getSalaryType() != SalaryType.HOURLY) {
            throw new BaseAppException(PaymentErrorCode.INVALID_SALARY_TYPE,
                    "Contract " + contractId + " salary type is not HOURLY (actual: " + contract.getSalaryType() + ")");
        }

        BigDecimal amount;
        if (request != null && request.getCustomAmount() != null && request.getCustomAmount().compareTo(BigDecimal.ZERO) > 0) {
            amount = request.getCustomAmount();
        } else if (request != null && request.getHoursWorked() != null && request.getHoursWorked() > 0) {
            BigDecimal rate = contract.getAgreedRate() != null ? contract.getAgreedRate() : BigDecimal.ZERO;
            amount = rate.multiply(BigDecimal.valueOf(request.getHoursWorked()));
        } else {
            throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_FUNDS,
                    "Please provide valid hoursWorked or customAmount for hourly disbursement");
        }

        String note = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote()
                : String.format("Hourly payout for %.2f hours", request != null && request.getHoursWorked() != null ? request.getHoursWorked() : 0.0);

        return executeEscrowRelease(contract, amount, note);
    }

    @Override
    @Transactional
    public DisbursementResponse disburseScheduledPayoutForContract(UUID contractId) {
        log.info("[Escrow Disbursement] Executing scheduled payout for contractId: {}", contractId);
        Contract contract = getActiveContract(contractId);

        if (contract.getSalaryType() == SalaryType.WEEKLY) {
            return releaseWeeklyPayout(contractId);
        } else if (contract.getSalaryType() == SalaryType.MONTHLY) {
            return releaseMonthlyPayout(contractId);
        } else if (contract.getSalaryType() == SalaryType.HOURLY) {
            BigDecimal rate = contract.getAgreedRate();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_LOCKED_ESCROW,
                        "Hourly contract does not have a valid rate configured");
            }
            return executeEscrowRelease(contract, rate, "Automated hourly scheduled escrow payout");
        } else {
            throw new BaseAppException(PaymentErrorCode.INVALID_SALARY_TYPE,
                    "Contract " + contractId + " salary type " + contract.getSalaryType() + " is not supported for scheduled payout");
        }
    }

    @Override
    @Transactional
    public List<DisbursementResponse> processScheduledPayouts() {
        log.info("[Escrow Disbursement Scheduler] Running scheduled payout scan...");
        List<Contract> activeContracts = contractRepository.findByStatus(ContractStatus.IN_PROGRESS);
        List<DisbursementResponse> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Contract contract : activeContracts) {
            try {
                if (contract.getSalaryType() == SalaryType.WEEKLY) {
                    LocalDateTime threshold = contract.getLastPayoutAt() != null
                            ? contract.getLastPayoutAt().plusDays(weeklyCycleDays)
                            : (contract.getStartedAt() != null ? contract.getStartedAt().plusDays(weeklyCycleDays) : now);

                    if (!now.isBefore(threshold)) {
                        log.info("[Scheduler] Triggering auto weekly payout for contractId: {}", contract.getId());
                        results.add(releaseWeeklyPayout(contract.getId()));
                    }
                } else if (contract.getSalaryType() == SalaryType.MONTHLY) {
                    LocalDateTime threshold = contract.getLastPayoutAt() != null
                            ? contract.getLastPayoutAt().plusDays(monthlyCycleDays)
                            : (contract.getStartedAt() != null ? contract.getStartedAt().plusDays(monthlyCycleDays) : now);

                    if (!now.isBefore(threshold)) {
                        log.info("[Scheduler] Triggering auto monthly payout for contractId: {}", contract.getId());
                        results.add(releaseMonthlyPayout(contract.getId()));
                    }
                }
            } catch (Exception ex) {
                log.warn("[Scheduler] Failed auto payout for contract [{}]: {}", contract.getId(), ex.getMessage());
            }
        }

        log.info("[Escrow Disbursement Scheduler] Completed payout scan. Disbursed: {} contracts", results.size());
        return results;
    }

    private Contract getActiveContract(UUID contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BaseAppException(PaymentErrorCode.CONTRACT_NOT_FOUND, "Contract " + contractId + " not found"));

        if (contract.getStatus() != ContractStatus.IN_PROGRESS) {
            throw new BaseAppException(PaymentErrorCode.INVALID_CONTRACT_STATUS,
                    "Contract " + contractId + " is not IN_PROGRESS (actual: " + contract.getStatus() + ")");
        }
        return contract;
    }

    private DisbursementResponse executeEscrowRelease(Contract contract, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_FUNDS, "Disbursement amount must be greater than 0");
        }

        Wallet employerWallet = walletRepository.findByUserIdForUpdate(contract.getEmployerId())
                .orElseThrow(() -> new BaseAppException(PaymentErrorCode.WALLET_NOT_FOUND, "Employer wallet not found"));

        if (employerWallet.getLockedBalance().compareTo(amount) < 0) {
            throw new BaseAppException(PaymentErrorCode.INSUFFICIENT_LOCKED_ESCROW,
                    String.format("Employer locked escrow balance [%s] insufficient for required release [%s]",
                            employerWallet.getLockedBalance(), amount));
        }

        Wallet freelancerWallet = walletRepository.findByUserIdForUpdate(contract.getEmployeeId())
                .orElseGet(() -> {
                    log.info("[Escrow Disbursement] Lazily creating wallet for employee: {}", contract.getEmployeeId());
                    return walletRepository.save(
                            Wallet.builder()
                                    .user(contract.getEmployee())
                                    .availableBalance(BigDecimal.ZERO)
                                    .lockedBalance(BigDecimal.ZERO)
                                    .build()
                    );
                });

        // Balance transfer
        employerWallet.setLockedBalance(employerWallet.getLockedBalance().subtract(amount));
        freelancerWallet.setAvailableBalance(freelancerWallet.getAvailableBalance().add(amount));

        walletRepository.save(employerWallet);
        walletRepository.save(freelancerWallet);

        // Update contract tracking
        contract.setLastPayoutAt(LocalDateTime.now());
        BigDecimal currentReleased = contract.getTotalReleased() != null ? contract.getTotalReleased() : BigDecimal.ZERO;
        contract.setTotalReleased(currentReleased.add(amount));
        contractRepository.save(contract);

        // Record immutable ledger entry
        Transaction releaseTx = Transaction.builder()
                .wallet(freelancerWallet)
                .contract(contract)
                .amount(amount)
                .transactionType(TransactionType.ESCROW_RELEASE)
                .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
        releaseTx = transactionRepository.save(releaseTx);

        log.info("[Escrow Disbursement] Successfully released [{}] from employer [{}] to freelancer [{}] for contract [{}]",
                amount, employerWallet.getUserId(), freelancerWallet.getUserId(), contract.getId());

        return DisbursementResponse.builder()
                .transactionId(releaseTx.getId())
                .contractId(contract.getId())
                .employerId(contract.getEmployerId())
                .freelancerId(contract.getEmployeeId())
                .salaryType(contract.getSalaryType())
                .amount(amount)
                .remainingLockedEscrow(employerWallet.getLockedBalance())
                .freelancerAvailableBalance(freelancerWallet.getAvailableBalance())
                .status(releaseTx.getStatus())
                .description(description)
                .disbursedAt(releaseTx.getCreatedAt())
                .build();
    }
}
