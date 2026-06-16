package com.tickonomics.persistence.config;

import javax.sql.DataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class TimescaleDbConfig {

  @Bean
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
    return configuration -> configuration.baselineOnMigrate(true);
  }
}
