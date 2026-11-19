package com.porquinho.service;

import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Unsubscribe links of the optional e-mails (today only the budget alert): List-Unsubscribe with one-click POST
 * (RFC 8058) and a link in the body. The token is stateless: "{userId}.{HMAC-SHA256(secret, purpose:userId)}", so it
 * never expires and needs no table. Transactional e-mails (trial, retention, billing, admin alerts) carry no link.
 */
@Service
public class EmailUnsubscribeService {

    /** Purpose of the budget alert token; a token of another purpose does not turn this one off. */
    public static final String BUDGET_ALERT = "budget-alert";

    private static final Logger log = LoggerFactory.getLogger(EmailUnsubscribeService.class);

    private final UserRepository userRepository;
    private final byte[] secret;
    private final String apiBaseUrl;

    public EmailUnsubscribeService(UserRepository userRepository,
                                   @Value("${app.email.unsubscribe-secret:}") String secret,
                                   @Value("${app.export.base-url:http://localhost:8080}") String apiBaseUrl) {
        this.userRepository = userRepository;
        if (secret == null || secret.isBlank()) {
            // Random per boot: links stay unforgeable, but the ones sent before a restart stop working
            log.warn("EMAIL_UNSUBSCRIBE_SECRET empty - unsubscribe links of e-mails sent before a restart will be rejected");
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    /** One-click URL of the List-Unsubscribe header; a GET on it (link in the body) opens the confirmation page. */
    public String budgetAlertUnsubscribeUrl(UUID userId) {
        return apiBaseUrl + "/api/v1/email/unsubscribe?token=" + token(userId, BUDGET_ALERT);
    }

    /** Turns the budget alert e-mail off. A valid token of a user that no longer exists is a silent no-op. */
    @Transactional
    public void unsubscribe(String token) {
        UUID userId = verify(token, BUDGET_ALERT);
        userRepository.findById(userId).ifPresent(user -> {
            if (user.isBudgetAlertEmailEnabled()) {
                user.setBudgetAlertEmailEnabled(false);
                userRepository.save(user);
                log.info("Budget alert e-mail turned off by unsubscribe link for user {}", userId);
            }
        });
    }

    String token(UUID userId, String purpose) {
        return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(purpose + ":" + userId));
    }

    /** The user of a token of this purpose; any other input is a 400 that says nothing about why. */
    UUID verify(String token, String purpose) {
        BadRequestException invalid = new BadRequestException("Link de descadastro inválido");
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            throw invalid;
        }
        String id = token.substring(0, dot);
        UUID userId;
        byte[] signature;
        try {
            userId = UUID.fromString(id);
            signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid;
        }
        // UUID.fromString accepts non-canonical forms; the signature covers the canonical one
        if (!userId.toString().equals(id) || !MessageDigest.isEqual(signature, hmac(purpose + ":" + userId))) {
            throw invalid;
        }
        return userId;
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
