package com.tickonomics.ingestion.config;

import com.tickonomics.cdm.adapter.AlphaVantageCdmAdapter;
import com.tickonomics.cdm.adapter.FinnhubEquityCdmAdapter;
import com.tickonomics.cdm.adapter.FredCdmAdapter;
import com.tickonomics.cdm.adapter.FrenchFactorCdmAdapter;
import com.tickonomics.cdm.adapter.NewsArticleCdmAdapter;
import com.tickonomics.cdm.adapter.NyFedCdmAdapter;
import com.tickonomics.cdm.adapter.YahooEquityCdmAdapter;
import com.tickonomics.cdm.adapter.YahooOptionsCdmAdapter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build();
  }

  // Stateless CDM (Canonical Data Model) adapters that map vendor-specific raw
  // payloads into the CDM. The cdm module is intentionally Spring-free, so the
  // adapters required as constructor-injected dependencies are wired here.

  @Bean
  public FredCdmAdapter fredCdmAdapter() {
    return new FredCdmAdapter();
  }

  @Bean
  public NyFedCdmAdapter nyFedCdmAdapter() {
    return new NyFedCdmAdapter();
  }

  @Bean
  public AlphaVantageCdmAdapter alphaVantageCdmAdapter() {
    return new AlphaVantageCdmAdapter();
  }

  @Bean
  public YahooEquityCdmAdapter yahooEquityCdmAdapter() {
    return new YahooEquityCdmAdapter();
  }

  @Bean
  public FinnhubEquityCdmAdapter finnhubEquityCdmAdapter() {
    return new FinnhubEquityCdmAdapter();
  }

  @Bean
  public FrenchFactorCdmAdapter frenchFactorCdmAdapter() {
    return new FrenchFactorCdmAdapter();
  }

  @Bean
  public NewsArticleCdmAdapter newsArticleCdmAdapter() {
    return new NewsArticleCdmAdapter();
  }

  @Bean
  public YahooOptionsCdmAdapter yahooOptionsCdmAdapter() {
    return new YahooOptionsCdmAdapter();
  }
}
