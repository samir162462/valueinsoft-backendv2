package com.example.valueinsoftbackend.Model.Response;

import java.math.BigDecimal;
import java.util.List;

public class SupplierStatementResponse {

    private final int supplierId;
    private final String supplierName;
    private final String currency;
    private final BigDecimal openingBalance;
    private final BigDecimal debits;
    private final BigDecimal credits;
    private final BigDecimal closingBalance;
    private final List<SupplierStatementLineResponse> lines;

    public SupplierStatementResponse(int supplierId,
                                     String supplierName,
                                     String currency,
                                     BigDecimal openingBalance,
                                     BigDecimal debits,
                                     BigDecimal credits,
                                     BigDecimal closingBalance,
                                     List<SupplierStatementLineResponse> lines) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.currency = currency;
        this.openingBalance = openingBalance;
        this.debits = debits;
        this.credits = credits;
        this.closingBalance = closingBalance;
        this.lines = lines;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getDebits() {
        return debits;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public List<SupplierStatementLineResponse> getLines() {
        return lines;
    }
}
