package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.scheduler.NotificationWorkSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationControlRedisConfig {
    @Bean
    RedisMessageListenerContainer notificationControlListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationControlProperties properties,
            NotificationControlService controlService,
            NotificationResourceSaverProperties resourceSaverProperties,
            NotificationOperatingWindowService operatingWindow,
            NotificationWorkSignal workSignal) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> controlService.refresh(),
                new ChannelTopic(properties.getChangeChannel()));
        container.addMessageListener(
                (message, pattern) -> operatingWindow.refresh(),
                new ChannelTopic(resourceSaverProperties.getOperatingWindowChannel()));
        container.addMessageListener(
                (message, pattern) -> workSignal.receive(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(resourceSaverProperties.getWorkChannel()));
        return container;
    }
}
