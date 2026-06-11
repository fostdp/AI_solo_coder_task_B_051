package com.saltdamage.crystal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.saltdamage")
public class CrystalPressureApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrystalPressureApplication.class, args);
    }
}
