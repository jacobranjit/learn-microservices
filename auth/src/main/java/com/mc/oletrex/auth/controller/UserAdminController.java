package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.dto.CursorPage;
import com.mc.oletrex.auth.dto.EnabledUpdateRequest;
import com.mc.oletrex.auth.dto.RoleUpdateRequest;
import com.mc.oletrex.auth.dto.UserResponse;
import com.mc.oletrex.auth.exception.SelfModificationException;
import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only user management; the path is restricted to ADMIN in SecurityConfig. */
@RestController
@Tag(name = "User administration", description = "List accounts, change roles, enable or disable accounts (ADMIN only)")
@RequestMapping("/api/auth/users")
public class UserAdminController {

    private final AccountService accountService;

    public UserAdminController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "List user accounts, one page at a time (ADMIN role, cursor pagination)",
            description = "Omit cursor for the first page, then pass nextCursor until hasMore is false.")
    @GetMapping
    public CursorPage<UserResponse> list(
            @Parameter(description = "nextCursor from the previous page; omit for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return accountService.listUsers(cursor, limit);
    }

    @Operation(summary = "Change a user's role (ADMIN role); you cannot demote yourself")
    @PutMapping("/{id}/role")
    public UserResponse updateRole(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                   @Valid @RequestBody RoleUpdateRequest request) {
        if (request.role() != Role.ADMIN) {
            rejectSelf(jwt, id);
        }
        return accountService.updateRole(id, request.role());
    }

    @Operation(summary = "Enable or disable a user (ADMIN role); you cannot disable yourself")
    @PutMapping("/{id}/enabled")
    public UserResponse updateEnabled(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                      @Valid @RequestBody EnabledUpdateRequest request) {
        if (!request.enabled()) {
            rejectSelf(jwt, id);
        }
        return accountService.updateEnabled(id, request.enabled());
    }

    private static void rejectSelf(Jwt jwt, long id) {
        if (String.valueOf(id).equals(jwt.getSubject())) {
            throw new SelfModificationException();
        }
    }
}
