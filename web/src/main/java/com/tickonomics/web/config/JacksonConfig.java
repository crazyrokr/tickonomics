package com.tickonomics.web.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;

/**
 * Ensures {@link java.math.BigDecimal} values serialize as plain JSON numbers
 * (e.g., {@code "price": 520.50}) rather than scientific notation or object form.
 * See ADR-033.
 */
@Configuration
public class JacksonConfig {

  @Bean
  public JsonMapperBuilderCustomizer bigDecimalPlainCustomizer() {
    return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
  }
}
