package com.occupify.payment.repository;

import com.occupify.payment.config.JpaAuditingConfig;
import com.occupify.payment.entity.Contract;
import com.occupify.payment.entity.Transaction;
import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.enums.ContractStatus;
import com.occupify.payment.enums.PaymentMethod;
import com.occupify.payment.enums.SalaryType;
import com.occupify.payment.enums.TransactionStatus;
import com.occupify.payment.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PaymentSchemaPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Should persist and retrieve User (Local Replicated Entity from Identity)")
    void shouldPersistUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("freelancer@occupify.com")
                .fullName("John Doe")
                .avatarUrl("https://occupify.com/avatars/john.png")
                .build();

        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("freelancer@occupify.com");
        assertThat(found.get().getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should persist and retrieve Wallet referencing User with audit fields")
    void shouldPersistWallet() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("wallet.owner@occupify.com")
                .fullName("Alice Smith")
                .build());

        Wallet wallet = Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("1000.5000"))
                .lockedBalance(new BigDecimal("500.0000"))
                .build();

        Wallet saved = walletRepository.saveAndFlush(wallet);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getAvailableBalance()).isEqualByComparingTo("1000.5000");
        assertThat(saved.getLockedBalance()).isEqualByComparingTo("500.0000");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Wallet> foundByUserId = walletRepository.findByUserId(user.getId());
        assertThat(foundByUserId).isPresent();
        assertThat(foundByUserId.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("Should allow User to own multiple Wallets (1 to 1/N relationship)")
    void shouldAllowUserToOwnMultipleWallets() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("multi.wallet.owner@occupify.com")
                .fullName("Bob Miller")
                .build());

        Wallet personalWallet = Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("100.0000"))
                .build();

        Wallet businessWallet = Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("2000.0000"))
                .build();

        walletRepository.saveAndFlush(personalWallet);
        walletRepository.saveAndFlush(businessWallet);

        List<Wallet> allWallets = walletRepository.findAll();
        assertThat(allWallets)
                .filteredOn(w -> w.getUser().getId().equals(user.getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("Should persist and retrieve Contract (Local Replicated Entity from Core matching ERD)")
    void shouldPersistContract() {
        User employer = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("employer.contract@occupify.com")
                .fullName("Employer Company")
                .build());

        User employee = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("employee.contract@occupify.com")
                .fullName("Expert Freelancer")
                .build());

        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime finishedAt = startedAt.plusMonths(2);

        Contract contract = Contract.builder()
                .jobId(jobId)
                .employer(employer)
                .employee(employee)
                .sourceType("JOB_BID")
                .sourceId(sourceId)
                .salaryType(SalaryType.FIXED)
                .status(ContractStatus.IN_PROGRESS)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();

        Contract saved = contractRepository.saveAndFlush(contract);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getJobId()).isEqualTo(jobId);
        assertThat(saved.getEmployer().getId()).isEqualTo(employer.getId());
        assertThat(saved.getEmployee().getId()).isEqualTo(employee.getId());
        assertThat(saved.getEmployerId()).isEqualTo(employer.getId());
        assertThat(saved.getEmployeeId()).isEqualTo(employee.getId());
        assertThat(saved.getSourceType()).isEqualTo("JOB_BID");
        assertThat(saved.getSourceId()).isEqualTo(sourceId);
        assertThat(saved.getSalaryType()).isEqualTo(SalaryType.FIXED);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.IN_PROGRESS);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getFinishedAt()).isNotNull();

        Page<Contract> employerContracts = contractRepository.findByEmployerId(employer.getId(), PageRequest.of(0, 10));
        assertThat(employerContracts.getContent()).hasSize(1);

        Page<Contract> employeeContracts = contractRepository.findByEmployeeId(employee.getId(), PageRequest.of(0, 10));
        assertThat(employeeContracts.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should allow nullable employee on contract creation (open for bidding)")
    void shouldAllowNullableEmployeeOnContract() {
        User employer = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("open.employer@occupify.com")
                .fullName("Open Client")
                .build());

        Contract contract = Contract.builder()
                .jobId(UUID.randomUUID())
                .employer(employer)
                .employee(null)
                .salaryType(SalaryType.HOURLY)
                .status(ContractStatus.PENDING)
                .build();

        Contract saved = contractRepository.saveAndFlush(contract);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmployee()).isNull();
        assertThat(saved.getEmployeeId()).isNull();
    }

    @Test
    @DisplayName("Should persist Transaction referencing Wallet and Contract")
    void shouldPersistTransaction() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("employer@occupify.com")
                .fullName("Employer Corp")
                .build());

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("5000.0000"))
                .lockedBalance(BigDecimal.ZERO)
                .build());

        Contract contract = contractRepository.saveAndFlush(Contract.builder()
                .jobId(UUID.randomUUID())
                .employer(user)
                .salaryType(SalaryType.FIXED)
                .status(ContractStatus.PENDING)
                .build());

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .contract(contract)
                .amount(new BigDecimal("3000.0000"))
                .transactionType(TransactionType.ESCROW_HOLD)
                .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWallet().getId()).isEqualTo(wallet.getId());
        assertThat(saved.getContract().getId()).isEqualTo(contract.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo("3000.0000");
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.ESCROW_HOLD);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.INTERNAL_WALLET);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.getCreatedAt()).isNotNull();

        List<Transaction> byContract = transactionRepository.findByContractId(contract.getId());
        assertThat(byContract).hasSize(1);
    }

    @Test
    @DisplayName("Should allow multiple Transactions on the same Wallet and Contract (1 to N)")
    void shouldAllowMultipleTransactionsPerWalletAndContract() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("client.trans@occupify.com")
                .fullName("Charlie Brown")
                .build());

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("10000.0000"))
                .build());

        Contract contract = contractRepository.saveAndFlush(Contract.builder()
                .jobId(UUID.randomUUID())
                .employer(user)
                .salaryType(SalaryType.FIXED)
                .build());

        Transaction deposit = Transaction.builder()
                .wallet(wallet)
                .contract(contract)
                .amount(new BigDecimal("5000.0000"))
                .transactionType(TransactionType.DEPOSIT)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(TransactionStatus.SUCCESS)
                .build();

        Transaction escrowHold = Transaction.builder()
                .wallet(wallet)
                .contract(contract)
                .amount(new BigDecimal("5000.0000"))
                .transactionType(TransactionType.ESCROW_HOLD)
                .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();

        transactionRepository.saveAndFlush(deposit);
        transactionRepository.saveAndFlush(escrowHold);

        List<Transaction> transactions = transactionRepository.findByContractId(contract.getId());
        assertThat(transactions).hasSize(2);
    }

    @Test
    @DisplayName("Should fail when persisting Wallet with negative balance (Check Constraint Invariant)")
    void shouldFailWhenWalletBalanceIsNegative() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("invalid.wallet@occupify.com")
                .fullName("Invalid Wallet User")
                .build());

        Wallet wallet = Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("-10.0000"))
                .build();

        assertThatThrownBy(() -> walletRepository.saveAndFlush(wallet))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should fail when persisting Transaction with non-positive amount (Check Constraint Invariant)")
    void shouldFailWhenTransactionAmountIsNotPositive() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("trans.fail@occupify.com")
                .fullName("Trans User")
                .build());

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .availableBalance(new BigDecimal("100.0000"))
                .build());

        Contract contract = contractRepository.saveAndFlush(Contract.builder()
                .jobId(UUID.randomUUID())
                .employer(user)
                .salaryType(SalaryType.FIXED)
                .build());

        Transaction zeroAmountTx = Transaction.builder()
                .wallet(wallet)
                .contract(contract)
                .amount(BigDecimal.ZERO)
                .transactionType(TransactionType.PLATFORM_FEE)
                .paymentMethod(PaymentMethod.INTERNAL_WALLET)
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(zeroAmountTx))
                .isInstanceOf(Exception.class);
    }
}
