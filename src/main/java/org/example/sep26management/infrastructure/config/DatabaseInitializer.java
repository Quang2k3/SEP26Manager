package org.example.sep26management.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initializeDatabase() {
        return args -> {
            log.info("=================================================================");
            log.info(" INITIALIZING DATABASE...");
            log.info("=================================================================");

            // Check if admin exists
            if (userRepository.findByEmail("admin@warehouse.com").isEmpty()) {
                createAdminUser();
            } else {
                log.info("Admin user already exists");
            }

            log.info("=================================================================");
            log.info("DATABASE INITIALIZATION COMPLETED!");
            log.info("=================================================================");
        };
    }

    private void createAdminUser() {
        log.info("Creating default admin user...");

        // Password: 1
        String rawPassword = "1";
        String hashedPassword = passwordEncoder.encode(rawPassword);

        UserEntity admin = UserEntity.builder()
                .email("admin@warehouse.com")
                .passwordHash(hashedPassword)
                .fullName("System Administrator")
                .phone("0123456789")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .isFirstLogin(false)
                .isPermanent(true)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(admin);

        // Verify password works
        boolean passwordWorks = passwordEncoder.matches(rawPassword, hashedPassword);
        if (passwordWorks) {
            log.info("Password verification: SUCCESS");
        } else {
            log.error("Password verification: FAILED");
        }
    }


}
