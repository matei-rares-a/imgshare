package com.example.backbase.config;



import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CORSConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // registry.addMapping("/**")
                //         .allowedOrigins("http://localhost:5173")
                //         .allowedMethods("*")
                //         .allowedHeaders("*");
             registry.addMapping("/**")   // allow CORS for all paths
                .allowedOrigins("*") // allow requests from any origin
                .allowedMethods("*") // allow all HTTP methods
                .allowedHeaders("*"); // allow all headers
            }
        };
    }
}