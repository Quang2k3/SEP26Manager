package org.example.sep26management;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;

import java.time.LocalDateTime;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.example.sep26management.infrastructure.persistence.repository")
@EnableJpaAuditing
@EnableAsync
@Slf4j
public class Sep26ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sep26ManagementApplication.class, args);

        log.info("=================================================================");
        log.info(" WAREHOUSE MANAGEMENT SYSTEM STARTED SUCCESSFULLY!");
        log.info("=================================================================");
        log.info(" Swagger UI: http://localhost:8080/api/swagger-ui/index.html");
        log.info(" Default Login:");
        log.info("   Email: admin@warehouse.com");
        log.info("   Password: Admin@123");
        log.info("=================================================================");
    }

    /**
     * Initialize demo data for testing
     * Only runs in dev profile
     */
    @Bean
    @Profile("dev")
    public CommandLineRunner initDemoData(
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            log.info(" Initializing demo data...");

            // Check if admin exists
            if (userRepository.findByEmail("admin@warehouse.com").isEmpty()) {
                log.info("📝 Creating default admin user...");

                UserEntity admin = UserEntity.builder()
                        .email("admin@warehouse.com")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
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
                log.info(" Admin user created successfully!");
            }

            // Create demo users for testing
            if (userRepository.findByEmail("manager@test.com").isEmpty()) {
                log.info(" Creating demo Manager user...");

                UserEntity manager = UserEntity.builder()
                        .email("manager@test.com")
                        .passwordHash(passwordEncoder.encode("Manager@123"))
                        .fullName("Test Manager")
                        .phone("0123456781")
                        .role(UserRole.MANAGER)
                        .status(UserStatus.ACTIVE)
                        .isFirstLogin(false)
                        .isPermanent(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(manager);
                log.info(" Demo Manager created!");
            }

            if (userRepository.findByEmail("accountant@test.com").isEmpty()) {
                log.info(" Creating demo Accountant user...");

                UserEntity accountant = UserEntity.builder()
                        .email("accountant@test.com")
                        .passwordHash(passwordEncoder.encode("Accountant@123"))
                        .fullName("Test Accountant")
                        .phone("0123456782")
                        .role(UserRole.ACCOUNTANT)
                        .status(UserStatus.ACTIVE)
                        .isFirstLogin(false)
                        .isPermanent(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(accountant);
                log.info("Demo Accountant created!");
            }

            if (userRepository.findByEmail("keeper@test.com").isEmpty()) {
                log.info("Creating demo Keeper user...");

                UserEntity keeper = UserEntity.builder()
                        .email("keeper@test.com")
                        .passwordHash(passwordEncoder.encode("Keeper@123"))
                        .fullName("Test Keeper")
                        .phone("0123456783")
                        .role(UserRole.KEEPER)
                        .status(UserStatus.ACTIVE)
                        .isFirstLogin(false)
                        .isPermanent(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(keeper);
                log.info("Demo Keeper created!");
            }

            // Create a first login user for testing
            if (userRepository.findByEmail("firstlogin@test.com").isEmpty()) {
                log.info("Creating first login test user...");

                UserEntity firstLogin = UserEntity.builder()
                        .email("firstlogin@test.com")
                        .passwordHash(passwordEncoder.encode("FirstLogin@123"))
                        .fullName("First Login User")
                        .phone("0123456784")
                        .role(UserRole.KEEPER)
                        .status(UserStatus.PENDING_VERIFICATION)
                        .isFirstLogin(true)
                        .isPermanent(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(firstLogin);
                log.info("First login test user created!");
            }

            // Create an inactive user for testing
            if (userRepository.findByEmail("inactive@test.com").isEmpty()) {
                log.info("Creating inactive test user...");

                UserEntity inactive = UserEntity.builder()
                        .email("inactive@test.com")
                        .passwordHash(passwordEncoder.encode("Inactive@123"))
                        .fullName("Inactive User")
                        .phone("0123456785")
                        .role(UserRole.KEEPER)
                        .status(UserStatus.INACTIVE)
                        .isFirstLogin(false)
                        .isPermanent(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(inactive);
                log.info("Inactive test user created!");
            }

            log.info("=================================================================");
            log.info("Demo data initialization completed!");
            log.info("=================================================================");
            log.info("DEMO ACCOUNTS FOR TESTING:");
            log.info("=================================================================");
            log.info("1  ADMIN (Full Access)");
            log.info("   Email: admin@warehouse.com");
            log.info("   Password: Admin@123");
            log.info("-----------------------------------------------------------------");
            log.info("2  MANAGER (Management Access)");
            log.info("   Email: manager@test.com");
            log.info("   Password: Manager@123");
            log.info("-----------------------------------------------------------------");
            log.info("3  ACCOUNTANT (Accounting Access)");
            log.info("   Email: accountant@test.com");
            log.info("   Password: Accountant@123");
            log.info("-----------------------------------------------------------------");
            log.info("4 KEEPER (Warehouse Staff)");
            log.info("   Email: keeper@test.com");
            log.info("   Password: Keeper@123");
            log.info("-----------------------------------------------------------------");
            log.info("5  FIRST LOGIN USER (Needs OTP Verification)");
            log.info("   Email: firstlogin@test.com");
            log.info("   Password: FirstLogin@123");
            log.info("-----------------------------------------------------------------");
            log.info("6  INACTIVE USER (Disabled Account)");
            log.info("   Email: inactive@test.com");
            log.info("   Password: Inactive@123");
            log.info("=================================================================");
        };
    }
}