package com.reloop.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reloop.product.repository.ProductPassportRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests (no Spring) for passport code format, randomness and collision handling. */
class PassportCodeGeneratorTest {

    private final ProductPassportRepository repo = mock(ProductPassportRepository.class);
    private final PassportCodeGenerator generator = new PassportCodeGenerator(repo);

    @Test
    void codesHaveTheDocumentedReadableFormat() {
        for (int i = 0; i < 200; i++) {
            String code = generator.randomCode();
            assertThat(code).matches(PassportCodeGenerator.FORMAT.pattern());
            assertThat(code.substring(4)).doesNotContain("I", "L", "O", "U");
        }
    }

    @Test
    void codesAreRandomEnoughToBeUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.randomCode());
        }
        assertThat(seen).hasSize(10_000);
    }

    @Test
    void collisionsAreRetried() {
        when(repo.existsByPassportCode(anyString())).thenReturn(true, true, false);
        assertThat(generator.generateUnique()).matches(PassportCodeGenerator.FORMAT.pattern());
        verify(repo, times(3)).existsByPassportCode(anyString());
    }

    @Test
    void givesUpAfterRepeatedCollisions() {
        when(repo.existsByPassportCode(anyString())).thenReturn(true);
        assertThatThrownBy(generator::generateUnique).isInstanceOf(IllegalStateException.class);
    }
}
