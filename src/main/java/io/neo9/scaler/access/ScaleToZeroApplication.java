package io.neo9.scaler.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScaleToZeroApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScaleToZeroApplication.class, args);
    }

}
