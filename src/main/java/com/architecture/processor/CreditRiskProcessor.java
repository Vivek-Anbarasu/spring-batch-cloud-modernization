package com.architecture.processor;

import com.architecture.domain.CreditRecord;
import com.architecture.domain.RiskEvaluation;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class CreditRiskProcessor implements ItemProcessor<CreditRecord, RiskEvaluation> {

    @Override
    public RiskEvaluation process(CreditRecord item) throws Exception {
        if (item.debtAmount() < 0) {
            throw new IllegalArgumentException("Negative balances are invalid data anomalies. Skipping row.");
        }

        double ratio = item.getDebtToAssetRatio();

        String tier = switch (Double.valueOf(ratio)) {
            case Double r when r >= 0.7 -> "HIGH_RISK";
            case Double r when r >= 0.4 -> "MEDIUM_RISK";
            case Double r when r >= 0.0 -> "LOW_RISK";
            default -> "UNCLASSIFIED_TIER";
        };


        String status = "HIGH_RISK".equals(tier) ? "REQUIRES_MANUAL_REVIEW" : "AUTO_APPROVED";

        return new RiskEvaluation(item.accountId(), item.companyName(), tier, status);
    }
}
