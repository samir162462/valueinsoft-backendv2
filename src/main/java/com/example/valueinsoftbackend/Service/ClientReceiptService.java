package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMClientReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.CreateClientReceiptRequest;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;

@Service
public class ClientReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ClientReceiptService.class);

    private final DBMClientReceipt clientReceiptRepository;

    public ClientReceiptService(DBMClientReceipt clientReceiptRepository) {
        this.clientReceiptRepository = clientReceiptRepository;
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
    public String addClientReceipt(int companyId, CreateClientReceiptRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (request.getAmount().signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_RECEIPT_INVALID_AMOUNT", "amount must not be zero");
        }

        ClientReceipt clientReceipt = new ClientReceipt(
                0,
                request.getType().trim(),
                request.getAmount(),
                new Timestamp(System.currentTimeMillis()),
                request.getUserName().trim(),
                request.getClientId(),
                request.getBranchId()
        );

        int rows = clientReceiptRepository.insertClientReceipt(companyId, clientReceipt);
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_RECEIPT_INSERT_FAILED", "the ReceiptUser not added -> error in server!");
        }

        log.info("Recorded client receipt for company {} branch {} client {}", companyId, request.getBranchId(), request.getClientId());
        return "the Client Receipt Added Successfully : " + request.getClientId();
    }
}
