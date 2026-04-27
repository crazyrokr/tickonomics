package com.tickonomics.computation.talib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BBandsResultTest {

    @Test
    void givenValidBands_whenConstruct_thenFieldsSet() {
        var result = new BBandsResult(
                new double[]{11, 10, 9},
                new double[]{10, 9, 8},
                new double[]{9, 8, 7},
                0, 3
        );
        assertArrayEquals(new double[]{11, 10, 9}, result.upper());
        assertArrayEquals(new double[]{10, 9, 8}, result.middle());
        assertArrayEquals(new double[]{9, 8, 7}, result.lower());
        assertEquals(0, result.begIdx());
        assertEquals(3, result.nbElement());
    }

    @Test
    void givenBandsWithOffset_whenValidMethodsCalled_thenReturnTruncatedArrays() {
        var result = new BBandsResult(
                new double[]{11, 10, 0, 0},
                new double[]{10, 9, 0, 0},
                new double[]{9, 8, 0, 0},
                2, 2
        );
        assertArrayEquals(new double[]{11, 10}, result.validUpper());
        assertArrayEquals(new double[]{10, 9}, result.validMiddle());
        assertArrayEquals(new double[]{9, 8}, result.validLower());
    }

    @Test
    void givenNullUpperBand_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new BBandsResult(null, new double[]{1}, new double[]{1}, 0, 1));
    }

    @Test
    void givenNullMiddleBand_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new BBandsResult(new double[]{1}, null, new double[]{1}, 0, 1));
    }

    @Test
    void givenNullLowerBand_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new BBandsResult(new double[]{1}, new double[]{1}, null, 0, 1));
    }

    @Test
    void givenNegativeNbElement_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BBandsResult(new double[]{}, new double[]{}, new double[]{}, 0, -1));
    }
}
