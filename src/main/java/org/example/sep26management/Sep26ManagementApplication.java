package org.example.sep26management;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.example.sep26management.infrastructure.persistence.repository")
@EntityScan(basePackages = "org.example.sep26management.infrastructure.persistence.entity")
@EnableAsync
@Slf4j

public class Sep26ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sep26ManagementApplication.class, args);

        log.info("=================================================================");
        log.info("APPLICATION STARTED SUCCESSFULLY!");
        log.info("=================================================================");
        log.info(" Health Check: http://localhost:8080/api/v1/health");
        log.info(" Swagger UI: http://localhost:8080/api/swagger-ui/index.html");
        log.info("=================================================================");
    }
}