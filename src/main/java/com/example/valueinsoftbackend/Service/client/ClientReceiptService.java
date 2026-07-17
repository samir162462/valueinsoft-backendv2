package com.example.valueinsoftbackend.Service.client;

import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.openitems.ArOpenItemService;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMClientReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.CreateClientReceiptRequest;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Set;

@Service
@Slf4j
public class ClientReceiptService {

    private static final Set<String> FINANCE_PAYMENT_TYPES = Set.of(
            "payment",
            "paidin",
            "paid_in",
            "receivevmoney",
            "receivemoney",
            "receipt",
            "collection",
            "clientpayout");

    private final DBMClientReceipt clientReceiptRepository;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final ArOpenItemService arOpenItemService;

    public ClientReceiptService(DBMClientReceipt clientReceiptRepository,
                                FinanceOperationalPostingService financeOperationalPostingService,
                                ArOpenItemService arOpenItemService) {
        this.clientReceiptRepository = clientReceiptRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.arOpenItemService = arOpenItemService;
    }

    public ArrayList<ClientReceipt> getClientReceipts(int companyId, int clientId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        return clientReceiptRepository.getClientReceipts(companyId, clientId);
    }

    public ArrayList<ClientReceipt> getClientReceiptsByTime(int companyId, int branchId, String startTime, String endTime) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        Timestamp start = RequestTimestampParser.parse(startTime, "startTime");
        Timestamp end = RequestTimestampParser.parse(endTime, "endTime");
        return clientReceiptRepository.getClientReceiptsByTime(companyId, branchId, start, end);
    }

    @Transactional
    public ClientReceipt addClientReceipt(int companyId, CreateClientReceiptRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (request.getAmount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_RECEIPT_INVALID_AMOUNT",
                    "New client receipts and payouts must use a positive amount");
        }

        Timestamp receiptTime = new Timestamp(System.currentTimeMillis());
        ClientReceipt clientReceipt = new ClientReceipt(
                0,
                request.getType().trim(),
                request.getAmount(),
                receiptTime,
                request.getUserName().trim(),
                request.getClientId(),
                request.getBranchId()
        );

        ClientReceipt created = clientReceiptRepository.createClientReceipt(companyId, clientReceipt);
        if (created == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_RECEIPT_INSERT_FAILED", "the ReceiptUser not added -> error in server!");
        }
        if (!"clientpayout".equals(normalizeReceiptType(request.getType()))) {
            String currency = request.getCurrencyCode() == null || request.getCurrencyCode().isBlank()
                    ? arOpenItemService.companyCurrency(companyId)
                    : request.getCurrencyCode().trim().toUpperCase();
            arOpenItemService.allocateReceipt(companyId, request.getBranchId(), request.getClientId(), created.getCrId(),
                    new OpenItemsWriteModels.AllocationCommand(
                            currency, request.getIdempotencyKey(), request.getAllocations()),
                    request.getUserName().trim());
        }
        enqueueFinanceClientReceipt(companyId, created);

        log.info("Recorded client receipt for company {} branch {} client {}", companyId, request.getBranchId(), request.getClientId());
        return created;
    }

    /**
     * Records the cash received during a POS credit/part-payment checkout and
     * applies it to that checkout's own open item. Keeping the receipt and
     * allocation separate preserves the normal append-only AR audit trail.
     */
    @Transactional
    public ClientReceipt recordPosCheckoutPayment(int companyId,
                                                   int branchId,
                                                   int clientId,
                                                   long openItemId,
                                                   java.math.BigDecimal amount,
                                                   String orderIdempotencyKey,
                                                   String actor) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POS_CHECKOUT_PAYMENT_INVALID",
                    "POS checkout payment must be greater than zero");
        }

        ClientReceipt created = clientReceiptRepository.createClientReceipt(companyId,
                new ClientReceipt(0, "Payment", amount, new Timestamp(System.currentTimeMillis()),
                        actor == null || actor.isBlank() ? "POS" : actor.trim(), clientId, branchId));
        if (created == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_RECEIPT_INSERT_FAILED",
                    "POS checkout receipt could not be recorded");
        }

        arOpenItemService.allocateReceipt(companyId, branchId, clientId, created.getCrId(),
                new OpenItemsWriteModels.AllocationCommand(
                        arOpenItemService.companyCurrency(companyId),
                        "pos-checkout-payment:" + (orderIdempotencyKey == null ? openItemId : orderIdempotencyKey),
                        java.util.List.of(new OpenItemsWriteModels.AllocationTarget(openItemId, amount))),
                created.getUserName());
        enqueueFinanceClientReceipt(companyId, created);
        return created;
    }

    private void enqueueFinanceClientReceipt(int companyId, ClientReceipt receipt) {
        if (receipt == null || receipt.getAmount() == null || receipt.getAmount().signum() <= 0) {
            return;
        }
        if (!FINANCE_PAYMENT_TYPES.contains(normalizeReceiptType(receipt.getType()))) {
            return;
        }

        try {
            financeOperationalPostingService.enqueueClientReceipt(companyId, receipt);
        } catch (RuntimeException exception) {
            log.warn(
                    "Client receipt {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                    receipt.getCrId(),
                    companyId,
                    receipt.getBranchId(),
                    exception.getMessage()
            );
        }
    }

    private String normalizeReceiptType(String type) {
        if (type == null) {
            return "";
        }
        return type.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}
