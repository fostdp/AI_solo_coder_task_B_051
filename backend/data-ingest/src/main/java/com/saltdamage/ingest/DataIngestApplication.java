package com.saltdamage.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.saltdamage")
public class DataIngestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataIngestApplication.class, args);
    }
}
