package com.smartverse.churchlitebackend.service.planaccount;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.dtos.PlanAccountDTO;
import com.smartverse.churchlitebackend.entities.PlanAccountEntity;
import com.smartverse.churchlitebackend.services.PlanAccountService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlanAccountBusinessService extends PlanAccountService {

    private final EntityManager entityManager;

    public PlanAccountBusinessService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public PlanAccountDTO save(PlanAccountDTO obj) {
        var entity = dtoConverter.toEntity(obj, null);
        entity.setFinancialNature(resolveNature(entity));
        entityManager.persist(entity);
        return dtoConverter.toDTO(entity, null);
    }

    @Override
    @Transactional
    public PlanAccountDTO update(PlanAccountDTO obj, UUID id) {
        var current = repository.findById(id)
                .orElseThrow(() -> error("plan_account_not_found"));
        var entity = dtoConverter.toEntity(obj, null);
        entity.setId(id);
        entity.setChildren(current.getChildren());

        if (obj.getFinancialNature() != null && obj.getFinancialNature() != current.getFinancialNature()) {
            throw error("plan_account_financial_nature_immutable");
        }
        entity.setFinancialNature(current.getFinancialNature());
        if (entity.getParentCode() != null) {
            var parent = repository.findById(entity.getParentCode().getId())
                    .orElseThrow(() -> error("plan_account_parent_not_found"));
            if (parent.getFinancialNature() != current.getFinancialNature()) {
                throw error("plan_account_parent_financial_nature_mismatch");
            }
            entity.setParentCode(parent);
        }

        entityManager.merge(entity);
        return dtoConverter.toDTO(entity, null);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var entity = repository.findById(id).orElse(null);
        if (entity != null) {
            if (entity.getParentCode() != null) {
                var parent = repository.findById(entity.getParentCode().getId()).orElse(null);
                parent.getChildren().remove(entity);
                entityManager.merge(parent);
            } else {
                entityManager.remove(entity);
            }
        }
    }

    private com.smartverse.churchlitebackend.enums.PlanAccountFinancialNature resolveNature(PlanAccountEntity entity) {
        if (entity.getParentCode() == null) {
            if (entity.getFinancialNature() == null) throw error("plan_account_financial_nature_required");
            return entity.getFinancialNature();
        }
        var parent = repository.findById(entity.getParentCode().getId())
                .orElseThrow(() -> error("plan_account_parent_not_found"));
        entity.setParentCode(parent);
        return parent.getFinancialNature();
    }

    private ServiceException error(String key) {
        return new ServiceException(HttpStatus.UNPROCESSABLE_ENTITY, key);
    }
}
