package org.example.sep26management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "org.example.sep26management")
@EnableJpaRepositories(basePackages = "org.example.sep26management.infrastructure.persistence.jpa")
public class Sep26ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sep26ManagementApplication.class, args);
    }

}
