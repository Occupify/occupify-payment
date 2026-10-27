package com.occupify.payment.listener;

import com.occupify.payment.entity.User;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.event.UserActivatedEvent;
import com.occupify.payment.repository.UserRepository;
import com.occupify.payment.repository.WalletRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

  private final UserRepository userRepository;
  private final WalletRepository walletRepository;

  @RabbitListener(queues = "${app.rabbitmq.user-queue:occupify.payment.user.queue}")
  @Transactional
  public void handleUserActivated(UserActivatedEvent event) {
    if (event == null || event.userId() == null || !StringUtils.hasText(event.email())) {
      log.warn("[Payment Event Listener] Received invalid UserActivatedEvent: {}", event);
      return;
    }

    String email = event.email().trim().toLowerCase();
    log.info(
        "[Payment Event Listener] Processing UserActivatedEvent for user [{}] ({})",
        event.userId(),
        email);

    User user =
        userRepository
            .findById(event.userId())
            .orElseGet(() -> User.builder().id(event.userId()).build());

    user.setEmail(email);
    String fullName =
        StringUtils.hasText(event.fullName())
            ? event.fullName().trim()
            : "User " + event.userId().toString().substring(0, 8);
    user.setFullName(fullName);

    User savedUser = userRepository.save(user);
    log.info(
        "[Payment Event Listener] Synchronized activated user [{}] ({}) to payment database",
        savedUser.getId(),
        savedUser.getEmail());

    if (walletRepository.findByUserId(savedUser.getId()).isEmpty()) {
      Wallet wallet =
          Wallet.builder()
              .user(savedUser)
              .availableBalance(BigDecimal.ZERO)
              .lockedBalance(BigDecimal.ZERO)
              .build();
      walletRepository.save(wallet);
      log.info(
          "[Payment Event Listener] Auto-provisioned wallet for activated user [{}]",
          savedUser.getId());
    }
  }
}
