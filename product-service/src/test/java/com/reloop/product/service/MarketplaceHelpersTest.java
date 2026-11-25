package com.reloop.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reloop.product.enums.ProductCategory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for money conversion, keyword parsing and placeholder images. */
class MarketplaceHelpersTest {

    @Test
    void rupeesAndPaiseConvertExactly() {
        assertThat(Money.toPaise(new BigDecimal("1300.50"))).isEqualTo(130050L);
        assertThat(Money.toPaise(new BigDecimal("2500"))).isEqualTo(250000L);
        assertThat(Money.toRupees(130050L)).isEqualByComparingTo("1300.50");
        assertThat(Money.toRupees(130050L).scale()).isEqualTo(2);
        assertThat(Money.format(110000L)).isEqualTo("\u20B91,100.00");
    }

    @Test
    void moreThanTwoDecimalsIsNeverSilentlyRounded() {
        assertThatThrownBy(() -> Money.toPaise(new BigDecimal("10.999"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void keywordsAreLowerCasedDeduplicatedAndCapped() {
        assertThat(ProductSpecifications.keywords("  Sony   SONY headphones ")).containsExactly("sony", "headphones");
        assertThat(ProductSpecifications.keywords("a b c d e f g")).hasSize(5);
        assertThat(ProductSpecifications.keywords("   ")).isEmpty();
        assertThat(ProductSpecifications.keywords(null)).isEmpty();
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(ProductSpecifications.escapeLike("100%_off\\")).isEqualTo("100\\%\\_off\\\\");
    }

    @Test
    void placeholderPerCategory() {
        assertThat(PlaceholderImages.forCategory(ProductCategory.HOME_KITCHEN)).isEqualTo("/demo-images/placeholders/home-kitchen.svg");
        assertThat(PlaceholderImages.isPlaceholder("/demo-images/placeholders/books.svg")).isTrue();
        assertThat(PlaceholderImages.isPlaceholder("https://example.com/a.jpg")).isFalse();
    }
}
