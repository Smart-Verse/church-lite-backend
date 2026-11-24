package com.smartverse.churchlitebackend.service.cash;

import com.smartverse.churchlitebackend.converters.CashDTOConverter;
import com.smartverse.churchlitebackend.dtos.CashDTO;
import com.smartverse.churchlitebackend.entities.CashEntity;
import com.smartverse.churchlitebackend.enums.SubscriptionResource;
import com.smartverse.churchlitebackend.enums.TypeCash;
import com.smartverse.churchlitebackend.repositories.CashRepository;
import com.smartverse.churchlitebackend.service.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashBusinessServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private CashRepository repository;
    @Mock
    private CashDTOConverter dtoConverter;

    private CashBusinessService service;

    @BeforeEach
    void setUp() {
        service = new CashBusinessService(subscriptionService);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "dtoConverter", dtoConverter);
    }

    @Test
    void saveShouldCheckBankResourceAndPersist() {
        var dto = new CashDTO();
        dto.typeCash = TypeCash.BANK;
        var entity = new CashEntity();
        var saved = new CashEntity();
        var expected = new CashDTO();

        when(dtoConverter.toEntity(dto, null)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(dtoConverter.toDTO(saved, null)).thenReturn(expected);

        var result = service.save(dto);

        assertSame(expected, result);
        verify(subscriptionService).requireAvailable(SubscriptionResource.BANK_ACCOUNT);
        verify(repository).save(entity);
    }

    @Test
    void updateShouldSkipResourceCheckWhenTypeDidNotChange() {
        UUID id = UUID.randomUUID();
        var dto = new CashDTO();
        dto.typeCash = TypeCash.CASH;

        var current = new CashEntity();
        current.setId(id);
        current.setTypeCash(TypeCash.CASH);

        var entity = new CashEntity();
        var saved = new CashEntity();
        var expected = new CashDTO();

        when(repository.findById(id)).thenReturn(Optional.of(current));
        when(dtoConverter.toEntity(dto, null)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(dtoConverter.toDTO(saved, null)).thenReturn(expected);

        var result = service.update(dto, id);

        assertSame(expected, result);
        verify(subscriptionService, never()).requireAvailable(SubscriptionResource.CASH_ACCOUNT);
        verify(subscriptionService, never()).requireAvailable(SubscriptionResource.BANK_ACCOUNT);
    }
}
