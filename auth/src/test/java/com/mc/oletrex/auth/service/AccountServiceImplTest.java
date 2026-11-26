package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.client.CustomerClient;
import com.mc.oletrex.auth.client.CustomerServiceUnavailableException;
import com.mc.oletrex.auth.dto.AddressDto;
import com.mc.oletrex.auth.dto.LoginRequest;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.exception.EmailAlreadyRegisteredException;
import com.mc.oletrex.auth.exception.InvalidCredentialsException;
import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.model.UserAccount;
import com.mc.oletrex.auth.repository.UserAccountRepository;
import com.mc.oletrex.auth.service.impl.AccountServiceImpl;
import com.mc.oletrex.auth.token.AuthProperties;
import com.mc.oletrex.auth.token.JwtIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final CustomerClient customerClient = mock(CustomerClient.class);
    private final JwtIssuer jwtIssuer = mock(JwtIssuer.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final TokenResponse token = TokenResponse.bearer("tok", 3600);

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(repository, customerClient, jwtIssuer, encoder);
    }

    private static RegisterRequest request(String email) {
        return new RegisterRequest(email, "password1", "Alice", "+1 555 0100",
                new AddressDto("1 Main St", null, "Springfield", null, "12345", "US"));
    }

    /** saveAndFlush returns its argument, assigning id 5 like the database would. */
    private void saveAssignsId() {
        when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 5L);
            return a;
        });
    }

    @Test
    void registerNormalisesEmailAndCreatesCustomer() {
        saveAssignsId();
        when(customerClient.createCustomer(eq(5L), any())).thenReturn(7L);
        when(jwtIssuer.issueUserToken(5L, "alice@example.com", Role.USER, 7L)).thenReturn(token);

        TokenResponse result = service.register(request(" Alice@Example.COM "));

        assertThat(result).isSameAs(token);
        verify(repository).existsByEmail("alice@example.com");
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        UserAccount account = saved.getAllValues().get(0);
        assertThat(account.getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getRole()).isEqualTo(Role.USER);
        assertThat(account.isEnabled()).isTrue();
        assertThat(encoder.matches("password1", account.getPasswordHash())).isTrue();
        assertThat(account.getCustomerId()).isEqualTo(7L);
        ArgumentCaptor<RegisterRequest> sent = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(customerClient).createCustomer(eq(5L), sent.capture());
        assertThat(sent.getValue().email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerDuplicateEmailIs409NoCustomerCall() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("Alice@example.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verifyNoInteractions(customerClient);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void customerServiceDownDeletesAccount() {
        saveAssignsId();
        CustomerServiceUnavailableException down = new CustomerServiceUnavailableException("down", null);
        when(customerClient.createCustomer(anyLong(), any())).thenThrow(down);

        assertThatThrownBy(() -> service.register(request("alice@example.com"))).isSameAs(down);

        ArgumentCaptor<UserAccount> deleted = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).delete(deleted.capture());
        assertThat(deleted.getValue().getEmail()).isEqualTo("alice@example.com");
        verify(customerClient, never()).deleteCustomer(anyLong());
        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void customerConflictDeletesAccountAndRethrows409() {
        saveAssignsId();
        when(customerClient.createCustomer(anyLong(), any())).thenThrow(new EmailAlreadyRegisteredException("taken"));

        assertThatThrownBy(() -> service.register(request("alice@example.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(repository).delete(any(UserAccount.class));
    }

    @Test
    void secondSaveFailsCompensatesCustomer() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("boom");
        when(repository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(inv -> {
                    UserAccount a = inv.getArgument(0);
                    ReflectionTestUtils.setField(a, "id", 5L);
                    return a;
                })
                .thenThrow(failure);
        when(customerClient.createCustomer(eq(5L), any())).thenReturn(7L);

        assertThatThrownBy(() -> service.register(request("alice@example.com"))).isSameAs(failure);

        verify(customerClient).deleteCustomer(7L);
        verify(repository).delete(any(UserAccount.class));
        verifyNoInteractions(jwtIssuer);
    }

    private UserAccount stored(boolean enabled) {
        UserAccount a = new UserAccount("alice@example.com", encoder.encode("password1"), Role.USER, enabled, 7L);
        ReflectionTestUtils.setField(a, "id", 5L);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void loginIsCaseInsensitive() {
        stored(true);
        when(jwtIssuer.issueUserToken(5L, "alice@example.com", Role.USER, 7L)).thenReturn(token);

        assertThat(service.login(new LoginRequest(" ALICE@example.com ", "password1"))).isSameAs(token);
    }

    @Test
    void loginWrongPasswordUnknownEmailOrDisabled() {
        stored(true);
        assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "password1")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");

        stored(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "password1")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void adminBootstrapCreatesOnceOnly() {
        List<UserAccount> db = new ArrayList<>();
        when(repository.existsByEmail(any())).thenAnswer(inv ->
                db.stream().anyMatch(a -> a.getEmail().equals(inv.getArgument(0))));
        when(repository.save(any(UserAccount.class))).thenAnswer(inv -> {
            db.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AuthProperties props = new AuthProperties("iss", Duration.ofHours(1),
                new AuthProperties.Admin(" Admin@Oletrex.Local ", "Admin123!"), Map.of());
        AdminBootstrap bootstrap = new AdminBootstrap(repository, encoder, props);

        bootstrap.run(new DefaultApplicationArguments());
        String firstHash = db.get(0).getPasswordHash();
        bootstrap.run(new DefaultApplicationArguments());

        assertThat(db).hasSize(1);
        assertThat(db.get(0).getEmail()).isEqualTo("admin@oletrex.local");
        assertThat(db.get(0).getRole()).isEqualTo(Role.ADMIN);
        assertThat(db.get(0).isEnabled()).isTrue();
        assertThat(db.get(0).getCustomerId()).isNull();
        assertThat(encoder.matches("Admin123!", firstHash)).isTrue();
        assertThat(db.get(0).getPasswordHash()).isEqualTo(firstHash);
    }
}
