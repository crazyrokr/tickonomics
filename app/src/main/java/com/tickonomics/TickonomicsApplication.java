package com.tickonomics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableResilientMethods(proxyTargetClass = true)
public class TickonomicsApplication {
  static void main(String[] args) {
    SpringApplication.run(TickonomicsApplication.class, args);
  }
}
