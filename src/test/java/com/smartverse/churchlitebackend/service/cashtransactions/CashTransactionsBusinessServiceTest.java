package com.smartverse.churchlitebackend.service.cashtransactions;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.config.context.RequestUserContext;
import com.smartverse.churchlitebackend.converters.CashTransactionsDTOConverter;
import com.smartverse.churchlitebackend.dtos.CashTransactionsDTO;
import com.smartverse.churchlitebackend.entities.CashClosingApprovalEntity;
import com.smartverse.churchlitebackend.entities.CashEntity;
import com.smartverse.churchlitebackend.entities.CashTransactionsEntity;
import com.smartverse.churchlitebackend.entities.ChurchConfigurationEntity;
import com.smartverse.churchlitebackend.entities.ChurchResponsibleUserEntity;
import com.smartverse.churchlitebackend.entities.UserConfigurationEntity;
import com.smartverse.churchlitebackend.enums.CashApprovalPolicy;
import com.smartverse.churchlitebackend.enums.NotificationType;
import com.smartverse.churchlitebackend.enums.TransactionOperation;
import com.smartverse.churchlitebackend.repositories.CashClosingApprovalRepository;
import com.smartverse.churchlitebackend.repositories.CashRepository;
import com.smartverse.churchlitebackend.repositories.ChurchConfigurationRepository;
import com.smartverse.churchlitebackend.repositories.ChurchResponsibleUserRepository;
import com.smartverse.churchlitebackend.repositories.UserConfigurationRepository;
import com.smartverse.churchlitebackend.repository.cashtransactions.CashTransactionsCustomRepository;
import com.smartverse.churchlitebackend.service.notification.NotificationBusinessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashTransactionsBusinessServiceTest {

    @Mock
    private CashTransactionsCustomRepository cashTransactionsCustomRepository;
    @Mock
    private CashRepository cashRepository;
    @Mock
    private ChurchConfigurationRepository churchConfigurationRepository;
    @Mock
    private ChurchResponsibleUserRepository responsibleUserRepository;
    @Mock
    private CashClosingApprovalRepository approvalRepository;
    @Mock
    private UserConfigurationRepository userRepository;
    @Mock
    private NotificationBusinessService notificationService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private CashTransactionsDTOConverter dtoConverter;

    private CashTransactionsBusinessService service;

    @BeforeEach
    void setUp() {
        service = new CashTransactionsBusinessService(
                cashTransactionsCustomRepository,
                cashRepository,
                churchConfigurationRepository,
                responsibleUserRepository,
                approvalRepository,
                userRepository,
                notificationService,
                entityManager
        );
        ReflectionTestUtils.setField(service, "dtoConverter", dtoConverter);
    }

    @AfterEach
    void tearDown() {
        RequestUserContext.clear();
    }

    @Test
    void saveShouldOpenCashWhenNoOpeningExists() {
        UUID cashId = UUID.randomUUID();
        var cash = new CashEntity();
        cash.setId(cashId);

        var input = new CashTransactionsDTO();
        var entity = new CashTransactionsEntity();
        entity.setCash(cash);
        var expected = new CashTransactionsDTO();

        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(cashTransactionsCustomRepository.existsByOpening(cashId)).thenReturn(false);
        when(dtoConverter.toDTO(entity, null)).thenReturn(expected);

        var result = service.save(input);

        assertSame(expected, result);
        assertEquals(TransactionOperation.OPEN_CASH, cash.getStatus());
        verify(entityManager).persist(entity);
        verify(cashRepository).save(cash);
    }

    @Test
    void saveShouldCloseCashDirectlyWhenApprovalPolicyIsDisabled() {
        UUID cashId = UUID.randomUUID();
        var cash = new CashEntity();
        cash.setId(cashId);

        var input = new CashTransactionsDTO();
        var entity = new CashTransactionsEntity();
        entity.setCash(cash);
        entity.setEndDate(LocalDate.now());
        var expected = new CashTransactionsDTO();

        var config = new ChurchConfigurationEntity();
        config.setCashApprovalPolicy(CashApprovalPolicy.DISABLED);

        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(cashTransactionsCustomRepository.existsByOpening(cashId)).thenReturn(true);
        when(churchConfigurationRepository.findAll()).thenReturn(List.of(config));
        when(dtoConverter.toDTO(entity, null)).thenReturn(expected);

        var result = service.save(input);

        assertSame(expected, result);
        assertEquals(TransactionOperation.CLOSE_CASH, cash.getStatus());
        verify(entityManager).merge(entity);
        verify(cashRepository).save(cash);
    }

    @Test
    void saveShouldSetPendingApprovalAndNotifyApproversWhenPolicyIsEnabled() {
        UUID cashId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID userHash = UUID.randomUUID();
        RequestUserContext.set(userHash);

        var cash = new CashEntity();
        cash.setId(cashId);
        cash.setDescription("Caixa Central");

        var input = new CashTransactionsDTO();
        var entity = new CashTransactionsEntity();
        entity.setId(txId);
        entity.setCash(cash);
        entity.setEndDate(LocalDate.now());

        var pending = new CashTransactionsEntity();
        pending.setId(txId);
        pending.setCash(cash);

        var config = new ChurchConfigurationEntity();
        config.setCashApprovalPolicy(CashApprovalPolicy.ANY);

        var approverUser = new UserConfigurationEntity();
        approverUser.setId(UUID.randomUUID());

        var responsible = new ChurchResponsibleUserEntity();
        responsible.setFinancialApprover(true);
        responsible.setUserConfiguration(approverUser);

        var requester = new UserConfigurationEntity();
        requester.setId(UUID.randomUUID());
        requester.setHash(userHash);

        var expected = new CashTransactionsDTO();

        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(cashTransactionsCustomRepository.existsByOpening(cashId)).thenReturn(true);
        when(churchConfigurationRepository.findAll()).thenReturn(List.of(config));
        when(responsibleUserRepository.findAll()).thenReturn(List.of(responsible));
        when(entityManager.merge(entity)).thenReturn(pending);
        when(approvalRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(requester));
        when(dtoConverter.toDTO(pending, null)).thenReturn(expected);

        var result = service.save(input);

        assertSame(expected, result);
        assertNull(entity.getEndDate());
        assertEquals(TransactionOperation.PENDING_APPROVAL, cash.getStatus());
        verify(approvalRepository).save(any(CashClosingApprovalEntity.class));
        verify(notificationService).create(
                eq(approverUser),
                eq(NotificationType.CASH_PENDING_APPROVAL),
                any(),
                any(),
                eq("CASH_TRANSACTION"),
                eq(txId),
                eq("/home/cash-approvals"),
                any()
        );
    }

    @Test
    void saveShouldThrowWhenAlreadyOpenAndNoEndDate() {
        UUID cashId = UUID.randomUUID();
        var cash = new CashEntity();
        cash.setId(cashId);

        var input = new CashTransactionsDTO();
        var entity = new CashTransactionsEntity();
        entity.setCash(cash);

        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(cashTransactionsCustomRepository.existsByOpening(cashId)).thenReturn(true);

        var exception = assertThrows(ServiceException.class, () -> service.save(input));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Já existe um caixa aberto", exception.getMessage());
    }
}
