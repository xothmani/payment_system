package com.payment.subscription.repository;

import com.payment.subscription.model.TokenLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TokenLedgerRepository extends JpaRepository<TokenLedger, UUID> {

    List<TokenLedger> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
