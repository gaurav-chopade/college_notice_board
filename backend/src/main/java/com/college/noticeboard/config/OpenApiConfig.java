package com.college.noticeboard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI noticeBoardOpenApi() {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("College Online Notice Board API").version("1.0.0")
                        .description("Role-based notice board: targeted visibility, approvals, scheduling, Cloudinary attachments."))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().name(scheme).type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .tags(List.of(
                        new Tag().name("Authentication"), new Tag().name("Users"), new Tag().name("Departments"),
                        new Tag().name("Notices"), new Tag().name("Attachments"), new Tag().name("Notifications"),
                        new Tag().name("Categories"), new Tag().name("Dashboard"), new Tag().name("Audit Logs"),
                        new Tag().name("AI Assistant")));
    }
}
