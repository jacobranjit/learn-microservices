package com.mc.oletrex.auth.dto;

import com.mc.oletrex.auth.model.Role;

import java.time.Instant;

/** A user account as shown to admins. Never carries the password hash. */
public record UserResponse(Long id, String email, Role role, boolean enabled, Long customerId, Instant createdAt) {
}
