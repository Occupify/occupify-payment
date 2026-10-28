package com.occupify.payment.scheduler;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.service.EscrowDisbursementService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractPayoutSchedulerManager {

    private final TaskScheduler taskScheduler;
    private final ContractRepository contractRepository;
    private final EscrowDisbursementService escrowDisbursementService;

    @Value("${app.payout.scheduler.weekly-interval-seconds:604800}")
    private long weeklyIntervalSeconds;

    @Value("${app.payout.scheduler.monthly-interval-seconds:2592000}")
    private long monthlyIntervalSeconds;

    @Value("${app.payout.scheduler.hourly-interval-seconds:604800}")
    private long hourlyIntervalSeconds;

    private final Map<UUID, ScheduledFuture<?>> activeSchedules = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduleInfo> scheduleMetadata = new ConcurrentHashMap<>();

    public record ScheduleInfo(UUID contractId, SalaryType salaryType, Instant armedAt, Instant nextRunAt, long intervalSeconds) {}

    @PostConstruct
    public void init() {
        try {
            List<Contract> activeContracts = contractRepository.findByStatus(ContractStatus.IN_PROGRESS);
            log.info("[Payout Scheduler Manager] Initializing schedulers for {} active contracts on startup", activeContracts.size());
            for (Contract contract : activeContracts) {
                armScheduler(contract);
            }
        } catch (Exception ex) {
            log.warn("[Payout Scheduler Manager] Could not initialize schedulers on startup: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Payout Scheduler Manager] Shutting down, cancelling {} active schedulers", activeSchedules.size());
        activeSchedules.values().forEach(future -> future.cancel(false));
        activeSchedules.clear();
        scheduleMetadata.clear();
    }

    public synchronized boolean armScheduler(Contract contract) {
        if (contract == null || contract.getId() == null) {
            return false;
        }

        if (contract.getStatus() != ContractStatus.IN_PROGRESS) {
            log.info("[Payout Scheduler] Contract [{}] is not IN_PROGRESS (status: {}), skipping scheduler arming",
                    contract.getId(), contract.getStatus());
            return false;
        }

        UUID contractId = contract.getId();
        cancelScheduler(contractId);

        SalaryType salaryType = contract.getSalaryType();
        long intervalSeconds;

        if (salaryType == SalaryType.WEEKLY) {
            intervalSeconds = weeklyIntervalSeconds;
        } else if (salaryType == SalaryType.MONTHLY) {
            intervalSeconds = monthlyIntervalSeconds;
        } else if (salaryType == SalaryType.HOURLY) {
            intervalSeconds = hourlyIntervalSeconds;
        } else {
            log.info("[Payout Scheduler] Contract [{}] has salaryType [{}], no periodic scheduler needed", contractId, salaryType);
            return false;
        }

        Duration period = Duration.ofSeconds(intervalSeconds);
        Instant startTime = Instant.now().plus(period);

        Runnable payoutTask = () -> {
            try {
                log.info("[Payout Scheduler Trigger] Executing scheduled payout for contract [{}] ({})", contractId, salaryType);
                escrowDisbursementService.disburseScheduledPayoutForContract(contractId);
            } catch (Exception ex) {
                log.warn("[Payout Scheduler Trigger] Scheduled payout failed for contract [{}]: {}", contractId, ex.getMessage());
            }
        };

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(payoutTask, startTime, period);
        activeSchedules.put(contractId, future);
        scheduleMetadata.put(contractId, new ScheduleInfo(contractId, salaryType, Instant.now(), startTime, intervalSeconds));

        log.info("[Payout Scheduler] Initialized automated scheduler for contract [{}] immediately. SalaryType: {}, Interval: {}s, Next run: {}",
                contractId, salaryType, intervalSeconds, startTime);
        return true;
    }

    public synchronized boolean cancelScheduler(UUID contractId) {
        if (contractId == null) {
            return false;
        }

        ScheduledFuture<?> future = activeSchedules.remove(contractId);
        scheduleMetadata.remove(contractId);
        if (future != null) {
            future.cancel(false);
            log.info("[Payout Scheduler] Disarmed automated payout scheduler for contract [{}]", contractId);
            return true;
        }
        return false;
    }

    public boolean isSchedulerActive(UUID contractId) {
        return activeSchedules.containsKey(contractId);
    }

    public List<ScheduleInfo> getActiveSchedules() {
        return new ArrayList<>(scheduleMetadata.values());
    }
}
