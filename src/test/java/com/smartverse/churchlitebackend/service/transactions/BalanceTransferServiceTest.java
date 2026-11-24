package com.smartverse.churchlitebackend.service.transactions;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.entities.CashEntity;
import com.smartverse.churchlitebackend.entities.CashTransactionsEntity;
import com.smartverse.churchlitebackend.entities.TransactionsEntity;
import com.smartverse.churchlitebackend.enums.TransactionOperation;
import com.smartverse.churchlitebackend.enums.TypeCash;
import com.smartverse.churchlitebackend.repository.cash.CashCustomRepository;
import com.smartverse.churchlitebackend.repository.cashtransactions.CashTransactionsCustomRepository;
import com.smartverse.churchlitebackend.repository.transactions.TransactionsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceTransferServiceTest {

    @Mock
    private CashCustomRepository cashRepository;
    @Mock
    private CashTransactionsCustomRepository sessionRepository;
    @Mock
    private TransactionsCustomRepository transactionRepository;

    private BalanceTransferService service;

    @BeforeEach
    void setUp() {
        service = new BalanceTransferService(cashRepository, sessionRepository, transactionRepository);
    }

    @Test
    void createShouldSavePairedTransferTransactionsWhenBalanceIsSufficient() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        var source = bank(sourceId, "Conta Principal", 100.0);
        var destination = bank(destinationId, "Conta Reserva", 0.0);

        when(cashRepository.findAllForUpdate(List.of(sourceId, destinationId))).thenReturn(List.of(source, destination));
        when(transactionRepository.sumBankBalance(sourceId)).thenReturn(400.0);

        var result = service.create(sourceId, destinationId, 300.0, LocalDate.now(), "Transferência", "Obs");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionsEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());

        var saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(TransactionOperation.TRANSFER_OUT, saved.get(0).getTransactionOperation());
        assertEquals(TransactionOperation.TRANSFER_IN, saved.get(1).getTransactionOperation());
        assertEquals(saved.get(0).getTransferId(), saved.get(1).getTransferId());
        assertNotNull(result.get("transferId"));
    }

    @Test
    void createShouldThrowWhenSourceBalanceIsInsufficient() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        var source = bank(sourceId, "Conta Principal", 50.0);
        var destination = bank(destinationId, "Conta Reserva", 0.0);

        when(cashRepository.findAllForUpdate(List.of(sourceId, destinationId))).thenReturn(List.of(source, destination));
        when(transactionRepository.sumBankBalance(sourceId)).thenReturn(20.0);

        var exception = assertThrows(ServiceException.class, () ->
                service.create(sourceId, destinationId, 100.0, LocalDate.now(), "Transferência", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Saldo insuficiente na conta de origem", exception.getMessage());
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void createShouldRejectSameSourceAndDestination() {
        UUID id = UUID.randomUUID();

        var exception = assertThrows(ServiceException.class, () ->
                service.create(id, id, 50.0, LocalDate.now(), "Transferência", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Origem e destino devem ser diferentes", exception.getMessage());
    }

    @Test
    void createShouldRejectClosedCashAccount() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        var source = new CashEntity();
        source.setId(sourceId);
        source.setDescription("Caixa Central");
        source.setTypeCash(TypeCash.CASH);

        var destination = bank(destinationId, "Conta Banco", 0.0);

        when(cashRepository.findAllForUpdate(List.of(sourceId, destinationId))).thenReturn(List.of(source, destination));
        when(sessionRepository.findOpenForUpdate(sourceId)).thenReturn(Optional.empty());

        var exception = assertThrows(ServiceException.class, () ->
                service.create(sourceId, destinationId, 50.0, LocalDate.now(), "Sangria", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("O caixa Caixa Central precisa estar aberto", exception.getMessage());
    }

    @Test
    void reverseShouldDeletePairWhenDestinationHasSufficientBalance() {
        UUID transferId = UUID.randomUUID();
        var source = bank(UUID.randomUUID(), "Origem", 0.0);
        var destination = bank(UUID.randomUUID(), "Destino", 200.0);

        var out = new TransactionsEntity();
        out.setTransferId(transferId);
        out.setCash(source);
        out.setValue(150.0);
        out.setTransactionOperation(TransactionOperation.TRANSFER_OUT);

        var in = new TransactionsEntity();
        in.setTransferId(transferId);
        in.setCash(destination);
        in.setValue(150.0);
        in.setTransactionOperation(TransactionOperation.TRANSFER_IN);

        when(transactionRepository.findByTransferIdOrderByTransactionOperation(transferId)).thenReturn(List.of(out, in));
        when(cashRepository.findAllForUpdate(List.of(source.getId(), destination.getId())))
                .thenReturn(List.of(source, destination));
        when(transactionRepository.sumBankBalance(destination.getId())).thenReturn(0.0);

        service.reverse(transferId);

        verify(transactionRepository).deleteAll(List.of(out, in));
    }

    @Test
    void reverseShouldThrowWhenDestinationBalanceIsInsufficient() {
        UUID transferId = UUID.randomUUID();
        var source = bank(UUID.randomUUID(), "Origem", 0.0);
        var destination = bank(UUID.randomUUID(), "Destino", 0.0);

        var out = new TransactionsEntity();
        out.setTransferId(transferId);
        out.setCash(source);
        out.setValue(150.0);
        out.setTransactionOperation(TransactionOperation.TRANSFER_OUT);

        var in = new TransactionsEntity();
        in.setTransferId(transferId);
        in.setCash(destination);
        in.setValue(150.0);
        in.setTransactionOperation(TransactionOperation.TRANSFER_IN);

        when(transactionRepository.findByTransferIdOrderByTransactionOperation(transferId)).thenReturn(List.of(out, in));
        when(cashRepository.findAllForUpdate(List.of(source.getId(), destination.getId())))
                .thenReturn(List.of(source, destination));
        when(transactionRepository.sumBankBalance(destination.getId())).thenReturn(50.0);

        var exception = assertThrows(ServiceException.class, () -> service.reverse(transferId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("O destino não possui saldo suficiente para estornar a transferência", exception.getMessage());
        verify(transactionRepository, never()).deleteAll(anyList());
    }

    private CashEntity bank(UUID id, String description, double initialValue) {
        var cash = new CashEntity();
        cash.setId(id);
        cash.setDescription(description);
        cash.setTypeCash(TypeCash.BANK);
        cash.setInitialValue(initialValue);
        return cash;
    }
}
