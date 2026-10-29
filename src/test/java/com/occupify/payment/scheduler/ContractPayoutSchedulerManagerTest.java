package com.occupify.payment.scheduler;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.service.EscrowDisbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractPayoutSchedulerManagerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private EscrowDisbursementService escrowDisbursementService;

    @InjectMocks
    private ContractPayoutSchedulerManager schedulerManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(schedulerManager, "weeklyIntervalSeconds", 604800L);
        ReflectionTestUtils.setField(schedulerManager, "monthlyIntervalSeconds", 2592000L);
        ReflectionTestUtils.setField(schedulerManager, "hourlyIntervalSeconds", 604800L);
    }

    @Test
    @DisplayName("Should NOT arm scheduler when contract is still PENDING (not yet accepted)")
    void armScheduler_PendingStatus_DoesNotArm() {
        Contract pendingContract = Contract.builder()
                .id(UUID.randomUUID())
                .salaryType(SalaryType.WEEKLY)
                .status(ContractStatus.PENDING)
                .build();

        boolean armed = schedulerManager.armScheduler(pendingContract);

        assertThat(armed).isFalse();
        verifyNoInteractions(taskScheduler);
        assertThat(schedulerManager.getActiveSchedules()).isEmpty();
    }

    @Test
    @DisplayName("Should arm scheduler when contract is accepted and IN_PROGRESS")
    void armScheduler_InProgressStatus_ArmsSuccessfully() {
        UUID contractId = UUID.randomUUID();
        Contract activeContract = Contract.builder()
                .id(contractId)
                .salaryType(SalaryType.WEEKLY)
                .status(ContractStatus.IN_PROGRESS)
                .build();

        ScheduledFuture mockFuture = mock(ScheduledFuture.class);
        doReturn(mockFuture).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        boolean armed = schedulerManager.armScheduler(activeContract);

        assertThat(armed).isTrue();
        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(604800)));
        assertThat(schedulerManager.getActiveSchedules()).hasSize(1);
        assertThat(schedulerManager.getActiveSchedules().get(0).contractId()).isEqualTo(contractId);
    }

    @Test
    @DisplayName("Should cancel and remove active schedule")
    void cancelScheduler_RemovesScheduledTask() {
        UUID contractId = UUID.randomUUID();
        Contract activeContract = Contract.builder()
                .id(contractId)
                .salaryType(SalaryType.MONTHLY)
                .status(ContractStatus.IN_PROGRESS)
                .build();

        ScheduledFuture mockFuture = mock(ScheduledFuture.class);
        doReturn(mockFuture).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        schedulerManager.armScheduler(activeContract);
        assertThat(schedulerManager.getActiveSchedules()).hasSize(1);

        boolean cancelled = schedulerManager.cancelScheduler(contractId);

        assertThat(cancelled).isTrue();
        verify(mockFuture).cancel(false);
        assertThat(schedulerManager.getActiveSchedules()).isEmpty();
    }
}
