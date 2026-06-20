package com.shortlyai.auth.authentication;

import com.shortlyai.auth.audit.AuditEventType;
import com.shortlyai.auth.audit.AuditLogService;
import com.shortlyai.auth.common.exception.*;
import com.shortlyai.auth.dto.*;
import com.shortlyai.auth.email.VerificationService;
import com.shortlyai.auth.security.JwtUtil;
import com.shortlyai.auth.token.RefreshTokenService;
import com.shortlyai.auth.user.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock JwtUtil jwtUtil;

    @Mock
    UserMapper userMapper;

    @Mock
    RefreshTokenService  refreshTokenService;

    @Mock
    AuditLogService auditLogService;

    @Mock
    VerificationService verificationService;

    @Mock
    HttpServletRequest httpRequest;

    @InjectMocks
    AuthServiceImpl authService;

    private User testUser;

    private UserResponse userResponse;

    private UUID userId;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("$2a$12$encodedPassword")
                .name("Test User")
                .role(Role.ROLE_FREE)
                .provider(Provider.LOCAL)
                .verified(true)
                .createdAt(Instant.now())
                .build();

        userResponse = new UserResponse(
                userId, "Test User", "user@example.com",
                Role.ROLE_FREE, Provider.LOCAL, true, Instant.now()
        );
    }

    @Test
    void login_validCredentials_returnsTokens() {

        LoginRequest request = new LoginRequest("user@example.com", "password123");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);

        when(jwtUtil.generateAccessToken(userId, testUser.getEmail(), "ROLE_FREE")).thenReturn("access-token");

        when(jwtUtil.generateRefreshToken(userId, testUser.getEmail(), "ROLE_FREE")).thenReturn("refresh-token");

        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        AuthResponse response = authService.login(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");

        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        assertThat(response.user()).isEqualTo(userResponse);

        verify(refreshTokenService).store("refresh-token", userId.toString());

        verify(auditLogService).log(eq(AuditEventType.LOGIN_SUCCESS), eq(userId), any());
    }

    @Test
    void login_emailNotFound_throwsInvalidCredentials() {

        LoginRequest request = new LoginRequest("unknown@example.com", "password123");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogService).log(eq(AuditEventType.LOGIN_FAILED), any(HttpServletRequest.class));

        verify(refreshTokenService, never()).store(any(), any());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {

        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches("wrong-password", testUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogService).log(eq(AuditEventType.LOGIN_FAILED), eq(userId), any());

        verify(refreshTokenService, never()).store(any(), any());
    }

    @Test
    void register_newEmail_savesUserAndReturnsTokens() {

        RegisterRequest request = new RegisterRequest("New User", "new@example.com", "password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$encoded");

        when(userRepository.save(any(User.class))).thenReturn(testUser);

        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("access-token");

        when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        AuthResponse response = authService.register(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");

        verify(userRepository).save(any(User.class));

        verify(auditLogService).log(eq(AuditEventType.REGISTER), eq(userId), any());

        verify(verificationService).sendVerificationEmail(testUser);
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest("Test", "user@example.com", "password123");

        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, httpRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());

        verify(auditLogService).log(eq(AuditEventType.REGISTER_FAILED), any(HttpServletRequest.class));
    }

    @Test
    void refresh_validToken_rotatesTokens() {

        String oldToken = "old-refresh-token";

        RefreshTokenRequest request = new RefreshTokenRequest(oldToken);

        when(jwtUtil.isTokenValid(oldToken)).thenReturn(true);

        when(refreshTokenService.exists(oldToken)).thenReturn(true);

        when(jwtUtil.extractUserId(oldToken)).thenReturn(userId.toString());

        when(jwtUtil.extractEmail(oldToken)).thenReturn("user@example.com");

        when(jwtUtil.extractRole(oldToken)).thenReturn("ROLE_FREE");

        when(jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE")).thenReturn("new-access");

        when(jwtUtil.generateRefreshToken(userId, "user@example.com", "ROLE_FREE")).thenReturn("new-refresh");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        AuthResponse response = authService.refresh(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("new-access");

        assertThat(response.refreshToken()).isEqualTo("new-refresh");

        verify(refreshTokenService).delete(oldToken);

        verify(refreshTokenService).store("new-refresh", userId.toString());
    }

    @Test
    void refresh_invalidJwt_throwsInvalidToken() {

        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        when(jwtUtil.isTokenValid("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_tokenNotInRedis_throwsInvalidToken() {

        RefreshTokenRequest request = new RefreshTokenRequest("valid-but-revoked");

        when(jwtUtil.isTokenValid("valid-but-revoked")).thenReturn(true);

        when(refreshTokenService.exists("valid-but-revoked")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logout_validToken_deletesFromRedis() {

        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh");

        when(refreshTokenService.exists("valid-refresh")).thenReturn(true);

        when(jwtUtil.extractUserId("valid-refresh")).thenReturn(userId.toString());

        authService.logout(request, httpRequest);

        verify(refreshTokenService).delete("valid-refresh");

        verify(auditLogService).log(eq(AuditEventType.LOGOUT), eq(userId), any());
    }

    @Test
    void logout_tokenNotInRedis_throwsInvalidToken() {

        RefreshTokenRequest request = new RefreshTokenRequest("stale-token");

        when(refreshTokenService.exists("stale-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.logout(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never()).delete(any());
    }

    @Test
    void getMe_existingUser_returnsUserResponse() {

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = authService.getMe(userId);

        assertThat(result).isEqualTo(userResponse);
    }

    @Test
    void getMe_unknownUser_throwsUserNotFound() {

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(userId))
                .isInstanceOf(UserNotFoundException.class);
    }
}