package com.electricitysplit.controller;

import com.electricitysplit.dto.ApiResponse;
import com.electricitysplit.dto.AuthDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthDto.LoginResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDto.UserResponse> getCurrentUser() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(authService.getCurrentUser(currentUser));
    }

    @PutMapping("/me")
    public ApiResponse<AuthDto.UserResponse> updateCurrentUser(@Valid @RequestBody AuthDto.UpdateUserRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.success(authService.updateCurrentUser(currentUser, request));
    }
}
