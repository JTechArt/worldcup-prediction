package com.worldcup.prediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class WorldCupPredictionApplication {

    public static void main(String[] args) {
        String tz = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Yerevan");
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(tz)));
        SpringApplication.run(WorldCupPredictionApplication.class, args);
    }
}
