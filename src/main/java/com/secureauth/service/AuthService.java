package com.secureauth.service;

import com.secureauth.dto.*;
import com.secureauth.entity.Role;
import com.secureauth.entity.User;
import com.secureauth.exception.AccountLockedException;
import com.secureauth.exception.BadRequestException;
import com.secureauth.exception.ResourceNotFoundException;
import com.secureauth.exception.TokenExpiredException;
import com.secureauth.repository.UserRepository;
import com.secureauth.security.JwtUtil;
import com.secureauth.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .emailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();

        userRepository.save(user);
//
//        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationToken);

        return new MessageResponse("Registration successful. Please check your email to verify your account.");
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {

        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() ->
                        new BadRequestException("Invalid verification token"));

        if (user.getVerificationTokenExpiry()
                .isBefore(LocalDateTime.now())) {

            throw new TokenExpiredException(
                    "Verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);

        userRepository.save(user);

        return new MessageResponse(
                "Email verified successfully. You can now log in.");
    }

    @Transactional
    public JwtResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        checkAndUnlockAccountIfEligible(user);

        if (user.isAccountLocked()) {
            throw new AccountLockedException(
                    "Account is locked due to multiple failed login attempts. Try again after "
                    + LOCK_DURATION_MINUTES + " minutes.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase().trim(), request.getPassword())
            );

            // Successful login: reset failed attempts
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            String accessToken = jwtUtil.generateAccessToken(userDetails, user.getRole().name());
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);

            user.setRefreshToken(refreshToken);
            user.setRefreshTokenExpiry(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpirationMs() / 1000));
            userRepository.save(user);

            return JwtResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .build();

        } catch (org.springframework.security.authentication.DisabledException ex) {
            throw ex; // handled globally -> "email not verified"
        } catch (BadCredentialsException ex) {
            registerFailedAttempt(user);
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now());
        }
        userRepository.save(user);
    }

    private void checkAndUnlockAccountIfEligible(User user) {
        if (user.isAccountLocked() && user.getLockTime() != null) {
            boolean lockExpired = user.getLockTime()
                    .plusMinutes(LOCK_DURATION_MINUTES)
                    .isBefore(LocalDateTime.now());

            if (lockExpired) {
                user.setAccountLocked(false);
                user.setFailedLoginAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        }
    }

    @Transactional
    public JwtResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        User user = userRepository.findByRefreshToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (user.getRefreshTokenExpiry() == null || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token has expired. Please log in again.");
        }

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String newAccessToken = jwtUtil.generateAccessToken(userDetails, user.getRole().name());

        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public MessageResponse logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(null);
        userRepository.save(user);
        return new MessageResponse("Logged out successfully");
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElse(null);

        // Always return a generic success message so we don't leak which emails are registered
        if (user == null) {
            return new MessageResponse("If an account exists with this email, a reset link has been sent.");
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetToken);

        return new MessageResponse("If an account exists with this email, a reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Reset link has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        // Invalidate existing sessions
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(null);
        userRepository.save(user);

        return new MessageResponse("Password reset successful. Please log in with your new password.");
    }
}
