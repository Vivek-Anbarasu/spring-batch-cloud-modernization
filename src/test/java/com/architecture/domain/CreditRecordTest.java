package com.architecture.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreditRecordTest {

    @Test
    @DisplayName("divides debt by assets")
    void computesTheRatio() {
        var record = new CreditRecord("ACC-1", "Entity", 350.0, 1_000.0);

        assertEquals(0.35, record.getDebtToAssetRatio(), 1e-9);
    }

    /**
     * Assets of zero would be an {@link ArithmeticException} on a plain division, and the ratio
     * would be undefined rather than infinite. Returning 1.0 routes the record into the highest
     * risk tier instead of failing the whole chunk.
     */
    @Test
    @DisplayName("treats zero assets as maximum leverage instead of dividing by zero")
    void guardsAgainstZeroAssets() {
        var record = new CreditRecord("ACC-2", "Entity", 10.0, 0.0);

        assertEquals(1.0, record.getDebtToAssetRatio(), 1e-9);
    }

    @Test
    @DisplayName("treats negative assets the same as zero")
    void guardsAgainstNegativeAssets() {
        var record = new CreditRecord("ACC-3", "Entity", 10.0, -1.0);

        assertEquals(1.0, record.getDebtToAssetRatio(), 1e-9);
    }

    @Test
    @DisplayName("a fully debt-free record has a ratio of zero")
    void handlesZeroDebt() {
        var record = new CreditRecord("ACC-4", "Entity", 0.0, 1_000.0);

        assertEquals(0.0, record.getDebtToAssetRatio(), 1e-9);
    }
}
