package com.smartverse.churchlitebackend.service.transactions;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.entities.CashEntity;
import com.smartverse.churchlitebackend.entities.CashTransactionsEntity;
import com.smartverse.churchlitebackend.entities.FinancialEntity;
import com.smartverse.churchlitebackend.entities.PlanAccountEntity;
import com.smartverse.churchlitebackend.entities.TransactionsEntity;
import com.smartverse.churchlitebackend.enums.TransactionOperation;
import com.smartverse.churchlitebackend.enums.TypeCash;
import com.smartverse.churchlitebackend.enums.TypeFinancial;
import com.smartverse.churchlitebackend.repository.cashtransactions.CashTransactionsCustomRepository;
import com.smartverse.churchlitebackend.repository.transactions.TransactionsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsBusinessServiceTest {

    @Mock
    private TransactionsCustomRepository transactionsRepository;
    @Mock
    private CashTransactionsCustomRepository cashTransactionsCustomRepository;

    private TransactionsBusinessService service;

    @BeforeEach
    void setUp() {
        service = new TransactionsBusinessService();
        ReflectionTestUtils.setField(service, "transactionsRepository", transactionsRepository);
        ReflectionTestUtils.setField(service, "cashTransactionsCustomRepository", cashTransactionsCustomRepository);
    }

    @Test
    void saveShouldCreateRevenueTransactionWhenCashIsOpenAndPaid() {
        UUID cashId = UUID.randomUUID();
        var cash = new CashEntity();
        cash.setId(cashId);
        cash.setTypeCash(TypeCash.CASH);
        cash.setStatus(TransactionOperation.OPEN_CASH);

        var plan = new PlanAccountEntity();
        plan.setDescription("Dízimos");

        var financial = new FinancialEntity();
        financial.setCash(cash);
        financial.setPlanAccount(plan);
        financial.setPaymentReceiptDate(LocalDate.now());
        financial.setIssueDate(LocalDate.now());
        financial.setValue(250.0);
        financial.setTypeFinancial(TypeFinancial.REVENUE);

        var opening = new CashTransactionsEntity();
        when(cashTransactionsCustomRepository.getLastOpeningId(cashId)).thenReturn(opening);

        service.save(financial);

        var captor = ArgumentCaptor.forClass(TransactionsEntity.class);
        verify(transactionsRepository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(TransactionOperation.REVENUE, saved.getTransactionOperation());
        assertEquals("LANÇAMENTO: DÍZIMOS", saved.getDescription());
        assertEquals(250.0, saved.getValue());
        assertEquals(opening, saved.getCashTransaction());
    }

    @Test
    void saveShouldSkipWhenPaymentReceiptDateIsNull() {
        var financial = new FinancialEntity();
        financial.setPaymentReceiptDate(null);

        service.save(financial);

        verify(transactionsRepository, never()).save(any());
    }

    @Test
    void saveShouldThrowWhenCashIsClosed() {
        var cash = new CashEntity();
        cash.setId(UUID.randomUUID());
        cash.setTypeCash(TypeCash.CASH);
        cash.setStatus(TransactionOperation.CLOSE_CASH);

        var financial = new FinancialEntity();
        financial.setCash(cash);
        financial.setPaymentReceiptDate(LocalDate.now());

        var exception = assertThrows(ServiceException.class, () -> service.save(financial));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Caixa selecionado está fechado", exception.getMessage());
        verify(transactionsRepository, never()).save(any());
    }

    @Test
    void updateShouldDeleteExistingTransactionWhenPaymentReceiptDateBecomesNull() {
        var financial = new FinancialEntity();
        financial.setPaymentReceiptDate(null);

        var existing = new TransactionsEntity();
        when(transactionsRepository.findByFinancial(financial)).thenReturn(Optional.of(existing));

        service.update(financial);

        verify(transactionsRepository).delete(existing);
        verify(transactionsRepository, never()).save(any());
    }

    @Test
    void deleteByIdShouldRejectDirectDeletion() {
        var exception = assertThrows(ServiceException.class, () -> service.delete(UUID.randomUUID()));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.getStatus());
    }
}
