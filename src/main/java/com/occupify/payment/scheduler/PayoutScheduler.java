package com.occupify.payment.scheduler;

import com.occupify.payment.service.EscrowDisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.payout.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PayoutScheduler {

    private final EscrowDisbursementService escrowDisbursementService;

    @Scheduled(fixedDelayString = "${app.payout.scheduler.fixed-delay-ms:60000}")
    public void runPayoutSchedule() {
        try {
            escrowDisbursementService.processScheduledPayouts();
        } catch (Exception ex) {
            log.error("[Payout Scheduler] Error during automated payout run: {}", ex.getMessage(), ex);
        }
    }
}
