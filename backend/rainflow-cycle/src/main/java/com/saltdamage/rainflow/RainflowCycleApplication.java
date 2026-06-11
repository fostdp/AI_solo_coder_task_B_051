package com.saltdamage.rainflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class RainflowCycleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RainflowCycleApplication.class, args);
    }
}
