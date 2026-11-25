package reloop.product.enums;

/**
 * AccountRole - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>The two ReLoop account types. Fixed forever at registration - a CUSTOMER
 * can never become a REPAIRER (strict separation rule).</p>
 */
public enum AccountRole {

    CUSTOMER,
    REPAIRER

}
