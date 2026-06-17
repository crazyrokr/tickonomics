package com.tickonomics.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@DisplayName("KeyHolderUtils")
class KeyHolderUtilsTest {

  @Nested
  @DisplayName("extractGeneratedLong")
  class ExtractGeneratedLong {

    @Nested
    @DisplayName("Given a KeyHolder with a valid key")
    class ValidKey {

      @Test
      @DisplayName("When key is a Long, then return it directly")
      void whenKeyIsLong_thenReturnDirectly() {
        var keyHolder = new GeneratedKeyHolder();
        keyHolder.getKeyList().add(java.util.Map.of("id", 42L));

        long result = KeyHolderUtils.extractGeneratedLong(keyHolder);

        assertThat(result).isEqualTo(42L);
      }

      @Test
      @DisplayName("When key is a BigInteger, then return its long value")
      void whenKeyIsBigInteger_thenReturnLongValue() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKey()).thenReturn(BigInteger.valueOf(123));

        long result = KeyHolderUtils.extractGeneratedLong(keyHolder);

        assertThat(result).isEqualTo(123L);
      }

      @Test
      @DisplayName("When key is an Integer, then return its long value")
      void whenKeyIsInteger_thenReturnLongValue() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKey()).thenReturn(Integer.valueOf(7));

        long result = KeyHolderUtils.extractGeneratedLong(keyHolder);

        assertThat(result).isEqualTo(7L);
      }
    }

    @Nested
    @DisplayName("Given a KeyHolder with a null key")
    class NullKey {

      @Test
      @DisplayName("When getKey returns null, then throw IllegalStateException")
      void whenGetKeyReturnsNull_thenThrowIllegalStateException() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKey()).thenReturn(null);

        assertThatThrownBy(() -> KeyHolderUtils.extractGeneratedLong(keyHolder))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expected a generated key");
      }
    }

    @Nested
    @DisplayName("Given an empty KeyHolder")
    class EmptyKeyHolder {

      @Test
      @DisplayName("When no keys were generated, then throw IllegalStateException")
      void whenNoKeysGenerated_thenThrowIllegalStateException() {
        var keyHolder = new GeneratedKeyHolder();

        assertThatThrownBy(() -> KeyHolderUtils.extractGeneratedLong(keyHolder))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expected a generated key");
      }
    }
  }
}
