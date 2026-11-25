package com.reloop.product.service;

import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.RepairerProfileClient;
import com.reloop.product.client.RepairerProfileView;
import com.reloop.product.dto.auth.AccountResponse;
import com.reloop.product.dto.auth.AuthResponse;
import com.reloop.product.dto.auth.CustomerDetails;
import com.reloop.product.dto.auth.CustomerRegistrationRequest;
import com.reloop.product.dto.auth.LoginRequest;
import com.reloop.product.dto.auth.RepairerDetails;
import com.reloop.product.dto.auth.RepairerRegistrationRequest;
import com.reloop.product.dto.auth.WorkshopDetails;
import com.reloop.product.entity.Account;
import com.reloop.product.entity.Address;
import com.reloop.product.entity.City;
import com.reloop.product.entity.CustomerAccount;
import com.reloop.product.entity.RepairerAccountIdentity;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.enums.AccountStatus;
import com.reloop.product.enums.RepairMethod;
import com.reloop.product.enums.VerificationStatus;
import com.reloop.product.exception.AccountNotActiveException;
import com.reloop.product.exception.AccountNotFoundException;
import com.reloop.product.exception.EmailAlreadyRegisteredException;
import com.reloop.product.exception.FieldValidationException;
import com.reloop.product.exception.InvalidCredentialsException;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.AddressRepository;
import com.reloop.product.repository.CityRepository;
import com.reloop.product.repository.CustomerAccountRepository;
import com.reloop.product.repository.RepairerAccountIdentityRepository;
import com.reloop.product.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login and "who am I" for both account types.
 *
 * <p>Rules enforced here:</p>
 * <ul>
 *   <li>E-mails are trimmed and lower-cased, so {@code A@X.com} and {@code a@x.com} are the
 *       same account. Duplicates are rejected across BOTH roles (pre-check + the
 *       {@code uk_accounts_email} unique constraint as a race-safe backstop).</li>
 *   <li>Passwords are hashed with BCrypt and never returned, logged or put in a token.</li>
 *   <li>The role is fixed by the endpoint used to register; clients cannot choose it.</li>
 *   <li>Repairers always start {@code UNVERIFIED}.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final AccountRepository accounts;
    private final CustomerAccountRepository customers;
    private final RepairerAccountIdentityRepository repairerIdentities;
    private final AddressRepository addresses;
    private final CityRepository cities;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RepairerProfileClient repairerProfileClient;
    /** Hash compared against when the e-mail is unknown, so login timing does not reveal it. */
    private final String dummyHash;

    public AuthService(AccountRepository accounts,
                       CustomerAccountRepository customers,
                       RepairerAccountIdentityRepository repairerIdentities,
                       AddressRepository addresses,
                       CityRepository cities,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RepairerProfileClient repairerProfileClient) {
        this.accounts = accounts;
        this.customers = customers;
        this.repairerIdentities = repairerIdentities;
        this.addresses = addresses;
        this.cities = cities;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.repairerProfileClient = repairerProfileClient;
        this.dummyHash = passwordEncoder.encode("reloop-timing-equaliser-not-a-real-password-1");
    }

    /* ───────────────────────────── registration ───────────────────────────── */

    @Transactional
    public AuthResponse registerCustomer(CustomerRegistrationRequest req) {
        Account account = createAccount(req.email(), req.password(), AccountRole.CUSTOMER, req.name(), req.phone());

        String[] names = splitName(req.name());
        CustomerAccount customer = new CustomerAccount();
        customer.setAccount(account);
        customer.setFirstName(names[0]);
        customer.setLastName(names[1]);
        customers.save(customer);

        Address address = new Address();
        address.setAccount(account);
        address.setCity(findOrCreateCity(req.city(), req.state()));
        address.setLine1(req.address().trim());
        address.setPincode(req.pincode().trim());
        address.setDefault(true);
        addresses.save(address);

        AccountResponse view = toResponse(account,
                customerDetails(customer, address), null);
        return issueToken(account, view);
    }

    /**
     * Creates the account locally, then asks repair-service to create the workshop profile,
     * then links them. Runs in one local transaction: if repair-service is down (503) or
     * rejects the profile (502), the account insert is rolled back - no half-registered user.
     */
    @Transactional
    public AuthResponse registerRepairer(RepairerRegistrationRequest req) {
        Account account = createAccount(req.email(), req.password(), AccountRole.REPAIRER, req.name(), req.phone());

        Set<RepairMethod> methods = req.repairMethods();
        CreateRepairerProfileCommand command = new CreateRepairerProfileCommand(
                account.getId(),
                req.workshopName().trim(),
                String.join(", ", req.repairExpertise().stream().map(String::trim).toList()),
                req.phone().trim(),
                req.workshopAddress().trim(),
                req.city().trim(),
                blankToNull(req.pincode()),
                req.latitude(),
                req.longitude(),
                req.serviceHours().trim(),
                methods.contains(RepairMethod.IN_PERSON),
                methods.contains(RepairMethod.PICKUP_DROPOFF),
                methods.contains(RepairMethod.SEND_ITEM));
        RepairerProfileView profile = repairerProfileClient.createProfile(command);

        RepairerAccountIdentity identity = new RepairerAccountIdentity();
        identity.setAccount(account);
        identity.setRepairerProfileId(profile.id());
        identity.setVerificationStatus(VerificationStatus.UNVERIFIED);
        repairerIdentities.save(identity);

        AccountResponse view = toResponse(account, null,
                new RepairerDetails(profile.id(), identity.getVerificationStatus(), toWorkshop(profile)));
        return issueToken(account, view);
    }

    /* ──────────────────────────────── login ──────────────────────────────── */

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = normaliseEmail(req.email());
        Account account = accounts.findByEmail(email).orElse(null);
        if (account == null) {
            passwordEncoder.matches(req.password(), dummyHash); // equalise timing
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(req.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getStatus().name());
        }
        return issueToken(account, buildView(account, false));
    }

    /* ────────────────────────────── who am I ─────────────────────────────── */

    /** Current account view; for repairers also tries to include live workshop data. */
    @Transactional(readOnly = true)
    public AccountResponse me(Long accountId) {
        Account account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        return buildView(account, true);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return accounts.existsByEmail(normaliseEmail(email));
    }

    /* ─────────────────────────────── helpers ─────────────────────────────── */

    private Account createAccount(String rawEmail, String rawPassword, AccountRole role, String name, String phone) {
        String email = normaliseEmail(rawEmail);
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new FieldValidationException("password", "must not exceed 72 bytes (BCrypt limit)");
        }
        if (accounts.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Account account = new Account(email, passwordEncoder.encode(rawPassword), role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setDisplayName(name.trim());
        account.setPhone(phone.trim());
        // flush now: assigns the id (needed by repair-service) and fires uk_accounts_email early
        return accounts.saveAndFlush(account);
    }

    private AuthResponse issueToken(Account account, AccountResponse view) {
        JwtService.IssuedToken token = jwtService.issue(account.getId(), account.getEmail(), account.getRole());
        return AuthResponse.bearer(token.token(), token.expiresAt(), token.expiresInSeconds(), view);
    }

    private AccountResponse buildView(Account account, boolean includeWorkshop) {
        if (account.getRole() == AccountRole.CUSTOMER) {
            CustomerAccount customer = customers.findByAccount_Id(account.getId()).orElse(null);
            Address address = addresses.findFirstByAccount_IdAndIsDefaultTrue(account.getId()).orElse(null);
            return toResponse(account, customerDetails(customer, address), null);
        }
        RepairerAccountIdentity identity = repairerIdentities.findByAccount_Id(account.getId()).orElse(null);
        if (identity == null) {
            return toResponse(account, null, null);
        }
        WorkshopDetails workshop = includeWorkshop
                ? repairerProfileClient.findProfile(identity.getRepairerProfileId()).map(AuthService::toWorkshop).orElse(null)
                : null;
        return toResponse(account, null,
                new RepairerDetails(identity.getRepairerProfileId(), identity.getVerificationStatus(), workshop));
    }

    private static AccountResponse toResponse(Account a, CustomerDetails customer, RepairerDetails repairer) {
        return new AccountResponse(a.getId(), a.getRole(), a.getStatus(), a.getDisplayName(), a.getEmail(),
                a.getPhone(), a.getCreatedAt(), customer, repairer);
    }

    private static CustomerDetails customerDetails(CustomerAccount customer, Address address) {
        if (customer == null && address == null) {
            return null;
        }
        City city = address != null ? address.getCity() : null;
        return new CustomerDetails(
                customer != null ? customer.getFirstName() : null,
                customer != null ? customer.getLastName() : null,
                address != null ? address.getLine1() : null,
                city != null ? city.getName() : null,
                city != null ? city.getState() : null,
                address != null ? address.getPincode() : null);
    }

    static WorkshopDetails toWorkshop(RepairerProfileView p) {
        EnumSet<RepairMethod> methods = EnumSet.noneOf(RepairMethod.class);
        if (p.acceptsInPerson()) {
            methods.add(RepairMethod.IN_PERSON);
        }
        if (p.acceptsShipping()) {
            methods.add(RepairMethod.SEND_ITEM);
        }
        if (p.acceptsPickupDrop()) {
            methods.add(RepairMethod.PICKUP_DROPOFF);
        }
        List<String> expertise = p.specialties() == null || p.specialties().isBlank()
                ? List.of()
                : Arrays.stream(p.specialties().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new WorkshopDetails(p.businessName(), expertise, p.addressLine(), p.city(), p.pincode(),
                p.latitude(), p.longitude(), p.serviceHours(), methods, p.verificationStatus());
    }

    private City findOrCreateCity(String rawName, String rawState) {
        String name = rawName.trim();
        return cities.findFirstByNameIgnoreCase(name).orElseGet(() -> {
            String state = rawState == null || rawState.isBlank() ? "Unspecified" : rawState.trim();
            return cities.save(new City(name, state));
        });
    }

    /** "Asha Patel" -> ["Asha", "Patel"]; "Asha" -> ["Asha", ""]. */
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, ""};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
