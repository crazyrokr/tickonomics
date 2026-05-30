package com.tickonomics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TickonomicsApplication {
  static void main(String[] args) {
    SpringApplication.run(TickonomicsApplication.class, args);
  }
}
