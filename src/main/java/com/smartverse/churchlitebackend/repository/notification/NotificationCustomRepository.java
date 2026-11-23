package com.smartverse.churchlitebackend.repository.notification;

import com.smartverse.churchlitebackend.entities.NotificationEntity;
import com.smartverse.churchlitebackend.repositories.NotificationRepository;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;

@Primary
public interface NotificationCustomRepository extends NotificationRepository {
    List<NotificationEntity> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<NotificationEntity> findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId);
    boolean existsByDeduplicationKey(String deduplicationKey);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
