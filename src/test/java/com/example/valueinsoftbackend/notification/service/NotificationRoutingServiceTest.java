package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Dashboard;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Target;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRouting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRoutingServiceTest {

    private final DbNotificationCatalog catalog = mock(DbNotificationCatalog.class);
    private final DbNotificationRouting repository = mock(DbNotificationRouting.class);
    private final NotificationRoutingService service =
            new NotificationRoutingService(catalog, repository);

    @Test
    void customRouteNormalizesDuplicatesAndReplacesEverySelectedType() {
        Dashboard dashboard = new Dashboard(List.of(), List.of(), List.of());
        when(repository.activeUserIds(1095, Set.of(42))).thenReturn(Set.of(42));
        when(repository.activeRoleIds(1095, Set.of("BranchManager")))
                .thenReturn(Set.of("BranchManager"));
        when(repository.dashboard(1095)).thenReturn(dashboard);

        Dashboard result = service.update(1095, 7,
                List.of("pos.shift.closed", "pos.order.voided"),
                false,
                List.of(
                        new Target("USER", 42, null),
                        new Target("user", 42, null),
                        new Target("role", null, "BranchManager")));

        List<Target> expected = List.of(
                new Target("user", 42, null),
                new Target("role", null, "BranchManager"));
        verify(repository).replace(1095, "pos.shift.closed", 7, expected);
        verify(repository).replace(1095, "pos.order.voided", 7, expected);
        assertSame(dashboard, result);
    }

    @Test
    void defaultAudienceDeletesTheExplicitRouteWithoutValidatingTargets() {
        Dashboard dashboard = new Dashboard(List.of(), List.of(), List.of());
        when(repository.dashboard(1095)).thenReturn(dashboard);

        Dashboard result = service.update(1095, 7,
                List.of("pos.shift.closed"), true,
                List.of(new Target("user", 999, null)));

        verify(repository).delete(1095, "pos.shift.closed");
        verify(repository, never()).activeUserIds(1095, Set.of(999));
        assertSame(dashboard, result);
    }

    @Test
    void rejectsAUserWhoIsNotActiveInTheAuthenticatedCompany() {
        when(repository.activeUserIds(1095, Set.of(42))).thenReturn(Set.of());
        when(repository.activeRoleIds(1095, Set.of())).thenReturn(Set.of());

        assertThrows(ApiException.class, () -> service.update(
                1095, 7, List.of("pos.shift.closed"), false,
                List.of(new Target("user", 42, null))));

        verify(repository, never()).replace(
                1095, "pos.shift.closed", 7, List.of(new Target("user", 42, null)));
    }
}
