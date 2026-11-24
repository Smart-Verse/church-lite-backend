package com.smartverse.churchlitebackend.service.cashclosingapproval;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.config.context.RequestUserContext;
import com.smartverse.churchlitebackend.converters.CashClosingApprovalDTOConverter;
import com.smartverse.churchlitebackend.dtos.CashClosingApprovalDTO;
import com.smartverse.churchlitebackend.dtos.CashTransactionsDTO;
import com.smartverse.churchlitebackend.entities.CashClosingApprovalEntity;
import com.smartverse.churchlitebackend.entities.CashEntity;
import com.smartverse.churchlitebackend.entities.CashTransactionsEntity;
import com.smartverse.churchlitebackend.entities.ChurchConfigurationEntity;
import com.smartverse.churchlitebackend.entities.UserConfigurationEntity;
import com.smartverse.churchlitebackend.enums.CashApprovalPolicy;
import com.smartverse.churchlitebackend.enums.NotificationType;
import com.smartverse.churchlitebackend.enums.TransactionOperation;
import com.smartverse.churchlitebackend.repositories.CashClosingApprovalRepository;
import com.smartverse.churchlitebackend.repositories.CashRepository;
import com.smartverse.churchlitebackend.repositories.CashTransactionsRepository;
import com.smartverse.churchlitebackend.repositories.ChurchConfigurationRepository;
import com.smartverse.churchlitebackend.repositories.UserConfigurationRepository;
import com.smartverse.churchlitebackend.service.notification.NotificationBusinessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashClosingApprovalBusinessServiceTest {

    @Mock
    private UserConfigurationRepository userRepository;
    @Mock
    private ChurchConfigurationRepository configurationRepository;
    @Mock
    private CashTransactionsRepository cashTransactionsRepository;
    @Mock
    private CashRepository cashRepository;
    @Mock
    private NotificationBusinessService notificationService;
    @Mock
    private CashClosingApprovalRepository repository;
    @Mock
    private CashClosingApprovalDTOConverter dtoConverter;

    private CashClosingApprovalBusinessService service;

    @BeforeEach
    void setUp() {
        service = new CashClosingApprovalBusinessService(
                userRepository,
                configurationRepository,
                cashTransactionsRepository,
                cashRepository,
                notificationService
        );
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "dtoConverter", dtoConverter);
    }

    @AfterEach
    void tearDown() {
        RequestUserContext.clear();
    }

    @Test
    void saveShouldCloseCashAndNotifyRequesterWhenPolicyIsAny() {
        UUID txId = UUID.randomUUID();
        UUID userHash = UUID.randomUUID();
        RequestUserContext.set(userHash);

        var approver = new UserConfigurationEntity();
        approver.setId(UUID.randomUUID());
        approver.setHash(userHash);

        var requester = new UserConfigurationEntity();
        requester.setId(UUID.randomUUID());

        var cash = new CashEntity();
        cash.setId(UUID.randomUUID());
        cash.setDescription("Caixa 01");
        cash.setStatus(TransactionOperation.PENDING_APPROVAL);

        var tx = new CashTransactionsEntity();
        tx.setId(txId);
        tx.setCash(cash);

        var approval = new CashClosingApprovalEntity();
        approval.setCashTransaction(tx);
        approval.setApprover(approver);
        approval.setRequestedBy(requester);
        approval.setApproved(false);

        var config = new ChurchConfigurationEntity();
        config.setCashApprovalPolicy(CashApprovalPolicy.ANY);

        var txDto = new CashTransactionsDTO();
        txDto.setId(txId);
        var input = new CashClosingApprovalDTO();
        input.setCashTransaction(txDto);
        var expected = new CashClosingApprovalDTO();

        when(userRepository.findAll()).thenReturn(List.of(approver));
        when(repository.findAll()).thenReturn(List.of(approval));
        when(configurationRepository.findAll()).thenReturn(List.of(config));
        when(cashTransactionsRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(dtoConverter.toDTO(approval, null)).thenReturn(expected);

        var result = service.save(input);

        assertSame(expected, result);
        assertTrue(approval.isApproved());
        assertNotNull(approval.getApprovedAt());
        assertNotNull(tx.getEndDate());
        assertEquals(TransactionOperation.CLOSE_CASH, cash.getStatus());
        verify(cashTransactionsRepository).save(tx);
        verify(cashRepository).save(cash);
        verify(notificationService).create(
                eq(requester),
                eq(NotificationType.CASH_APPROVED),
                any(),
                any(),
                eq("CASH_TRANSACTION"),
                eq(txId),
                eq("/home/cash-history"),
                eq("cash:" + txId + ":approved")
        );
    }

    @Test
    void saveShouldKeepCashPendingWhenPolicyIsAllAndAnotherApproverRemains() {
        UUID txId = UUID.randomUUID();
        UUID userHash = UUID.randomUUID();
        RequestUserContext.set(userHash);

        var approver1 = new UserConfigurationEntity();
        approver1.setId(UUID.randomUUID());
        approver1.setHash(userHash);

        var approver2 = new UserConfigurationEntity();
        approver2.setId(UUID.randomUUID());

        var tx = new CashTransactionsEntity();
        tx.setId(txId);

        var approval1 = new CashClosingApprovalEntity();
        approval1.setCashTransaction(tx);
        approval1.setApprover(approver1);
        approval1.setApproved(false);

        var approval2 = new CashClosingApprovalEntity();
        approval2.setCashTransaction(tx);
        approval2.setApprover(approver2);
        approval2.setApproved(false);

        var config = new ChurchConfigurationEntity();
        config.setCashApprovalPolicy(CashApprovalPolicy.ALL);

        var txDto = new CashTransactionsDTO();
        txDto.setId(txId);
        var input = new CashClosingApprovalDTO();
        input.setCashTransaction(txDto);
        var expected = new CashClosingApprovalDTO();

        when(userRepository.findAll()).thenReturn(List.of(approver1));
        when(repository.findAll()).thenReturn(List.of(approval1, approval2));
        when(configurationRepository.findAll()).thenReturn(List.of(config));
        when(dtoConverter.toDTO(approval1, null)).thenReturn(expected);

        var result = service.save(input);

        assertSame(expected, result);
        assertTrue(approval1.isApproved());
        verify(cashTransactionsRepository, never()).save(any());
        verify(cashRepository, never()).save(any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveShouldThrowForbiddenWhenUserIsNotAnApprover() {
        UUID txId = UUID.randomUUID();
        RequestUserContext.set(UUID.randomUUID());

        var txDto = new CashTransactionsDTO();
        txDto.setId(txId);
        var input = new CashClosingApprovalDTO();
        input.setCashTransaction(txDto);

        when(userRepository.findAll()).thenReturn(List.of());

        var exception = assertThrows(ServiceException.class, () -> service.save(input));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("cash_approval_not_allowed", exception.getMessage());
    }
}
