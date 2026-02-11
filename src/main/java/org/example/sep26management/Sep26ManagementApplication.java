package org.example.sep26management;

import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
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

        log.info(LogMessages.APP_SEPARATOR);
        log.info(LogMessages.APP_STARTED_SUCCESS);
        log.info(LogMessages.APP_SEPARATOR);
        log.info(LogMessages.APP_SWAGGER_UI);
        log.info(LogMessages.APP_DEFAULT_LOGIN);
        log.info(LogMessages.APP_DEFAULT_EMAIL);
        log.info(LogMessages.APP_DEFAULT_PASSWORD);
        log.info(LogMessages.APP_SEPARATOR);
    }
}