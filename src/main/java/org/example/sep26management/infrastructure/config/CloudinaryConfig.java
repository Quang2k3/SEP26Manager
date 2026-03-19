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
        // Guard: nếu env var được inject là empty string (không phải null),
        // Spring KHÔNG trigger fallback trong @Value → dùng hardcoded fallback
        String resolvedCloudName   = (cloudName   == null || cloudName.isBlank())   ? "dzti1zycp"               : cloudName;
        String resolvedApiKey      = (apiKey      == null || apiKey.isBlank())      ? "796479776192876"          : apiKey;
        String resolvedApiSecret   = (apiSecret   == null || apiSecret.isBlank())   ? "ab7zzKgk4UcD7gYwPyzoZMK5vsM" : apiSecret;

        log.info("Cloudinary init: cloud_name='{}', api_key='{}***', api_secret_set={}",
                resolvedCloudName,
                resolvedApiKey.length() > 6 ? resolvedApiKey.substring(0, 6) : resolvedApiKey,
                !resolvedApiSecret.isBlank());

        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", resolvedCloudName,
                "api_key",    resolvedApiKey,
                "api_secret", resolvedApiSecret,
                "secure",     true
        ));
    }
}