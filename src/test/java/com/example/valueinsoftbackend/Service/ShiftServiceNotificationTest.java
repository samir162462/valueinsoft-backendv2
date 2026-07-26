package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Request.OpenShiftRequest;
import com.example.valueinsoftbackend.Model.Shift.Shift;
import com.example.valueinsoftbackend.Service.client.ClientReceiptService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.notification.producer.NotificationPilotIntegrationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiftServiceNotificationTest {

    @Test
    void newlyOpenedShiftPublishesNotificationIntegrationEvent() {
        DbPosShiftPeriod shifts = mock(DbPosShiftPeriod.class);
        NotificationPilotIntegrationService notifications =
                mock(NotificationPilotIntegrationService.class);
        ShiftService service = service(shifts);
        service.setNotificationPilotIntegrationService(notifications);
        Shift opened = shift(24, 1074);
        when(shifts.getActiveShift(1095, 1074)).thenReturn(null);
        when(shifts.insertShift(
                eq(1095), eq(1074), any(Timestamp.class), eq("cashier"),
                eq(BigDecimal.valueOf(6000.0)), eq("POS-1")))
                .thenReturn(opened);

        Shift result = service.openShift(
                1095, new OpenShiftRequest(1074, 6000, "POS-1"), "cashier");

        assertSame(opened, result);
        verify(notifications).afterShiftOpened(1095, 1074, 24, "cashier");
    }

    @Test
    void existingOpenShiftDoesNotPublishDuplicateNotification() {
        DbPosShiftPeriod shifts = mock(DbPosShiftPeriod.class);
        NotificationPilotIntegrationService notifications =
                mock(NotificationPilotIntegrationService.class);
        ShiftService service = service(shifts);
        service.setNotificationPilotIntegrationService(notifications);
        Shift existing = shift(24, 1074);
        when(shifts.getActiveShift(1095, 1074)).thenReturn(existing);

        Shift result = service.openShift(
                1095, new OpenShiftRequest(1074, 6000, "POS-1"), "cashier");

        assertSame(existing, result);
        verify(notifications, never()).afterShiftOpened(
                1095, 1074, 24, "cashier");
    }

    private static ShiftService service(DbPosShiftPeriod shifts) {
        return new ShiftService(
                shifts,
                mock(DbPosOrder.class),
                mock(ClientReceiptService.class),
                mock(FinanceOperationalPostingService.class));
    }

    private static Shift shift(int shiftId, int branchId) {
        Shift shift = new Shift();
        shift.setShiftId(shiftId);
        shift.setBranchId(branchId);
        shift.setStatus("OPEN");
        return shift;
    }
}
