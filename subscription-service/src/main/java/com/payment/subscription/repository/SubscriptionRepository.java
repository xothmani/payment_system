package com.payment.subscription.repository;

import com.payment.subscription.model.Subscription;
import com.payment.subscription.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    List<Subscription> findByStatusAndTrialEndsAtLessThanEqual(SubscriptionStatus status, LocalDateTime date);
}
