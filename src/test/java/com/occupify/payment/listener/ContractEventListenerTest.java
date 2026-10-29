package com.occupify.payment.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import com.occupify.payment.event.ContractAcceptedEvent;
import com.occupify.payment.event.ContractCompensationRequestedEvent;
import com.occupify.payment.event.ContractEscrowLockedEvent;
import com.occupify.payment.event.ContractRejectedEvent;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.repository.TransactionRepository;
import com.occupify.payment.repository.UserRepository;
import com.occupify.payment.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContractEventListenerTest {

  @Mock private ContractRepository contractRepository;
  @Mock private WalletRepository walletRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private UserRepository userRepository;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private com.occupify.payment.scheduler.ContractPayoutSchedulerManager payoutSchedulerManager;

  @InjectMocks private ContractEventListener contractEventListener;

  private final UUID employerId = UUID.randomUUID();
  private final UUID freelancerId = UUID.randomUUID();
  private final UUID contractId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        contractEventListener, "contractExchangeName", "occupify.contract.exchange");
  }

  @Test
  @DisplayName("Should lock escrow and set contract to IN_PROGRESS when employer has sufficient funds")
  void handleContractAccepted_SufficientFunds_LocksEscrowAndActivates() {
    User employer = User.builder().id(employerId).email("emp@occupify.local").fullName("Employer").build();
    User freelancer = User.builder().id(freelancerId).email("free@occupify.local").fullName("Freelancer").build();
    Contract contract = Contract.builder().id(contractId).jobId(jobId).employer(employer).employee(freelancer).salaryType(SalaryType.FIXED).status(ContractStatus.PENDING).build();
    Wallet wallet = Wallet.builder().id(UUID.randomUUID()).user(employer).availableBalance(new BigDecimal("1000.00")).lockedBalance(BigDecimal.ZERO).build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(wallet));

    ContractAcceptedEvent event =
        new ContractAcceptedEvent(
            UUID.randomUUID(), contractId, jobId, UUID.randomUUID(),
            employerId, freelancerId, "Test Title", new BigDecimal("400.00"),
            "FIXED", LocalDate.now(), LocalDate.now().plusDays(30), LocalDateTime.now());

    contractEventListener.handleContractAccepted(event);

    assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("600.00");
    assertThat(wallet.getLockedBalance()).isEqualByComparingTo("400.00");
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.IN_PROGRESS);

    verify(walletRepository).save(wallet);
    verify(transactionRepository).save(any(Transaction.class));
    verify(contractRepository).save(contract);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("occupify.contract.exchange"),
            eq("contract.escrow.locked"),
            any(ContractEscrowLockedEvent.class));
    verify(payoutSchedulerManager).armScheduler(contract);
  }

  @Test
  @DisplayName("Should emit ContractCompensationRequestedEvent when employer balance is insufficient")
  void handleContractAccepted_InsufficientFunds_TriggersCompensation() {
    User employer = User.builder().id(employerId).email("emp@occupify.local").fullName("Employer").build();
    Contract contract = Contract.builder().id(contractId).jobId(jobId).employer(employer).status(ContractStatus.PENDING).salaryType(SalaryType.FIXED).build();
    Wallet wallet = Wallet.builder().id(UUID.randomUUID()).user(employer).availableBalance(new BigDecimal("100.00")).lockedBalance(BigDecimal.ZERO).build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(wallet));

    ContractAcceptedEvent event =
        new ContractAcceptedEvent(
            UUID.randomUUID(), contractId, jobId, UUID.randomUUID(),
            employerId, freelancerId, "Test Title", new BigDecimal("500.00"),
            "FIXED", LocalDate.now(), LocalDate.now().plusDays(30), LocalDateTime.now());

    contractEventListener.handleContractAccepted(event);

    ArgumentCaptor<ContractCompensationRequestedEvent> captor =
        ArgumentCaptor.forClass(ContractCompensationRequestedEvent.class);
    verify(rabbitTemplate).convertAndSend(
        eq("occupify.contract.exchange"),
        eq("contract.compensation.requested"),
        captor.capture());

    ContractCompensationRequestedEvent compEvent = captor.getValue();
    assertThat(compEvent.contractId()).isEqualTo(contractId);
    assertThat(compEvent.failedService()).isEqualTo("occupify-payment");
    assertThat(compEvent.failureType()).isEqualTo("INSUFFICIENT_FUNDS");

    // Escrow must NOT be deducted
    assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("100.00");
    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("Should mark contract as CANCELLED when ContractRejectedEvent is received")
  void handleContractRejected_MarksCancelled() {
    Contract contract = Contract.builder().id(contractId).jobId(jobId).status(ContractStatus.PENDING).salaryType(SalaryType.FIXED).build();
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    ContractRejectedEvent event =
        new ContractRejectedEvent(
            UUID.randomUUID(), contractId, jobId, employerId, freelancerId, "Too busy", LocalDateTime.now());

    contractEventListener.handleContractRejected(event);

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
    assertThat(contract.getFinishedAt()).isNotNull();
    verify(contractRepository).save(contract);
    verify(payoutSchedulerManager).cancelScheduler(contractId);
  }

  @Test
  @DisplayName("Should release locked escrow back to available balance on compensation")
  void handleContractCompensation_ReleasesEscrowAndRevertsToPending() {
    User employer = User.builder().id(employerId).email("emp@occupify.local").fullName("Employer").build();
    Contract contract = Contract.builder().id(contractId).jobId(jobId).employer(employer).status(ContractStatus.IN_PROGRESS).salaryType(SalaryType.FIXED).build();
    Wallet wallet = Wallet.builder().id(UUID.randomUUID()).user(employer).availableBalance(new BigDecimal("100.00")).lockedBalance(new BigDecimal("500.00")).build();

    Transaction holdTx =
        Transaction.builder()
            .id(UUID.randomUUID())
            .wallet(wallet)
            .contract(contract)
            .amount(new BigDecimal("500.00"))
            .transactionType(TransactionType.ESCROW_HOLD)
            .status(TransactionStatus.SUCCESS)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(walletRepository.findByUserIdForUpdate(employerId)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findByContractId(contractId)).thenReturn(List.of(holdTx));

    ContractCompensationRequestedEvent event =
        new ContractCompensationRequestedEvent(
            UUID.randomUUID(), contractId, "occupify-admin", "DB_ERROR", "Administration service crashed", LocalDateTime.now());

    contractEventListener.handleContractCompensation(event);

    assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("600.00");
    assertThat(wallet.getLockedBalance()).isEqualByComparingTo("0.00");
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING);
    assertThat(contract.getStartedAt()).isNull();

    verify(walletRepository).save(wallet);
    verify(transactionRepository).save(any(Transaction.class));
    verify(contractRepository).save(contract);
    verify(payoutSchedulerManager).cancelScheduler(contractId);
  }
}
