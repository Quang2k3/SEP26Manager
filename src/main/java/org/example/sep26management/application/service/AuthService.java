package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.LoginRequest;
import org.example.sep26management.application.dto.LoginResponse;
import org.example.sep26management.application.dto.RegisterRequest;
import org.example.sep26management.application.dto.UserDto;
import org.example.sep26management.application.mapper.UserMapper;
import org.example.sep26management.application.port.AuthUseCase;
import org.example.sep26management.domain.entity.User;
import org.example.sep26management.domain.exception.InvalidCredentialsException;
import org.example.sep26management.domain.exception.DomainException;
import org.example.sep26management.domain.repository.UserRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.example.sep26management.infrastructure.security.PasswordEncoderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements AuthUseCase {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoderService passwordEncoder;
    
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        
        if (!user.getEnabled()) {
            throw new DomainException("Account is disabled");
        }
        
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .user(userMapper.toDto(user))
                .build();
    }
    
    @Override
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DomainException("Username already exists");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DomainException("Email already exists");
        }
        
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encodedPassword)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(Boolean.valueOf(true))
                .role("USER")
                .build();
        
        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        if (!jwtTokenProvider.validateToken(token)) {
            throw new InvalidCredentialsException("Invalid token");
        }
        
        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        
        return userMapper.toDto(user);
    }
}

