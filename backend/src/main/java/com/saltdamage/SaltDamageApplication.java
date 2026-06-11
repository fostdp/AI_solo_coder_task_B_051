package com.saltdamage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SaltDamageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaltDamageApplication.class, args);
    }
}
