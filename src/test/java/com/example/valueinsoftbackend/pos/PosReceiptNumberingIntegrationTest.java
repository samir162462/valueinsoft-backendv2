package com.example.valueinsoftbackend.pos;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Model.Response.CreateOrderResult;
import com.example.valueinsoftbackend.Service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PosReceiptNumberingIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:15-alpine"
    );

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testFirstReceiptGeneration() {
        // Setup schema
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS c_9999");
        jdbcTemplate.execute("CREATE TABLE c_9999.pos_receipt_sequences (branch_id BIGINT NOT NULL, period_yymm CHAR(4) NOT NULL, last_sequence_no INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (branch_id, period_yymm), CHECK (last_sequence_no BETWEEN 0 AND 999999))");
        jdbcTemplate.execute("CREATE TABLE c_9999.\"PosOrder_1082\" (\"orderId\" SERIAL PRIMARY KEY, \"orderTime\" TIMESTAMP, \"clientName\" VARCHAR, \"orderType\" VARCHAR, \"orderDiscount\" INTEGER, \"orderTotal\" INTEGER, \"salesUser\" VARCHAR, \"clientId\" INTEGER, \"orderIncome\" INTEGER, \"orderBouncedBack\" INTEGER, \"shift_id\" INTEGER, receipt_number VARCHAR(14), idempotency_key VARCHAR(255))");
        
        CreateOrderRequest request = new CreateOrderRequest(0, null, "Test", "Direct", 0, 100, "user", 1082, 1, 100, Collections.emptyList(), null, null, null, null, null, UUID.randomUUID().toString());
        
        CreateOrderResult result = orderService.createOrder(request, 9999);
        assertNotNull(result);
        assertFalse(result.idempotencyHit());
        assertNotNull(result.receiptNumber());
        assertTrue(result.receiptNumber().contains("1082000001"));
    }
}
