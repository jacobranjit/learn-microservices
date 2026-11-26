package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.dto.LoginRequest;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;

public interface AccountService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
