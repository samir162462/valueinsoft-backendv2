package com.example.valueinsoftbackend.Service.finance;

import java.util.Locale;
import java.util.Set;

/** Single authority for exact, auditable payment-type normalization. */
public final class PaymentTypeClassifier {

    private static final Set<String> CASH = Set.of(
            "cash", "direct", "dirict", "cash payment", "direct payment", "مباشر");
    private static final Set<String> CARD = Set.of(
            "card", "visa", "master", "mastercard", "visa card", "master card", "card payment");
    private static final Set<String> WALLET = Set.of(
            "wallet", "mobile_wallet", "mobile wallet", "instapay", "vodafone", "vodafone cash");
    private static final Set<String> RECEIVABLE = Set.of(
            "credit", "later", "debt", "receivable", "on account", "on_account");

    private PaymentTypeClassifier() {
    }

    public static Classification classify(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (CASH.contains(raw)) return new Classification(Category.CASH, raw);
        if (CARD.contains(raw)) return new Classification(Category.CARD, raw);
        if (WALLET.contains(raw)) return new Classification(Category.WALLET, raw);
        if (RECEIVABLE.contains(raw)) return new Classification(Category.RECEIVABLE, raw);
        return new Classification(Category.OTHER, raw);
    }

    public enum Category { CASH, CARD, WALLET, RECEIVABLE, OTHER }

    public record Classification(Category category, String raw) {
        public String mappingKey() {
            return switch (category) {
                case CASH -> "cash";
                case CARD -> "card";
                case WALLET -> "wallet";
                case RECEIVABLE -> "receivable";
                case OTHER -> raw;
            };
        }
    }
}
