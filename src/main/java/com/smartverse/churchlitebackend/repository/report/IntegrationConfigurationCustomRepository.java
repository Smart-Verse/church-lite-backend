package com.smartverse.churchlitebackend.repository.report;

import com.smartverse.churchlitebackend.entities.IntegrationConfigurationEntity;
import com.smartverse.churchlitebackend.enums.IntegrationService;
import com.smartverse.churchlitebackend.repositories.IntegrationConfigurationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
public interface IntegrationConfigurationCustomRepository extends IntegrationConfigurationRepository {
    Optional<IntegrationConfigurationEntity> findByService(IntegrationService service);
}
