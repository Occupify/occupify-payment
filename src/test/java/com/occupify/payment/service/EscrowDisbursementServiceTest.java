package com.occupify.payment.service;

import com.occupify.payment.dto.request.HourlyDisbursementRequest;
import com.occupify.payment.dto.response.DisbursementResponse;
import com.occupify.payment.entity.Contract;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import com.occupify.payment.exception.BaseAppException;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.repository.TransactionRepository;
import com.occupify.payment.repository.WalletRepository;
import com.occupify.payment.service.impl.EscrowDisbursementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscrowDisbursementServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private EscrowDisbursementServiceImpl escrowDisbursementService;

    private final UUID contractId = UUID.randomUUID();
    private final UUID employerId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private User employer;
    private User freelancer;
    private Wallet employerWallet;
    private Wallet freelancerWallet;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(escrowDisbursementService, "weeklyCycleDays", 7);
        ReflectionTestUtils.setField(escrowDisbursementService, "monthlyCycleDays", 30);

        employer = User.builder().id(employerId).email("emp@test.local").fullName("Employer").build();
        freelancer = User.builder().id(freelancerId).email("free@test.local").fullName("Freelancer").build();

        employerWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(employer)
                .availableBalance(new BigDecimal("1000.00"))
                .lockedBalance(new BigDecimal("5000.00"))
                .build();

        freelancerWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(freelancer)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Should successfully release WEEKLY payout from locked escrow to freelancer")
    void releaseWeeklyPayout_Success() {
        Contract contract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.WEEKLY)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("1200.00"))
                .build();

        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(employerWallet));
        when(walletRepository.findByUserIdForUpdate(freelancerId)).thenReturn(Optional.of(freelancerWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DisbursementResponse response = escrowDisbursementService.releaseWeeklyPayout(contractId);

        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(response.getSalaryType()).isEqualTo(SalaryType.WEEKLY);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        // Employer locked balance decreased from 5000 by 1200 -> 3800
        assertThat(employerWallet.getLockedBalance()).isEqualByComparingTo("3800.00");
        // Freelancer available balance increased from 0 by 1200 -> 1200
        assertThat(freelancerWallet.getAvailableBalance()).isEqualByComparingTo("1200.00");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.ESCROW_RELEASE);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Should fail WEEKLY payout if contract is not WEEKLY")
    void releaseWeeklyPayout_FailsIfWrongSalaryType() {
        Contract contract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.FIXED)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("1000.00"))
                .build();

        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> escrowDisbursementService.releaseWeeklyPayout(contractId))
                .isInstanceOf(BaseAppException.class)
                .hasMessageContaining("not WEEKLY");
    }

    @Test
    @DisplayName("Should successfully release MONTHLY payout from locked escrow")
    void releaseMonthlyPayout_Success() {
        Contract contract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.MONTHLY)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("3000.00"))
                .build();

        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(employerWallet));
        when(walletRepository.findByUserIdForUpdate(freelancerId)).thenReturn(Optional.of(freelancerWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DisbursementResponse response = escrowDisbursementService.releaseMonthlyPayout(contractId);

        assertThat(response.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(response.getSalaryType()).isEqualTo(SalaryType.MONTHLY);
        assertThat(employerWallet.getLockedBalance()).isEqualByComparingTo("2000.00");
        assertThat(freelancerWallet.getAvailableBalance()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Should successfully release HOURLY payout based on hoursWorked * agreedRate")
    void releaseHourlyPayout_CalculatedFromHours() {
        Contract contract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.HOURLY)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("50.00")) // 50/hour
                .build();

        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(employerWallet));
        when(walletRepository.findByUserIdForUpdate(freelancerId)).thenReturn(Optional.of(freelancerWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // 20 hours * 50 = 1000
        HourlyDisbursementRequest request = HourlyDisbursementRequest.builder()
                .hoursWorked(20.0)
                .note("Frontend task completion")
                .build();

        DisbursementResponse response = escrowDisbursementService.releaseHourlyPayout(contractId, request);

        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getDescription()).contains("Frontend task completion");
        assertThat(employerWallet.getLockedBalance()).isEqualByComparingTo("4000.00");
        assertThat(freelancerWallet.getAvailableBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Should fail release if employer locked escrow is insufficient")
    void releasePayout_FailsIfInsufficientEscrow() {
        employerWallet.setLockedBalance(new BigDecimal("500.00")); // Only 500 locked

        Contract contract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.WEEKLY)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("1200.00")) // Needs 1200
                .build();

        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(employerWallet));

        assertThatThrownBy(() -> escrowDisbursementService.releaseWeeklyPayout(contractId))
                .isInstanceOf(BaseAppException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    @DisplayName("Should scan and auto-disburse scheduled contracts whose cycle has passed")
    void processScheduledPayouts_PicksEligibleContracts() {
        Contract weeklyContract = Contract.builder()
                .id(contractId)
                .employer(employer)
                .employee(freelancer)
                .salaryType(SalaryType.WEEKLY)
                .status(ContractStatus.IN_PROGRESS)
                .agreedRate(new BigDecimal("500.00"))
                .lastPayoutAt(LocalDateTime.now().minusDays(8)) // 8 days ago > 7
                .build();

        when(contractRepository.findByStatus(ContractStatus.IN_PROGRESS)).thenReturn(List.of(weeklyContract));
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(weeklyContract));
        when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(employerWallet));
        when(walletRepository.findByUserIdForUpdate(freelancerId)).thenReturn(Optional.of(freelancerWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DisbursementResponse> responses = escrowDisbursementService.processScheduledPayouts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAmount()).isEqualByComparingTo("500.00");
    }
}
