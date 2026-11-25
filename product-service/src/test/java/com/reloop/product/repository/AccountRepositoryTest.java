package com.reloop.product.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reloop.product.entity.Account;
import com.reloop.product.entity.Product;
import com.reloop.product.entity.ProductPassport;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the product-service schema works end-to-end on H2:
 * unique email constraint, timestamps auto-filled, paise price column,
 * and indexed status/title search queries.
 */
@SpringBootTest
@Transactional
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ProductPassportRepository passports;

    @Test
    void accountRoundTripFillsTimestampsAndFindsByEmail() {
        Account account = new Account("ana@reloop.test", "bcrypt-hash", AccountRole.CUSTOMER);
        account = accounts.saveAndFlush(account);

        assertNotNull(account.getId(), "id should be generated");
        assertNotNull(account.getCreatedAt(), "created_at should be set by @PrePersist");
        assertNotNull(account.getUpdatedAt(), "updated_at should be set by @PrePersist");

        assertTrue(accounts.findByEmail("ana@reloop.test").isPresent());
        assertTrue(accounts.existsByEmail("ana@reloop.test"));
        assertEquals(1, accounts.findByRole(AccountRole.CUSTOMER).size());
    }

    @Test
    void duplicateEmailIsRejectedByTheDatabase() {
        accounts.saveAndFlush(new Account("dup@reloop.test", "hash-1", AccountRole.CUSTOMER));

        assertThrows(DataIntegrityViolationException.class, () ->
                accounts.saveAndFlush(new Account("dup@reloop.test", "hash-2", AccountRole.REPAIRER)));
    }

    @Test
    void productSavesPriceInPaiseAndSearchesByStatusAndTitle() {
        Account seller = accounts.saveAndFlush(new Account("seller@reloop.test", "hash", AccountRole.CUSTOMER));

        Product product = new Product();
        product.setSeller(seller);
        product.setTitle("Refurbished iPhone 12");
        product.setCategory(ProductCategory.ELECTRONICS);
        product.setPricePaise(3299900L); // ₹32,999.00 stored as paise
        product.setStatus(ProductStatus.ACTIVE);
        product.setCondition(ProductCondition.VERY_GOOD);
        product.setPassport(passports.saveAndFlush(new ProductPassport("RLP-TEST-0000-0001", seller)));

        Product saved = products.saveAndFlush(product);
        assertNotNull(saved.getId());
        assertEquals(3299900L, saved.getPricePaise());

        assertEquals(1, products.findByStatus(ProductStatus.ACTIVE).size());
        assertEquals(1, products.findByTitleContainingIgnoreCase("iphone").size());
        assertEquals(1, products.findBySeller_Id(seller.getId()).size());
    }

    @Test
    void passportCodeIsUnique() {
        Account owner = accounts.saveAndFlush(new Account("owner@reloop.test", "hash", AccountRole.CUSTOMER));
        passports.saveAndFlush(new ProductPassport("RLP-TEST-0000-0002", owner));

        assertThrows(DataIntegrityViolationException.class, () ->
                passports.saveAndFlush(new ProductPassport("RLP-TEST-0000-0002", owner)));
    }
}
