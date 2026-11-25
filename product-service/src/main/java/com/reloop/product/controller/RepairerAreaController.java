package com.reloop.product.controller;

import com.reloop.product.security.AuthenticatedAccount;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repairer-only area. Guarded twice: URL rule {@code /api/repairer/**} -> ROLE_REPAIRER in
 * SecurityConfig, and {@code @PreAuthorize} here. A CUSTOMER token gets 403.
 * Later phases add repairer features (quotes, inventory) under this prefix.
 */
@RestController
@RequestMapping(path = "/api/repairer", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('REPAIRER')")
public class RepairerAreaController {

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedAccount principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("area", "repairer");
        body.put("accountId", principal.accountId());
        body.put("email", principal.email());
        body.put("role", principal.role());
        body.put("message", "Welcome to the ReLoop repairer area");
        return body;
    }
}
