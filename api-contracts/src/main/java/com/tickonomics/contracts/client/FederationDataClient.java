package com.tickonomics.contracts.client;

import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.adapter.raw.NyFedRateResponse;

import java.util.List;

public interface FederationDataClient {

    List<FredObservation> fetchFredSeries(String seriesId, String apiKey);

    List<NyFedRateResponse> fetchNyFedRates(String rateType);

    boolean isHealthy();
}
