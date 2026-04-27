package com.tickonomics.cdm.adapter;

/**
 * Maps raw source-specific data to CDM-typed objects at the ingestion boundary.
 * The computation engine consumes only CDM-typed data regardless of source.
 *
 * @param <T> raw source-specific type (e.g., FredObservation, NyFedRateResponse)
 * @param <R> CDM output type (e.g., CdmRateSnapshot, CdmTick)
 */
@FunctionalInterface
public interface CdmAdapter<T, R> {
    R toCdm(T rawData);
}
