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

import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;

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
     *
     * Only runs in dev profile
     */
    @Bean
    @Profile("dev")
    public CommandLineRunner initDemoData(
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("⏭️ Skipping demo data initialization - will be updated for dynamic RBAC");
            // TODO: Recreate demo data with roles from database after migrating to dynamic
            // RBAC
            /*
             * Previously created users with UserRole enum:
             * - admin@warehouse.com (MANAGER)
             * - manager@test.com (MANAGER)
             * - accountant@test.com (ACCOUNTANT)
             * - keeper@test.com (KEEPER)
             * - firstlogin@test.com (KEEPER, PENDING_VERIFICATION)
             * - inactive@test.com (KEEPER, INACTIVE)
             *
             * New approach: Load roles from database and assign to users
             */
        };
    }
}