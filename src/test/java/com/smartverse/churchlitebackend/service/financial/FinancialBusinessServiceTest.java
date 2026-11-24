package com.smartverse.churchlitebackend.service.financial;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.converters.FinancialDTOConverter;
import com.smartverse.churchlitebackend.dtos.FinancialDTO;
import com.smartverse.churchlitebackend.dtos.PlanAccountDTO;
import com.smartverse.churchlitebackend.entities.FinancialEntity;
import com.smartverse.churchlitebackend.entities.PlanAccountEntity;
import com.smartverse.churchlitebackend.enums.PlanAccountFinancialNature;
import com.smartverse.churchlitebackend.enums.TypeFinancial;
import com.smartverse.churchlitebackend.repositories.FinancialRepository;
import com.smartverse.churchlitebackend.service.transactions.TransactionsBusinessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialBusinessServiceTest {

    @Mock
    private TransactionsBusinessService transactionsService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private FinancialRepository repository;
    @Mock
    private FinancialDTOConverter dtoConverter;

    private FinancialBusinessService service;

    @BeforeEach
    void setUp() {
        service = new FinancialBusinessService(transactionsService, entityManager);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "dtoConverter", dtoConverter);
    }

    // Garante que o lançamento financeiro é salvo e gera a transação quando a natureza do plano de contas é compatível.
    @Test
    void saveShouldPersistAndCreateTransactionWhenPlanAccountMatchesNature() {
        UUID planId = UUID.randomUUID();
        var planDto = new PlanAccountDTO();
        planDto.setId(planId);

        var input = new FinancialDTO();
        input.setTypeFinancial(TypeFinancial.REVENUE);
        input.setPlanAccount(planDto);

        var planAccount = new PlanAccountEntity();
        planAccount.setId(planId);
        planAccount.setFinancialNature(PlanAccountFinancialNature.REVENUE);

        var entity = new FinancialEntity();
        var expectedDto = new FinancialDTO();

        when(entityManager.find(PlanAccountEntity.class, planId)).thenReturn(planAccount);
        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(dtoConverter.toDTO(entity, null)).thenReturn(expectedDto);

        var result = service.save(input);

        assertSame(expectedDto, result);
        verify(entityManager).persist(entity);
        verify(transactionsService).save(entity);
    }

    // Garante que lança erro e bloqueia a persistência quando a natureza do plano de contas diverge do tipo financeiro.
    @Test
    void saveShouldThrowWhenPlanAccountNatureMismatches() {
        UUID planId = UUID.randomUUID();
        var planDto = new PlanAccountDTO();
        planDto.setId(planId);

        var input = new FinancialDTO();
        input.setTypeFinancial(TypeFinancial.EXPENSE);
        input.setPlanAccount(planDto);

        var planAccount = new PlanAccountEntity();
        planAccount.setId(planId);
        planAccount.setFinancialNature(PlanAccountFinancialNature.REVENUE);

        when(entityManager.find(PlanAccountEntity.class, planId)).thenReturn(planAccount);

        var exception = assertThrows(ServiceException.class, () -> service.save(input));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("financial_plan_account_nature_mismatch", exception.getMessage());
        verify(entityManager, never()).persist(any());
        verify(transactionsService, never()).save(any(FinancialEntity.class));
    }

    // Garante que a atualização do lançamento financeiro propaga a alteração para a transação vinculada.
    @Test
    void updateShouldMergeAndUpdateTransaction() {
        UUID id = UUID.randomUUID();
        var input = new FinancialDTO();
        input.setTypeFinancial(TypeFinancial.EXPENSE);

        var entity = new FinancialEntity();
        var expectedDto = new FinancialDTO();

        when(dtoConverter.toEntity(input, null)).thenReturn(entity);
        when(dtoConverter.toDTO(entity, null)).thenReturn(expectedDto);

        var result = service.update(input, id);

        assertSame(expectedDto, result);
        assertEquals(id, entity.getId());
        verify(entityManager).merge(entity);
        verify(transactionsService).update(entity);
    }

    // Garante que a exclusão do lançamento financeiro também remove a movimentação associada.
    @Test
    void deleteShouldRemoveFinancialAndDelegateTransactionDeletion() {
        UUID id = UUID.randomUUID();
        var entity = new FinancialEntity();
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(id);

        verify(repository).deleteById(id);
        verify(transactionsService).delete(entity);
    }
}
