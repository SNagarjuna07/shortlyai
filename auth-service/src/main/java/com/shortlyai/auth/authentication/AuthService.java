package com.shortlyai.auth.authentication;

import com.shortlyai.auth.dto.AuthResponse;
import com.shortlyai.auth.dto.LoginRequest;
import com.shortlyai.auth.dto.RegisterRequest;

// Interface — defines the contract, hides implementation details
// AuthServiceImpl is the only class that knows HOW login works
// Controllers and tests only depend on this interface, not the impl
public interface AuthService {

    // Login — validates credentials, returns tokens + user info
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest registerRequest);
}