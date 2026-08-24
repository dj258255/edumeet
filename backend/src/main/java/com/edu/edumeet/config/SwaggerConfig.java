package com.edu.edumeet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(apiInfo());
    }

    private Info apiInfo(){
        return new Info()
                .title("Edumeet API")
                .description("Edumeet 교육 플랫폼의 REST API 명세서 - 과제 관리, 제출물 관리, 게시판 등의 기능을 제공합니다.")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Edumeet Development Team")
                        .email("developer@edumeet.com"));
    }
}
