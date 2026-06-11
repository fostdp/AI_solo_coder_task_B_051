package com.saltdamage.transport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.saltdamage")
public class SalinityTransportApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalinityTransportApplication.class, args);
    }
}
