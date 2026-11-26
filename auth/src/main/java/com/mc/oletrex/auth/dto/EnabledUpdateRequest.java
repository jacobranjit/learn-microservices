package com.mc.oletrex.auth.dto;

import jakarta.validation.constraints.NotNull;

public record EnabledUpdateRequest(@NotNull Boolean enabled) {
}
