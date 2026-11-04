package com.occupify.payment.listener;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.PaymentMethod;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import com.occupify.payment.event.ContractAcceptedEvent;
import com.occupify.payment.event.ContractCompensationRequestedEvent;
import com.occupify.payment.event.ContractCompletedEvent;
import com.occupify.payment.event.ContractEscrowLockedEvent;
import com.occupify.payment.event.ContractRejectedEvent;
import com.occupify.payment.repository.ContractRepository;
import com.occupify.payment.repository.TransactionRepository;
import com.occupify.payment.repository.UserRepository;
import com.occupify.payment.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "${app.rabbitmq.contract-queue:occupify.payment.contract.queue}")
public class ContractEventListener {

  private final ContractRepository contractRepository;
  private final WalletRepository walletRepository;
  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final RabbitTemplate rabbitTemplate;
  private final com.occupify.payment.scheduler.ContractPayoutSchedulerManager payoutSchedulerManager;

  @Value("${app.rabbitmq.contract-exchange:occupify.contract.exchange}")
  private String contractExchangeName;

  @Transactional
  @RabbitHandler
  public void handleContractAccepted(ContractAcceptedEvent event) {
    log.info(
        "[Payment Contract Listener] Handling ContractAcceptedEvent for contractId: {}, employerId: {}, budget: {}",
        event.contractId(),
        event.employerId(),
        event.budget());

    Contract contract = getOrCreateContract(event);
    BigDecimal budget = event.budget() != null ? event.budget() : BigDecimal.ZERO;

    if (budget.compareTo(BigDecimal.ZERO) <= 0) {
      log.warn(
          "[Payment Contract Listener] Contract [{}] has zero or negative budget [{}]. Skipping escrow hold.",
          event.contractId(),
          budget);
      contract.setStatus(ContractStatus.IN_PROGRESS);
      contract.setStartedAt(LocalDateTime.now());
      contract.setAgreedRate(budget);
      contract.setLastPayoutAt(LocalDateTime.now());
      if (event.salaryType() != null) {
        try {
          contract.setSalaryType(SalaryType.valueOf(event.salaryType().toUpperCase()));
        } catch (Exception ignored) {
        }
      }
      contractRepository.save(contract);
      payoutSchedulerManager.armScheduler(contract);

      ContractEscrowLockedEvent lockedEvent =
          new ContractEscrowLockedEvent(UUID.randomUUID(), event.contractId(), LocalDateTime.now());
      rabbitTemplate.convertAndSend(contractExchangeName, "contract.escrow.locked", lockedEvent);
      return;
    }

    Optional<Wallet> walletOpt = walletRepository.findByUserIdForUpdate(event.employerId());
    if (walletOpt.isEmpty() || walletOpt.get().getAvailableBalance().compareTo(budget) < 0) {
      BigDecimal available = walletOpt.map(Wallet::getAvailableBalance).orElse(BigDecimal.ZERO);
      log.warn(
          "[Payment Contract Listener] Insufficient funds for contract [{}]. Available: {}, Required: {}. Emitting compensation request.",
          event.contractId(),
          available,
          budget);

      ContractCompensationRequestedEvent compEvent =
          new ContractCompensationRequestedEvent(
              UUID.randomUUID(),
              event.contractId(),
              "occupify-payment",
              "INSUFFICIENT_FUNDS",
              String.format(
                  "Employer wallet balance [%s] insufficient for required escrow [%s]",
                  available, budget),
              LocalDateTime.now());

      rabbitTemplate.convertAndSend(
          contractExchangeName, "contract.compensation.requested", compEvent);
      return;
    }

    Wallet wallet = walletOpt.get();
    wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(budget));
    wallet.setLockedBalance(wallet.getLockedBalance().add(budget));
    walletRepository.save(wallet);

    Transaction holdTx =
        Transaction.builder()
            .wallet(wallet)
            .contract(contract)
            .amount(budget)
            .transactionType(TransactionType.ESCROW_HOLD)
            .paymentMethod(PaymentMethod.INTERNAL_WALLET)
            .status(TransactionStatus.SUCCESS)
            .build();
    transactionRepository.save(holdTx);

    contract.setStatus(ContractStatus.IN_PROGRESS);
    contract.setStartedAt(LocalDateTime.now());
    contract.setAgreedRate(budget);
    contract.setLastPayoutAt(LocalDateTime.now());
    if (event.salaryType() != null) {
      try {
        contract.setSalaryType(SalaryType.valueOf(event.salaryType().toUpperCase()));
      } catch (Exception ignored) {
      }
    }
    contractRepository.save(contract);
    payoutSchedulerManager.armScheduler(contract);

    log.info(
        "[Payment Contract Listener] Successfully locked escrow [{}] and activated contract [{}]",
        budget,
        event.contractId());

    ContractEscrowLockedEvent lockedEvent =
        new ContractEscrowLockedEvent(UUID.randomUUID(), event.contractId(), LocalDateTime.now());
    rabbitTemplate.convertAndSend(contractExchangeName, "contract.escrow.locked", lockedEvent);
  }

  @Transactional
  @RabbitHandler
  public void handleContractRejected(ContractRejectedEvent event) {
    log.info(
        "[Payment Contract Listener] Handling ContractRejectedEvent for contractId: {}, reason: {}",
        event.contractId(),
        event.reason());

    contractRepository
        .findById(event.contractId())
        .ifPresent(
            contract -> {
              contract.setStatus(ContractStatus.CANCELLED);
              contract.setFinishedAt(LocalDateTime.now());
              contractRepository.save(contract);
              payoutSchedulerManager.cancelScheduler(contract.getId());
              log.info("[Payment Contract Listener] Marked contract [{}] as CANCELLED", event.contractId());
            });
  }

  @Transactional
  @RabbitHandler
  public void handleContractCompensation(ContractCompensationRequestedEvent event) {
    log.warn(
        "[Payment Contract Listener] Handling compensation for contractId: {}, reason: {}",
        event.contractId(),
        event.reason());

    Optional<Contract> contractOpt = contractRepository.findById(event.contractId());
    if (contractOpt.isEmpty()) {
      return;
    }

    Contract contract = contractOpt.get();
    if (contract.getStatus() == ContractStatus.IN_PROGRESS && contract.getEmployer() != null) {
      Optional<Wallet> walletOpt =
          walletRepository.findByUserIdForUpdate(contract.getEmployer().getId());
      if (walletOpt.isPresent()) {
        Wallet wallet = walletOpt.get();
        // Look up any successful escrow hold transaction for this contract to refund
        transactionRepository.findByContractId(contract.getId()).stream()
            .filter(
                t ->
                    t.getTransactionType() == TransactionType.ESCROW_HOLD
                        && t.getStatus() == TransactionStatus.SUCCESS)
            .forEach(
                holdTx -> {
                  BigDecimal amount = holdTx.getAmount();
                  wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
                  wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
                  walletRepository.save(wallet);

                  Transaction refundTx =
                      Transaction.builder()
                          .wallet(wallet)
                          .contract(contract)
                          .amount(amount)
                          .transactionType(TransactionType.ESCROW_REFUND)
                          .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                          .status(TransactionStatus.SUCCESS)
                          .build();
                  transactionRepository.save(refundTx);
                  log.info(
                      "[Payment Contract Listener] Released locked escrow [{}] back to employer [{}]",
                      amount,
                      wallet.getUser().getId());
                });
      }
    }

    payoutSchedulerManager.cancelScheduler(event.contractId());
    contract.setStatus(ContractStatus.PENDING);
    contract.setStartedAt(null);
    contractRepository.save(contract);
    log.info("[Payment Contract Listener] Reverted contract [{}] status back to PENDING", event.contractId());
  }

  @Transactional
  @RabbitHandler
  public void handleContractCompleted(ContractCompletedEvent event) {
    if (event == null || event.contractId() == null) {
      log.warn("[Payment Contract Listener] Received invalid ContractCompletedEvent");
      return;
    }
    log.info("[Payment Contract Listener] Handling ContractCompletedEvent for contractId: {}", event.contractId());

    Optional<Contract> contractOpt = contractRepository.findById(event.contractId());
    if (contractOpt.isEmpty()) {
      log.warn("[Payment Contract Listener] Contract [{}] not found for completion", event.contractId());
      return;
    }

    Contract contract = contractOpt.get();
    payoutSchedulerManager.cancelScheduler(contract.getId());

    if (contract.getEmployer() != null && contract.getEmployee() != null) {
      BigDecimal totalHeld = transactionRepository.findByContractId(contract.getId()).stream()
          .filter(t -> t.getTransactionType() == TransactionType.ESCROW_HOLD && t.getStatus() == TransactionStatus.SUCCESS)
          .map(Transaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalReleased = transactionRepository.findByContractId(contract.getId()).stream()
          .filter(t -> t.getTransactionType() == TransactionType.ESCROW_RELEASE && t.getStatus() == TransactionStatus.SUCCESS)
          .map(Transaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalRefunded = transactionRepository.findByContractId(contract.getId()).stream()
          .filter(t -> t.getTransactionType() == TransactionType.ESCROW_REFUND && t.getStatus() == TransactionStatus.SUCCESS)
          .map(Transaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal remainingEscrow = totalHeld.subtract(totalReleased).subtract(totalRefunded);

      if (remainingEscrow.compareTo(BigDecimal.ZERO) > 0) {
        Optional<Wallet> employerWalletOpt = walletRepository.findByUserIdForUpdate(contract.getEmployer().getId());
        if (employerWalletOpt.isPresent()) {
          Wallet employerWallet = employerWalletOpt.get();
          BigDecimal releaseAmount = employerWallet.getLockedBalance().min(remainingEscrow);

          if (releaseAmount.compareTo(BigDecimal.ZERO) > 0) {
            Wallet employeeWallet = walletRepository.findByUserIdForUpdate(contract.getEmployee().getId())
                .orElseGet(() -> walletRepository.save(
                    Wallet.builder()
                        .user(contract.getEmployee())
                        .availableBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .build()));

            employerWallet.setLockedBalance(employerWallet.getLockedBalance().subtract(releaseAmount));
            employeeWallet.setAvailableBalance(employeeWallet.getAvailableBalance().add(releaseAmount));
            walletRepository.save(employerWallet);
            walletRepository.save(employeeWallet);

            BigDecimal currentReleased = contract.getTotalReleased() != null ? contract.getTotalReleased() : BigDecimal.ZERO;
            contract.setTotalReleased(currentReleased.add(releaseAmount));

            Transaction releaseTx = Transaction.builder()
                .wallet(employeeWallet)
                .contract(contract)
                .amount(releaseAmount)
                .transactionType(TransactionType.ESCROW_RELEASE)
                .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
            transactionRepository.save(releaseTx);

            log.info("[Payment Contract Listener] Released remaining locked escrow [{}] from employer [{}] to employee [{}] for contract [{}]",
                releaseAmount, employerWallet.getUser().getId(), employeeWallet.getUser().getId(), contract.getId());
          }
        }
      }
    }

    contract.setStatus(ContractStatus.COMPLETED);
    contract.setFinishedAt(event.finishedAt() != null ? event.finishedAt() : LocalDateTime.now());
    contractRepository.save(contract);
    log.info("[Payment Contract Listener] Marked contract [{}] as COMPLETED", event.contractId());
  }

  private Contract getOrCreateContract(ContractAcceptedEvent event) {
    return contractRepository
        .findById(event.contractId())
        .orElseGet(
            () -> {
              User employer =
                  userRepository
                      .findById(event.employerId())
                      .orElseGet(
                          () ->
                              userRepository.save(
                                  User.builder()
                                      .id(event.employerId())
                                      .email("employer-" + event.employerId() + "@occupify.local")
                                      .fullName("Employer " + event.employerId())
                                      .build()));

              User employee = null;
              if (event.freelancerId() != null) {
                employee =
                    userRepository
                        .findById(event.freelancerId())
                        .orElseGet(
                            () ->
                                userRepository.save(
                                    User.builder()
                                        .id(event.freelancerId())
                                        .email(
                                            "freelancer-"
                                                + event.freelancerId()
                                                + "@occupify.local")
                                        .fullName("Freelancer " + event.freelancerId())
                                        .build()));
              }

              SalaryType st = SalaryType.FIXED;
              if (event.salaryType() != null) {
                try {
                  st = SalaryType.valueOf(event.salaryType().toUpperCase());
                } catch (Exception ignored) {
                }
              }

              Contract newContract =
                  Contract.builder()
                      .id(event.contractId())
                      .jobId(event.jobId() != null ? event.jobId() : UUID.randomUUID())
                      .employer(employer)
                      .employee(employee)
                      .salaryType(st)
                      .agreedRate(event.budget())
                      .status(ContractStatus.PENDING)
                      .build();
              return contractRepository.save(newContract);
            });
  }
}
