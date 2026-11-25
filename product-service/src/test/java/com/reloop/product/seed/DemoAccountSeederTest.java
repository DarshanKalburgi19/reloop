package com.reloop.product.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reloop.product.dto.auth.CustomerRegistrationRequest;
import com.reloop.product.dto.auth.RepairerRegistrationRequest;
import com.reloop.product.exception.RepairServiceException;
import com.reloop.product.service.AuthService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** Unit tests of the seeder's decisions, plus a check that the demo data passes API validation. */
class DemoAccountSeederTest {

    private final AuthService authService = mock(AuthService.class);
    private final DemoAccountSeeder seeder = new DemoAccountSeeder(authService, 1, 1);

    @Test
    void existingAccountsAreNotRecreated() {
        when(authService.emailExists(any())).thenReturn(true);

        seeder.seedCustomer();
        assertThat(seeder.trySeedRepairer()).isTrue();

        verify(authService, never()).registerCustomer(any());
        verify(authService, never()).registerRepairer(any());
    }

    @Test
    void repairerSeedingReportsFailureWhenRepairServiceIsDown() {
        when(authService.emailExists(any())).thenReturn(false);
        when(authService.registerRepairer(any()))
                .thenThrow(new RepairServiceException(503, "down", null));

        assertThat(seeder.trySeedRepairer()).isFalse();
    }

    @Test
    void demoDataSatisfiesTheSameValidationAsThePublicApi() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            CustomerRegistrationRequest customer = DemoAccounts.customer();
            RepairerRegistrationRequest repairer = DemoAccounts.repairer();
            assertThat(validator.validate(customer)).isEmpty();
            assertThat(validator.validate(repairer)).isEmpty();
        }
    }
}
