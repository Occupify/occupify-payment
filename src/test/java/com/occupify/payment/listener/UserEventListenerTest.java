package com.occupify.payment.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.event.UserActivatedEvent;
import com.occupify.payment.repository.UserRepository;
import com.occupify.payment.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

  @Mock private UserRepository userRepository;
  @Mock private WalletRepository walletRepository;

  @InjectMocks private UserEventListener userEventListener;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should sync activated user and auto-provision wallet when no wallet exists")
  void shouldSyncActivatedUserAndProvisionWallet() {
    UserActivatedEvent event =
        new UserActivatedEvent(
            userId,
            "john.doe@occupify.work",
            "John Doe",
            "USER",
            "ACTIVE",
            Instant.now());

    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              return u;
            });
    when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

    userEventListener.handleUserActivated(event);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertEquals(userId, savedUser.getId());
    assertEquals("john.doe@occupify.work", savedUser.getEmail());
    assertEquals("John Doe", savedUser.getFullName());

    ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
    verify(walletRepository).save(walletCaptor.capture());
    Wallet savedWallet = walletCaptor.getValue();
    assertEquals(savedUser, savedWallet.getUser());
    assertEquals(BigDecimal.ZERO, savedWallet.getAvailableBalance());
    assertEquals(BigDecimal.ZERO, savedWallet.getLockedBalance());
  }

  @Test
  @DisplayName("Should update existing user and skip wallet creation if already exists")
  void shouldUpdateExistingUserAndSkipWalletCreation() {
    User existingUser =
        User.builder()
            .id(userId)
            .email("old@occupify.work")
            .fullName("Old Name")
            .build();

    Wallet existingWallet =
        Wallet.builder()
            .id(UUID.randomUUID())
            .user(existingUser)
            .availableBalance(BigDecimal.valueOf(100))
            .build();

    UserActivatedEvent event =
        new UserActivatedEvent(
            userId,
            "new.email@occupify.work",
            "New Name",
            "USER",
            "ACTIVE",
            Instant.now());

    when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(existingWallet));

    userEventListener.handleUserActivated(event);

    verify(userRepository).save(existingUser);
    assertEquals("new.email@occupify.work", existingUser.getEmail());
    assertEquals("New Name", existingUser.getFullName());
    verify(walletRepository, never()).save(any(Wallet.class));
  }

  @Test
  @DisplayName("Should ignore invalid event with missing userId or email")
  void shouldIgnoreInvalidEvent() {
    UserActivatedEvent event =
        new UserActivatedEvent(null, "", null, null, null, null);

    userEventListener.handleUserActivated(event);

    verify(userRepository, never()).findById(any());
    verify(userRepository, never()).save(any());
    verify(walletRepository, never()).save(any());
  }
}
