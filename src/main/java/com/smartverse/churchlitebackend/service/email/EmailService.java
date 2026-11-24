package com.smartverse.churchlitebackend.service.email;

import com.smartverse.churchlitebackend.authorization.exception.ServiceException;
import com.smartverse.churchlitebackend.common.FileCommon;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private final RestClient resend;
    private final JavaMailSender mailSender;
    private final String from;
    private final String mailProvider;
    private final String resendKey;

    public EmailService(RestClient.Builder builder,
                        JavaMailSender mailSender,
                        @Value("${app.mail.provider:mailpit}") String mailProvider,
                        @Value("${resend.api-key:${RESEND_KEY:}}") String resendKey,
                        @Value("${resend.from:${RESEND_FROM:Church Lite <no-reply@smartverse.com.br>}}") String from) {
        this.mailSender = mailSender;
        this.mailProvider = mailProvider;
        this.resendKey = resendKey;
        this.resend = builder
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + resendKey)
                .build();
        this.from = from;
    }

    public void sendEmail(String to, String subject, String model, String idempotencyKey) {
        if ("mailpit".equalsIgnoreCase(mailProvider) || resendKey == null || resendKey.isBlank()) {
            sendViaMailpit(to, subject, model);
            return;
        }
        sendViaResend(to, subject, model, idempotencyKey);
    }

    private void sendViaMailpit(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ServiceException(HttpStatus.BAD_GATEWAY, "email_delivery_failed");
        }
    }

    private void sendViaResend(String to, String subject, String model, String idempotencyKey) {
        try {
            var response = resend.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new ResendEmailRequest(from, List.of(to), subject, model))
                    .retrieve()
                    .body(ResendEmailResponse.class);

            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new ServiceException(HttpStatus.BAD_GATEWAY, "email_delivery_failed");
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException(HttpStatus.BAD_GATEWAY, "email_delivery_failed");
        }
    }

    public String loadModel(String modelName) {
        return FileCommon.loadMod(modelName);
    }

    public String renderModel(String modelName, Map<String, String> values) {
        var content = loadModel(modelName);
        for (var entry : values.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        if (content.matches("(?s).*\\{\\{[^}]+}}.*")) {
            throw new IllegalStateException("Email template contains unresolved placeholders: " + modelName);
        }
        return content;
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {}

    private record ResendEmailResponse(String id) {}
}
