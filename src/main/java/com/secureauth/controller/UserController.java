package com.secureauth.controller;

import com.secureauth.dto.UserProfileResponse;
import com.secureauth.security.UserDetailsImpl;
import com.secureauth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getUsername()));
    }
}
