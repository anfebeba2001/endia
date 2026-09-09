package com.endia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EndiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EndiaApplication.class, args);
    }
}
