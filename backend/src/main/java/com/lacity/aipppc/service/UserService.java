package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.auth.*;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.UserRepository;
import com.lacity.aipppc.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(req.password()))
            .name(req.name().trim())
            .organization(req.organization())
            .role(User.Role.APPLICANT)
            .enabled(true)
            .build();
        userRepository.save(user);
        return issueToken(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> ApiException.notFound("User not found"));
        return issueToken(user);
    }

    public UserDto getMe(String email) {
        return UserDto.from(requireUser(email));
    }

    @Transactional
    public AuthResponse updateProfile(String email, UpdateProfileRequest req) {
        User user = requireUser(email);
        user.setName(req.name().trim());
        user.setOrganization(req.organization());
        userRepository.save(user);
        return issueToken(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = requireUser(email);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    public User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private AuthResponse issueToken(User user) {
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(details);
        return new AuthResponse(token, UserDto.from(user));
    }
}
