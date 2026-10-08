package com.architecture.domain;

public record CreditRecord(String accountId, String companyName, double debtAmount, double assetValue) {
    public double getDebtToAssetRatio() {
        if (assetValue <= 0) return 1.0;
        return debtAmount / assetValue;
    }
}
