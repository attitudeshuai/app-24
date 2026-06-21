package com.electricitysplit.service;

import com.electricitysplit.config.JwtUtil;
import com.electricitysplit.dto.AuthDto;
import com.electricitysplit.entity.User;
import com.electricitysplit.exception.BusinessException;
import com.electricitysplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthDto.LoginResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .avatar(request.getAvatar())
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs())
                .build();
    }

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .orElseGet(() -> userRepository.findByEmail(request.getUsernameOrEmail())
                        .orElseThrow(() -> new BusinessException("用户名或密码错误")));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs())
                .build();
    }

    public AuthDto.UserResponse getCurrentUser(User user) {
        User foundUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return toUserResponse(foundUser);
    }

    @Transactional
    public AuthDto.UserResponse updateCurrentUser(User user, AuthDto.UpdateUserRequest request) {
        User foundUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        if (request.getAvatar() != null) {
            foundUser.setAvatar(request.getAvatar());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            foundUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User savedUser = userRepository.save(foundUser);
        return toUserResponse(savedUser);
    }

    private AuthDto.UserResponse toUserResponse(User user) {
        return AuthDto.UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
