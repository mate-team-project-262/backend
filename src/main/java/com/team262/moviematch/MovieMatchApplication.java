package com.team262.moviematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MovieMatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(MovieMatchApplication.class, args);
    }
}
