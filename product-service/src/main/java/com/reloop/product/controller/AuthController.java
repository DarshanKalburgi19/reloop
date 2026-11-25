package com.reloop.product.controller;

import com.reloop.product.dto.auth.AccountResponse;
import com.reloop.product.dto.auth.AuthResponse;
import com.reloop.product.dto.auth.CustomerRegistrationRequest;
import com.reloop.product.dto.auth.LoginRequest;
import com.reloop.product.dto.auth.RepairerRegistrationRequest;
import com.reloop.product.security.AuthenticatedAccount;
import com.reloop.product.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account registration and authentication.
 *
 * <pre>
 * POST /api/auth/register/customer  public   -> 201 AuthResponse (role CUSTOMER)
 * POST /api/auth/register/repairer  public   -> 201 AuthResponse (role REPAIRER, UNVERIFIED)
 * POST /api/auth/login              public   -> 200 AuthResponse (either role)
 * GET  /api/auth/me                 any role -> 200 AccountResponse
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/register/customer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCustomer(request));
    }

    @PostMapping(path = "/register/repairer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> registerRepairer(@Valid @RequestBody RepairerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerRepairer(request));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return authService.me(principal.accountId());
    }
}
