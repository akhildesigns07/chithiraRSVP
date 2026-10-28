package com.chithira.rsvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RsvpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RsvpApplication.class, args);
    }
}
