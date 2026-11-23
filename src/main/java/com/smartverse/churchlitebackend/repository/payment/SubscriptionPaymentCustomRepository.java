package com.smartverse.churchlitebackend.repository.payment;

import com.smartverse.churchlitebackend.entities.SubscriptionPaymentEntity;
import com.smartverse.churchlitebackend.enums.BillingCycle;
import com.smartverse.churchlitebackend.enums.PaymentStatus;
import com.smartverse.churchlitebackend.repositories.SubscriptionPaymentRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public interface SubscriptionPaymentCustomRepository extends SubscriptionPaymentRepository {
    Optional<SubscriptionPaymentEntity> findFirstByTenantAndPlanCodeAndBillingCycleAndStatusOrderByCreatedAtDesc(
            String tenant, String planCode, BillingCycle billingCycle, PaymentStatus status);

    List<SubscriptionPaymentEntity> findAllByTenantOrderByCreatedAtDesc(String tenant);

    List<SubscriptionPaymentEntity> findAllByTenantAndStatusOrderByCoverageEndAtDesc(
            String tenant, PaymentStatus status);
}
