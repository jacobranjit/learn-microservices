package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.dto.CursorPage;
import com.mc.oletrex.auth.dto.LoginRequest;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.dto.UserResponse;
import com.mc.oletrex.auth.model.Role;

public interface AccountService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    CursorPage<UserResponse> listUsers(String cursor, int limit);

    UserResponse updateRole(long id, Role role);

    UserResponse updateEnabled(long id, boolean enabled);
}
