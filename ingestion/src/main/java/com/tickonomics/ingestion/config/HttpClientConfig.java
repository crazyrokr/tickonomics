package com.tickonomics.ingestion.config;

import com.tickonomics.cdm.adapter.FredCdmAdapter;
import com.tickonomics.cdm.adapter.NyFedCdmAdapter;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }

  @Bean
  public FredCdmAdapter fredCdmAdapter() {
    return new FredCdmAdapter();
  }

  @Bean
  public NyFedCdmAdapter nyFedCdmAdapter() {
    return new NyFedCdmAdapter();
  }
}
