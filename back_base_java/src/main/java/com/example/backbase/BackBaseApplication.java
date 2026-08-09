package com.example.backbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackBaseApplication.class, args);
    }
}