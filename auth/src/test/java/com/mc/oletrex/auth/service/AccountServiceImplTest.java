package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.client.CustomerClient;
import com.mc.oletrex.auth.client.CustomerServiceUnavailableException;
import com.mc.oletrex.auth.dto.AddressDto;
import com.mc.oletrex.auth.dto.CursorPage;
import com.mc.oletrex.auth.dto.LoginRequest;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.dto.UserResponse;
import com.mc.oletrex.auth.exception.InvalidCursorException;
import com.mc.oletrex.auth.exception.UserNotFoundException;
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
import org.springframework.data.domain.Limit;
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
    private final List<Boolean> enabledAtEachSave = new ArrayList<>();

    private void saveAssignsId() {
        when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            enabledAtEachSave.add(a.isEnabled());
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
        assertThat(enabledAtEachSave).containsExactly(false, true);
        assertThat(encoder.matches("password1", account.getPasswordHash())).isTrue();
        assertThat(account.getCustomerId()).isEqualTo(7L);
        ArgumentCaptor<RegisterRequest> sent = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(customerClient).createCustomer(eq(5L), sent.capture());
        assertThat(sent.getValue().email()).isEqualTo("alice@example.com");
    }

    @Test
    void interimAccountIsDisabledSoALostCompensationCannotLeaveALoginableHalfAccount() {
        saveAssignsId();
        when(customerClient.createCustomer(eq(5L), any())).thenThrow(new CustomerServiceUnavailableException("down", null));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(repository).delete(any(UserAccount.class));

        assertThatThrownBy(() -> service.register(request("alice@example.com")))
                .isInstanceOf(CustomerServiceUnavailableException.class);

        assertThat(enabledAtEachSave).containsExactly(false);
    }

    @Test
    void adminBootstrapResetRehashesExistingAdmin() {
        UserAccount existing = new UserAccount("admin@oletrex.local", encoder.encode("old-password"), Role.USER, false, null);
        when(repository.existsByEmail("admin@oletrex.local")).thenReturn(true);
        when(repository.findByEmail("admin@oletrex.local")).thenReturn(java.util.Optional.of(existing));
        AuthProperties props = new AuthProperties("iss", Duration.ofHours(1),
                new AuthProperties.Admin("admin@oletrex.local", "NewPassw0rd!", true), Map.of());

        new AdminBootstrap(repository, encoder, props).run(new DefaultApplicationArguments());

        assertThat(encoder.matches("NewPassw0rd!", existing.getPasswordHash())).isTrue();
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        verify(repository).save(existing);
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

    @Test
    void deleteCustomerFailsStillDeletesAccount() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("boom");
        CustomerServiceUnavailableException compensation = new CustomerServiceUnavailableException("down", null);
        when(repository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(inv -> {
                    UserAccount a = inv.getArgument(0);
                    ReflectionTestUtils.setField(a, "id", 5L);
                    return a;
                })
                .thenThrow(failure);
        when(customerClient.createCustomer(eq(5L), any())).thenReturn(7L);
        org.mockito.Mockito.doThrow(compensation).when(customerClient).deleteCustomer(7L);

        assertThatThrownBy(() -> service.register(request("alice@example.com")))
                .isSameAs(failure)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(compensation));

        verify(repository).delete(any(UserAccount.class));
    }

    @Test
    void accountDeleteFailsOriginalErrorStillThrown() {
        saveAssignsId();
        CustomerServiceUnavailableException down = new CustomerServiceUnavailableException("down", null);
        IllegalStateException dbDown = new IllegalStateException("db");
        when(customerClient.createCustomer(anyLong(), any())).thenThrow(down);
        org.mockito.Mockito.doThrow(dbDown).when(repository).delete(any(UserAccount.class));

        assertThatThrownBy(() -> service.register(request("alice@example.com")))
                .isSameAs(down)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(dbDown));
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
                new AuthProperties.Admin(" Admin@Oletrex.Local ", "Admin123!", false), Map.of());
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

    private static UserAccount account(long id, Role role, boolean enabled) {
        UserAccount a = new UserAccount("u" + id + "@example.com", "HASH", role, enabled, null);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void listUsersOverFetchesOneRowAndReturnsCursorWhenMore() {
        when(repository.findByIdGreaterThanOrderByIdAsc(0L, Limit.of(3)))
                .thenReturn(List.of(account(1, Role.ADMIN, true), account(2, Role.USER, true), account(3, Role.USER, true)));

        CursorPage<UserResponse> page = service.listUsers(null, 2);

        assertThat(page.items()).extracting(UserResponse::id).containsExactly(1L, 2L);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("id:2".getBytes()));
    }

    @Test
    void listUsersLastPageHasNoCursorAndContinuesAfterDecodedId() {
        when(repository.findByIdGreaterThanOrderByIdAsc(2L, Limit.of(3))).thenReturn(List.of(account(3, Role.USER, true)));
        String cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("id:2".getBytes());

        CursorPage<UserResponse> page = service.listUsers(cursor, 2);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listUsersRejectsBadCursor() {
        assertThatThrownBy(() -> service.listUsers("!!!", 20)).isInstanceOf(InvalidCursorException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void updateRoleAndEnabledPersistAndNeverExposeHash() {
        UserAccount a = account(2, Role.USER, true);
        when(repository.findById(2L)).thenReturn(Optional.of(a));
        when(repository.saveAndFlush(a)).thenReturn(a);

        assertThat(service.updateRole(2L, Role.ADMIN).role()).isEqualTo(Role.ADMIN);
        assertThat(service.updateEnabled(2L, false).enabled()).isFalse();
        assertThat(a.getRole()).isEqualTo(Role.ADMIN);
        assertThat(a.isEnabled()).isFalse();
    }

    @Test
    void updateUnknownUserThrowsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateRole(9L, Role.ADMIN)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service.updateEnabled(9L, true)).isInstanceOf(UserNotFoundException.class);
    }
}
