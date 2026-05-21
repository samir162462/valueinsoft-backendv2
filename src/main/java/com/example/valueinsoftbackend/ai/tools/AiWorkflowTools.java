package com.example.valueinsoftbackend.ai.tools;

import org.springframework.stereotype.Component;

@Component
public class AiWorkflowTools {

    public String getStepsToCreateInvoice() {
        return """
                **Steps to Create an Invoice / Sale:**
                1. Navigate to the **Point of Sale (POS)** screen (or ask me: *"Open POS"*).
                2. Search for products by name or barcode, or click on product tiles.
                3. The selected products will appear in the cart on the right.
                4. Select an optional customer from the dropdown to record customer balance or loyalty.
                5. Click the **Pay** button at the bottom of the cart.
                6. Choose the payment method (Cash, Card, Wallet, or Credit for unpaid customer balance).
                7. Enter the received amount and click **Submit Payment** to finalize and print the receipt.
                """;
    }

    public String getStepsToAddProduct() {
        return """
                **Steps to Add a Product to Inventory:**
                1. Navigate to the **Inventory / Products** screen.
                2. Click the **Add Product** button in the top right.
                3. Enter the required product details:
                   - **Product Name** (must be descriptive)
                   - **Barcode** (scan or auto-generate)
                   - **Unit Price** (selling price)
                   - **Purchase Price** (cost price for profit calculations)
                   - **Current Stock Level** (opening stock)
                   - **Category** and **Supplier**
                4. Set the **Low Stock Warning threshold** so I can alert you when stock levels run low.
                5. Click **Save Product** to add it to your active inventory list.
                """;
    }

    public String getStepsToOpenShift() {
        return """
                **Steps to Open a Shift (POS):**
                1. Click the **POS / Shift** button or navigate to the POS screen.
                2. If no shift is open, you will be prompted to open one.
                3. Enter the **Opening Safe Balance** (amount of cash in drawer).
                4. Click **Open Shift** to begin selling.
                5. Once open, cashiers can process receipts.
                """;
    }

    public String getStepsToRunPayroll() {
        return """
                **Steps to Run Payroll:**
                1. Navigate to the **HR / Payroll Management** screen.
                2. Select the current payroll month and year.
                3. Review calculated salary adjustments:
                   - **Basic Salary**
                   - **Overtime & Allowances**
                   - **Deductions & Unexcused Absences**
                4. Click **Calculate Payroll** to generate pay sheets.
                5. Approve and finalize payroll to post transaction balances and mark payroll as processed.
                """;
    }
}
