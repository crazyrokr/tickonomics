package com.tickonomics.cdm.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tickonomics.cdm.adapter.raw.YahooOptionContract;
import com.tickonomics.cdm.enums.OptionType;
import com.tickonomics.cdm.model.CdmOptionSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class YahooOptionsCdmAdapterTest {

  private static YahooOptionContract contract(String optionType) {
    return new YahooOptionContract(
        Instant.parse("2026-06-14T10:00:00Z"),
        "AAPL",
        new BigDecimal("150"),
        LocalDate.now().plusDays(30),
        optionType,
        1.0,
        1.5,
        1.25,
        0.2,
        0.5,
        0.01,
        -0.05,
        0.1,
        0.02,
        1000L,
        150.0);
  }

  private final YahooOptionsCdmAdapter adapter = new YahooOptionsCdmAdapter();

  @Nested
  class OptionTypeMapping {

    @Test
    void givenCallToken_whenMapped_thenCall() {
      var snapshot = adapter.toCdm(contract("CALL"));
      assertEquals(OptionType.CALL, snapshot.type());
    }

    @Test
    void givenPutToken_whenMapped_thenPut() {
      var snapshot = adapter.toCdm(contract("PUT"));
      assertEquals(OptionType.PUT, snapshot.type());
    }

    @Test
    void givenLowercaseToken_whenMapped_thenNormalized() {
      var call = adapter.toCdm(contract("call"));
      var put = adapter.toCdm(contract("put"));

      assertEquals(OptionType.CALL, call.type());
      assertEquals(OptionType.PUT, put.type());
    }

    @Test
    void givenUnknownToken_whenMapped_thenThrows() {
      var ex = assertThrows(IllegalArgumentException.class, () -> adapter.toCdm(contract("JUNK")));

      assertEquals("Unknown optionType: JUNK", ex.getMessage());
    }

    @Test
    void givenBlankToken_whenMapped_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> adapter.toCdm(contract("   ")));
    }
  }
}
