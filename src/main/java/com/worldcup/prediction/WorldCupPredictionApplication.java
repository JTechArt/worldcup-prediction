package com.worldcup.prediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorldCupPredictionApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorldCupPredictionApplication.class, args);
    }
}
