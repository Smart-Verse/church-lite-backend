package com.smartverse.churchlitebackend.repository.userconfiguration;

import com.smartverse.churchlitebackend.entities.UserConfigurationEntity;
import com.smartverse.churchlitebackend.repositories.UserConfigurationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public interface UserConfigurationCustomRepository extends UserConfigurationRepository {

    Optional<UserConfigurationEntity> findByHash(UUID hash);
}
