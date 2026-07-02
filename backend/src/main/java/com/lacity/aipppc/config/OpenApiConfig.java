package com.lacity.aipppc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI descriptor for the integration API. Appendix 3 §2.1.2 requires the API
 * to be "documented and versioned"; springdoc publishes the live contract at
 * {@code /swagger-ui.html} and {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aipPpcOpenApi() {
        return new OpenAPI().info(new Info()
            .title("AI-Powered Pre-Plan Check Assistant API")
            .description("City of Los Angeles / LADBS — RFP 2025AIP007. Advisory pre-plan-check "
                + "screening: completeness validation, rule-based + AI-assisted pre-screening, and "
                + "departmental clearance identification. Results are advisory only and do not "
                + "constitute Formal Plan Check approval (SOW 2.2.9).")
            .version("v1")
            .contact(new Contact().name("LADBS Development Services").email("info@lacity.gov"))
            .license(new License().name("City of Los Angeles")));
    }
}
