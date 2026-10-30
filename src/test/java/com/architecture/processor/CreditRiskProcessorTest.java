package com.architecture.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.architecture.domain.CreditRecord;
import com.architecture.domain.RiskEvaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreditRiskProcessorTest {

    private final CreditRiskProcessor processor = new CreditRiskProcessor();

    /**
     * The tier boundaries are inclusive on the lower edge, so 0.70 is HIGH and 0.6999 is MEDIUM.
     * Testing either side of each threshold is the only way to pin that down: an implementation
     * using {@code >} instead of {@code >=} passes every test that only probes the middle of a
     * band.
     */
    @ParameterizedTest(name = "debt {0} on assets {1} is {2}")
    @CsvSource({
            "700.0,  1000.0, HIGH_RISK",
            "699.99, 1000.0, MEDIUM_RISK",
            "400.0,  1000.0, MEDIUM_RISK",
            "399.99, 1000.0, LOW_RISK",
            "0.0,    1000.0, LOW_RISK",
            "5000.0, 1000.0, HIGH_RISK",
    })
    @DisplayName("classifies on the debt-to-asset ratio, boundaries included")
    void classifiesByRatio(double debt, double assets, String expectedTier) throws Exception {
        RiskEvaluation evaluation = processor.process(
                new CreditRecord("ACC-1", "Corporate Entity 1", debt, assets));

        assertEquals(expectedTier, evaluation.riskTier());
    }

    @Test
    @DisplayName("only high risk accounts are sent for manual review")
    void setsStatusFromTier() throws Exception {
        assertEquals("REQUIRES_MANUAL_REVIEW",
                processor.process(new CreditRecord("A", "B", 900.0, 1_000.0)).status());
        assertEquals("AUTO_APPROVED",
                processor.process(new CreditRecord("A", "B", 100.0, 1_000.0)).status());
    }

    /**
     * A record with no assets cannot be scored, and {@link CreditRecord} maps it to a ratio of
     * 1.0, which must land in the highest tier rather than falling through to a default.
     */
    @Test
    @DisplayName("an account with no assets is high risk")
    void treatsMissingAssetsAsHighRisk() throws Exception {
        RiskEvaluation evaluation = processor.process(new CreditRecord("A", "B", 10.0, 0.0));

        assertEquals("HIGH_RISK", evaluation.riskTier());
    }

    /**
     * This is the exception the step is configured to skip. It has to be an
     * {@link IllegalArgumentException} and nothing wider: the skip policy is scoped to that type
     * so that a genuine failure is not quietly swallowed.
     */
    @Test
    @DisplayName("rejects a negative balance with the type the step skips")
    void rejectsNegativeBalances() {
        var record = new CreditRecord("ACC-15000", "Corporate Entity 15000", -500.0, 5_000.0);

        assertThrows(IllegalArgumentException.class, () -> processor.process(record));
    }

    @Test
    @DisplayName("carries the account identity through to the evaluation")
    void preservesIdentity() throws Exception {
        RiskEvaluation evaluation = processor.process(
                new CreditRecord("ACC-42", "Corporate Entity 42", 1.0, 1_000.0));

        assertEquals("ACC-42", evaluation.accountId());
        assertEquals("Corporate Entity 42", evaluation.companyName());
    }
}
