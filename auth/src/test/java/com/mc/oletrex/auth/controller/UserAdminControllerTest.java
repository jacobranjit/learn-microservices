package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.dto.CursorPage;
import com.mc.oletrex.auth.dto.UserResponse;
import com.mc.oletrex.auth.exception.InvalidCursorException;
import com.mc.oletrex.auth.exception.UserNotFoundException;
import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.security.JwtRolesConverter;
import com.mc.oletrex.auth.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.auth.security.SecurityConfig;
import com.mc.oletrex.auth.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.mc.oletrex.auth.security.TestTokens.admin;
import static com.mc.oletrex.auth.security.TestTokens.user;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class UserAdminControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService accountService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static UserResponse response(long id, Role role, boolean enabled) {
        return new UserResponse(id, "u" + id + "@example.com", role, enabled, id == 1 ? null : 40 + id,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void listAsAdminIs200AndNeverContainsPasswordHash() throws Exception {
        when(accountService.listUsers(any(), anyInt()))
                .thenReturn(new CursorPage<>(List.of(response(2, Role.USER, true)), "abc", true));

        mvc.perform(get("/api/auth/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].email").value("u2@example.com"))
                .andExpect(jsonPath("$.nextCursor").value("abc"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password"))));
        verify(accountService).listUsers(null, 20);
    }

    @Test
    void listPassesCursorAndLimit() throws Exception {
        when(accountService.listUsers("c1", 5)).thenReturn(new CursorPage<>(List.of(), null, false));

        mvc.perform(get("/api/auth/users?cursor=c1&limit=5").with(admin())).andExpect(status().isOk());
        verify(accountService).listUsers("c1", 5);
    }

    @Test
    void listAsUserIs403() throws Exception {
        mvc.perform(get("/api/auth/users").with(user(42))).andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    void listWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void listWithBadLimitIs400() throws Exception {
        mvc.perform(get("/api/auth/users?limit=0").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/users?limit=101").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void listWithBadCursorIs400() throws Exception {
        when(accountService.listUsers("!!!", 20)).thenThrow(new InvalidCursorException("!!!"));

        mvc.perform(get("/api/auth/users?cursor=!!!").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Cursor"));
    }

    @Test
    void updateRoleIs200() throws Exception {
        when(accountService.updateRole(2L, Role.ADMIN)).thenReturn(response(2, Role.ADMIN, true));

        mvc.perform(put("/api/auth/users/2/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void updateRoleAsUserIs403() throws Exception {
        mvc.perform(put("/api/auth/users/2/role").with(user(42)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    void invalidRoleIs400() throws Exception {
        mvc.perform(put("/api/auth/users/2/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROOT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/users/2/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accountService);
    }

    @Test
    void unknownIdIs404() throws Exception {
        when(accountService.updateRole(99L, Role.USER)).thenThrow(new UserNotFoundException(99L));

        mvc.perform(put("/api/auth/users/99/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void demotingYourselfIs409() throws Exception {
        mvc.perform(put("/api/auth/users/1/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You cannot change your own role or disable yourself"));
        verifyNoInteractions(accountService);
    }

    @Test
    void disablingYourselfIs409() throws Exception {
        mvc.perform(put("/api/auth/users/1/enabled").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(accountService);
    }

    @Test
    void reEnablingOrKeepingAdminOnYourselfIsAllowed() throws Exception {
        when(accountService.updateRole(1L, Role.ADMIN)).thenReturn(response(1, Role.ADMIN, true));
        when(accountService.updateEnabled(1L, true)).thenReturn(response(1, Role.ADMIN, true));

        mvc.perform(put("/api/auth/users/1/role").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/auth/users/1/enabled").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")).andExpect(status().isOk());
    }

    @Test
    void disableIs200() throws Exception {
        when(accountService.updateEnabled(2L, false)).thenReturn(response(2, Role.USER, false));

        mvc.perform(put("/api/auth/users/2/enabled").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void missingEnabledIs400() throws Exception {
        mvc.perform(put("/api/auth/users/2/enabled").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdOnEnabledIs404() throws Exception {
        when(accountService.updateEnabled(99L, true)).thenThrow(new UserNotFoundException(99L));

        mvc.perform(put("/api/auth/users/99/enabled").with(admin(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
    }
}
