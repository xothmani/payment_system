package com.payment.subscription.service;

import com.payment.subscription.model.TokenLedger;
import com.payment.subscription.model.TokenLedgerType;
import com.payment.subscription.repository.TokenLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenLedgerService {

    private final TokenLedgerRepository tokenLedgerRepository;

    @Async("tokenLedgerExecutor")
    public void logDebitAsync(UUID userId, long amount, long balanceAfter, String description) {
        try {
            tokenLedgerRepository.save(TokenLedger.builder()
                    .userId(userId)
                    .type(TokenLedgerType.DEBIT)
                    .amount(amount)
                    .balanceAfter(balanceAfter)
                    .description(description)
                    .build());
        } catch (Exception e) {
            log.error("Failed to write token debit ledger entry for userId={}: {}", userId, e.getMessage(), e);
        }
    }
}
