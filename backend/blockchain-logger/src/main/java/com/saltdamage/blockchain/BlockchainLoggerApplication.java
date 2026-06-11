package com.saltdamage.blockchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BlockchainLoggerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlockchainLoggerApplication.class, args);
    }
}
