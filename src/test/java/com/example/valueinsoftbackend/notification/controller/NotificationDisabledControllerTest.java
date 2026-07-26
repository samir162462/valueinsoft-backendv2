package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.service.NotificationAdminService;
import com.example.valueinsoftbackend.notification.service.NotificationDeviceService;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import com.example.valueinsoftbackend.notification.service.NotificationRoutingService;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDisabledControllerTest {

    private static final Principal PRINCIPAL = () -> "admin";

    @Test
    void routingRejectsRuntimeDisabledModuleBeforeResolvingTenantContext() {
        NotificationProperties properties = enabledProperties();
        NotificationControlGate controls = mock(NotificationControlGate.class);
        when(controls.isEnabled(NotificationComponent.FEED_READ)).thenReturn(false);
        NotificationRequestContextResolver contexts = mock(NotificationRequestContextResolver.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        NotificationRoutingService routing = mock(NotificationRoutingService.class);
        NotificationRoutingController controller = new NotificationRoutingController(
                contexts, authorization, routing, properties, controls);

        assertThatThrownBy(() -> controller.dashboard(PRINCIPAL))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(contexts, authorization, routing);
    }

    @Test
    void adminSearchRejectsRuntimeDisabledModuleBeforeAuthorizationOrRepositoryService() {
        NotificationProperties properties = enabledProperties();
        NotificationControlGate controls = mock(NotificationControlGate.class);
        when(controls.isEnabled(NotificationComponent.MODULE)).thenReturn(false);
        AuthorizationService authorization = mock(AuthorizationService.class);
        NotificationAdminService admin = mock(NotificationAdminService.class);
        NotificationAdminController controller =
                new NotificationAdminController(properties, controls, authorization, admin);

        assertThatThrownBy(() -> controller.devices(PRINCIPAL, 1095, 100))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(authorization, admin);
    }

    @Test
    void adminDeviceRevokeRejectsRuntimeDisabledModuleBeforeResolvingTenantContext() {
        NotificationProperties properties = enabledProperties();
        NotificationControlGate controls = mock(NotificationControlGate.class);
        when(controls.isEnabled(NotificationComponent.MODULE)).thenReturn(false);
        NotificationRequestContextResolver contexts = mock(NotificationRequestContextResolver.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        NotificationDeviceService devices = mock(NotificationDeviceService.class);
        NotificationAdminDeviceController controller = new NotificationAdminDeviceController(
                contexts, authorization, devices, properties, controls);

        assertThatThrownBy(() -> controller.revoke(PRINCIPAL, java.util.UUID.randomUUID()))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(contexts, authorization, devices);
    }

    private static NotificationProperties enabledProperties() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        return properties;
    }
}
