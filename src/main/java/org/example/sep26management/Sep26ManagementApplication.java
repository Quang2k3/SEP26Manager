package org.example.sep26management;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SEP26 Warehouse Management System
 * Main application entry point
 */
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
}