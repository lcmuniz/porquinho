package com.porquinho.controller;

import com.porquinho.service.EmailUnsubscribeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Public (no JWT): the signed token is the authentication. POST is the RFC 8058 one-click unsubscribe (mail clients
 * send "List-Unsubscribe=One-Click" in the body) and the button of the front page. GET only redirects to that page,
 * so link scanners that prefetch the URL do not unsubscribe anyone.
 */
@RestController
@RequestMapping("/api/v1/email/unsubscribe")
public class EmailUnsubscribeController {

    private final EmailUnsubscribeService emailUnsubscribeService;
    private final String frontendUrl;

    public EmailUnsubscribeController(EmailUnsubscribeService emailUnsubscribeService,
                                      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.emailUnsubscribeService = emailUnsubscribeService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam(required = false) String token) {
        emailUnsubscribeService.unsubscribe(token);
    }

    @GetMapping
    public ResponseEntity<Void> page(@RequestParam(required = false) String token) {
        URI page = UriComponentsBuilder.fromUriString(frontendUrl).path("/descadastro")
            .queryParamIfPresent("token", Optional.ofNullable(token)).encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(page).build();
    }
}
