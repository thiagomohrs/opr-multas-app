package com.opr.multas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MultasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultasApplication.class, args);
    }
}

