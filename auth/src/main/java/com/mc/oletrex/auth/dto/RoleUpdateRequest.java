package com.mc.oletrex.auth.dto;

import com.mc.oletrex.auth.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull Role role) {
}
