package org.example.sep26management.infrastructure.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        // Log để dễ debug khi credentials bị thiếu
        if (cloudName == null || cloudName.isBlank()) {
            log.error("CLOUDINARY_CLOUD_NAME is not set! Avatar upload will fail.");
        } else {
            log.info("Cloudinary configured: cloud_name={}, api_key={}***",
                    cloudName,
                    apiKey != null && apiKey.length() > 6 ? apiKey.substring(0, 6) : "(empty)");
        }

        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }
}