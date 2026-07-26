package com.example.valueinsoftbackend.notification.producer;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Response.CreateOrderResult;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.AudienceMember;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPilotContext;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPilotIntegrationServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void lowStockIsResolvedAndPublishedOnlyAfterBusinessCommit() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationPilotProducer producer = mock(NotificationPilotProducer.class);
        DbNotificationPilotContext context = mock(DbNotificationPilotContext.class);
        DbBranchSettings settings = mock(DbBranchSettings.class);
        NotificationPilotIntegrationService service =
                new NotificationPilotIntegrationService(properties, producer, context, settings);

        OrderDetails detail = new OrderDetails(1, 1, "Phone", 1, 100, 100, 77, 0);
        Order order = new Order(0, new Timestamp(System.currentTimeMillis()), "Customer",
                "Direct", 0, 100, "cashier", 3, 0, 20, 0,
                new ArrayList<>(List.of(detail)));
        CreateOrderResult result = new CreateOrderResult(
                501, "R-501", false, 9, new Timestamp(System.currentTimeMillis()));
        when(settings.getEffectiveValueMap(12, 3))
                .thenReturn(Map.of("inventory.lowStockThreshold", 4));
        when(context.branchName(12, 3)).thenReturn("Downtown");
        when(context.lowStockProducts(12, 3, Set.of(77L), 4))
                .thenReturn(List.of(new DbNotificationPilotContext.LowStockProduct(
                        77L, "Phone", 3)));

        TransactionSynchronizationManager.initSynchronization();
        service.afterPosSale(12, order, result);

        verify(context, never()).lowStockProducts(12, 3, Set.of(77L), 4);
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(producer).lowStock(
                12, 3, 77L, "501:77", "Phone", 3, "Downtown");
    }

    @Test
    void disabledModuleDoesNotRegisterOrQueryAnything() {
        NotificationProperties properties = new NotificationProperties();
        NotificationPilotProducer producer = mock(NotificationPilotProducer.class);
        DbNotificationPilotContext context = mock(DbNotificationPilotContext.class);
        DbBranchSettings settings = mock(DbBranchSettings.class);
        NotificationPilotIntegrationService service =
                new NotificationPilotIntegrationService(properties, producer, context, settings);
        Order order = new Order(0, new Timestamp(System.currentTimeMillis()), "Customer",
                "Direct", 0, 100, "cashier", 3, 0, 20, 0,
                new ArrayList<>());

        TransactionSynchronizationManager.initSynchronization();
        service.afterPosSale(12, order,
                new CreateOrderResult(501, "R-501", false, 9,
                        new Timestamp(System.currentTimeMillis())));

        verify(context, never()).branchName(12, 3);
        verify(producer, never()).lowStock(
                12, 3, 77L, "501:77", "Phone", 3, "Downtown");
    }

    @Test
    void leaveManagerResolutionAndPublishBothRunOnlyAfterCommit() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationPilotProducer producer = mock(NotificationPilotProducer.class);
        NotificationAudienceResolver audience = mock(NotificationAudienceResolver.class);
        NotificationPilotIntegrationService service = new NotificationPilotIntegrationService(
                properties,
                producer,
                mock(DbNotificationPilotContext.class),
                mock(DbBranchSettings.class));
        service.configureLeaveAudience(audience);
        when(audience.fetchBatch(12, 3, "hr.leave.manage", 0, 1))
                .thenReturn(List.of(new AudienceMember(99, "en")));

        TransactionSynchronizationManager.initSynchronization();
        service.afterLeaveRequested(12, 3, 7001, 42, "Sam Employee");

        verify(audience, never()).fetchBatch(12, 3, "hr.leave.manage", 0, 1);
        verify(producer, never()).leaveRequested(
                12, 3, 7001, 42, 99, "7001", "Sam Employee");
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(audience).fetchBatch(12, 3, "hr.leave.manage", 0, 1);
        verify(producer).leaveRequested(
                12, 3, 7001, 42, 99, "7001", "Sam Employee");
    }
}
