package com.smartverse.churchlitebackend.repository.payment;

import com.smartverse.churchlitebackend.entities.BillingDiscountEntity;
import com.smartverse.churchlitebackend.enums.BillingCycle;
import com.smartverse.churchlitebackend.repositories.BillingDiscountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
public interface BillingDiscountCustomRepository extends BillingDiscountRepository {
    Optional<BillingDiscountEntity> findByBillingCycleAndActiveTrue(BillingCycle billingCycle);
}
