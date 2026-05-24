package com.securebank.service;

import com.securebank.domain.Role;
import com.securebank.domain.User;
import com.securebank.dto.AuthResponse;
import com.securebank.dto.LoginRequest;
import com.securebank.dto.RegisterRequest;
import com.securebank.exception.DuplicateResourceException;
import com.securebank.repository.UserRepository;
import com.securebank.security.JwtTokenProvider;
import com.securebank.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.USER);
        user = userRepository.save(user);

        auditService.record(user.getUsername(), "USER_REGISTERED", "New user account created");

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return AuthResponse.bearer(token, jwtTokenProvider.getExpirationMs(), user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String token = jwtTokenProvider.generateToken(principal.getId(), principal.getUsername());
            auditService.record(principal.getUsername(), "USER_LOGIN", "Successful login");
            return AuthResponse.bearer(token, jwtTokenProvider.getExpirationMs(), principal.getUsername());
        } catch (BadCredentialsException ex) {
            // Logged in its own transaction so the failed-attempt record survives.
            auditService.record(request.username(), "LOGIN_FAILED", "Failed login attempt");
            throw ex;
        }
    }
}
