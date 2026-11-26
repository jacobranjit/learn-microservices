package com.mc.oletrex.auth.service.impl;

import com.mc.oletrex.auth.client.CustomerClient;
import com.mc.oletrex.auth.dto.LoginRequest;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.exception.EmailAlreadyRegisteredException;
import com.mc.oletrex.auth.exception.InvalidCredentialsException;
import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.model.UserAccount;
import com.mc.oletrex.auth.repository.UserAccountRepository;
import com.mc.oletrex.auth.service.AccountService;
import com.mc.oletrex.auth.token.JwtIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Deliberately not @Transactional as a whole: registration makes a remote call between two
 * local writes, so each repository call commits on its own and failures are compensated by hand.
 */
@Service
public class AccountServiceImpl implements AccountService {

    private static final String INVALID = "Invalid email or password";

    private final UserAccountRepository repository;
    private final CustomerClient customerClient;
    private final JwtIssuer jwtIssuer;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when the email is unknown, so unknown and wrong-password cost the same. */
    private final String dummyHash;

    public AccountServiceImpl(UserAccountRepository repository, CustomerClient customerClient,
                              JwtIssuer jwtIssuer, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.customerClient = customerClient;
        this.jwtIssuer = jwtIssuer;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Override
    public TokenResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (repository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }
        UserAccount account = repository.saveAndFlush(
                new UserAccount(email, passwordEncoder.encode(request.password()), Role.USER, true, null));

        long customerId;
        try {
            customerId = customerClient.createCustomer(account.getId(), withEmail(request, email));
        } catch (RuntimeException e) {
            repository.delete(account);
            throw e;
        }

        try {
            account.setCustomerId(customerId);
            repository.saveAndFlush(account);
        } catch (RuntimeException e) {
            customerClient.deleteCustomer(customerId);
            repository.delete(account);
            throw e;
        }
        return jwtIssuer.issueUserToken(account.getId(), email, Role.USER, customerId);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        UserAccount account = repository.findByEmail(normalise(request.email())).orElse(null);
        boolean passwordOk = passwordEncoder.matches(request.password(),
                account != null ? account.getPasswordHash() : dummyHash);
        if (account == null || !passwordOk || !account.isEnabled()) {
            throw new InvalidCredentialsException(INVALID);
        }
        return jwtIssuer.issueUserToken(account.getId(), account.getEmail(), account.getRole(), account.getCustomerId());
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static RegisterRequest withEmail(RegisterRequest r, String email) {
        return new RegisterRequest(email, r.password(), r.name(), r.phone(), r.deliveryAddress());
    }
}
