package com.smartverse.churchlitebackend.service.report;

import com.smartverse.churchlitebackend.enums.SubscriptionFeature;
import com.smartverse.churchlitebackend.service.subscription.SubscriptionService;
import com.smartverse.churchlitebackend.common.RequestData;
import com.smartverse.churchlitebackend.common.ResponseData;
import com.smartverse.churchlitebackend.dtos.ReportTemplateDTO;
import com.smartverse.churchlitebackend.services.ReportTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReportTemplateBusinessService extends ReportTemplateService {
    private final SubscriptionService subscriptionService;

    public ReportTemplateBusinessService(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    @Transactional
    public ReportTemplateDTO save(ReportTemplateDTO dto) {
        requireFeature();
        return super.save(dto);
    }

    @Override
    @Transactional
    public ReportTemplateDTO update(ReportTemplateDTO dto, UUID id) {
        requireFeature();
        return super.update(dto, id);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        requireFeature();
        super.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTemplateDTO get(UUID id) {
        requireFeature();
        return super.get(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseData getAll(RequestData input) {
        requireFeature();
        return super.getAll(input);
    }

    private void requireFeature() {
        subscriptionService.requireFeature(SubscriptionFeature.REPORT_TEMPLATE);
    }
}
