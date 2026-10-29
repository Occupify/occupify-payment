package com.occupify.payment.controller;

import com.occupify.payment.dto.response.WalletResponse;
import com.occupify.payment.dto.response.base.SingleResponse;
import com.occupify.payment.entity.Wallet;
import com.occupify.payment.exception.BaseAppException;
import com.occupify.payment.exception.error.PaymentErrorCode;
import com.occupify.payment.repository.WalletRepository;
import com.occupify.payment.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController extends AbstractBaseController {

    private final WalletRepository walletRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<SingleResponse<WalletResponse>> getWalletByUserId(@PathVariable UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BaseAppException(PaymentErrorCode.WALLET_NOT_FOUND, "Ví của người dùng không tồn tại"));

        return success(toWalletResponse(wallet), "Wallet retrieved successfully");
    }

    @GetMapping("/me")
    public ResponseEntity<SingleResponse<WalletResponse>> getMyWallet() {
        UUID currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BaseAppException(PaymentErrorCode.UNAUTHORIZED_WALLET_ACCESS, "User context required");
        }
        return getWalletByUserId(currentUserId);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .userEmail(wallet.getUser() != null ? wallet.getUser().getEmail() : null)
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
