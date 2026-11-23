package com.smartverse.churchlitebackend.model.subscription;

import com.smartverse.churchlitebackend.enums.SubscriptionFeature;
import com.smartverse.churchlitebackend.enums.SubscriptionResource;

import java.time.LocalDateTime;
import java.util.Map;

public record SubscriptionSnapshot(
        String tenant,
        String planCode,
        String planName,
        Double priceMonthly,
        String status,
        LocalDateTime currentPeriodEndsAt,
        LocalDateTime gracePeriodEndsAt,
        Map<SubscriptionResource, Limit> limits,
        Map<SubscriptionFeature, Boolean> features) {

    public record Limit(Long value, int warningPercentage) {
    }
}
