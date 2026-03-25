package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RateLimitConfig;
import com.yizhaoqi.smartpai.repository.RateLimitConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigServiceTest {

    @Mock
    private RateLimitConfigRepository rateLimitConfigRepository;

    private RateLimitConfigService rateLimitConfigService;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        rateLimitConfigService = new RateLimitConfigService(properties, rateLimitConfigRepository);
    }

    @Test
    void shouldLoadPersistedOverrides() {
        when(rateLimitConfigRepository.findAll()).thenReturn(List.of(
                createWindowConfig("chat-message", 45, 90L),
                createDualConfig("llm-global-token", 25000L, 60L, 600000L, 86400L),
                createDualConfig("embedding-upload-token", 80000L, 60L, 2400000L, 86400L),
                createDualConfig("embedding-query-request", 70L, 60L, 2200L, 86400L),
                createDualConfig("embedding-query-global-token", 30000L, 60L, 1200000L, 86400L)
        ));

        rateLimitConfigService.loadPersistedConfigs();

        RateLimitConfigService.RateLimitSettingsView settings = rateLimitConfigService.getCurrentSettings();
        assertEquals(45, settings.chatMessage().max());
        assertEquals(90L, settings.chatMessage().windowSeconds());
        assertEquals(25000L, settings.llmGlobalToken().minuteMax());
        assertEquals(600000L, settings.llmGlobalToken().dayMax());
        assertEquals(80000L, settings.embeddingUploadToken().minuteMax());
        assertEquals(2400000L, settings.embeddingUploadToken().dayMax());
        assertEquals(70L, settings.embeddingQueryRequest().minuteMax());
        assertEquals(2200L, settings.embeddingQueryRequest().dayMax());
        assertEquals(30000L, settings.embeddingQueryGlobalToken().minuteMax());
        assertEquals(1200000L, settings.embeddingQueryGlobalToken().dayMax());
    }

    @Test
    void shouldPersistUpdatedSettings() {
        when(rateLimitConfigRepository.findById(anyString())).thenReturn(Optional.empty());
        when(rateLimitConfigRepository.save(any(RateLimitConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitConfigService.UpdateRateLimitRequest request = new RateLimitConfigService.UpdateRateLimitRequest(
                new RateLimitConfigService.WindowLimitView(40, 60L),
                new RateLimitConfigService.TokenBudgetView(120000L, 60L, 9000000L, 86400L),
                new RateLimitConfigService.TokenBudgetView(180000L, 60L, 22000000L, 86400L),
                new RateLimitConfigService.DualWindowLimitView(70L, 60L, 2200L, 86400L),
                new RateLimitConfigService.TokenBudgetView(50000L, 60L, 3500000L, 86400L)
        );

        RateLimitConfigService.RateLimitSettingsView updated = rateLimitConfigService.updateSettings(request, "admin");

        assertEquals(40, updated.chatMessage().max());
        assertEquals(120000L, updated.llmGlobalToken().minuteMax());
        assertEquals(22000000L, updated.embeddingUploadToken().dayMax());
        assertEquals(2200L, updated.embeddingQueryRequest().dayMax());
        assertEquals(3500000L, updated.embeddingQueryGlobalToken().dayMax());
        verify(rateLimitConfigRepository, times(5)).save(any(RateLimitConfig.class));
    }

    @Test
    void shouldRejectInvalidDailyLimit() {
        RateLimitConfigService.UpdateRateLimitRequest request = new RateLimitConfigService.UpdateRateLimitRequest(
                new RateLimitConfigService.WindowLimitView(30, 60L),
                new RateLimitConfigService.TokenBudgetView(50000L, 60L, 40000L, 86400L),
                new RateLimitConfigService.TokenBudgetView(120000L, 60L, 20000000L, 86400L),
                new RateLimitConfigService.DualWindowLimitView(60L, 60L, 2000L, 86400L),
                new RateLimitConfigService.TokenBudgetView(30000L, 60L, 1000000L, 86400L)
        );

        assertThrows(CustomException.class, () -> rateLimitConfigService.updateSettings(request, "admin"));
    }

    private RateLimitConfig createWindowConfig(String key, int max, long windowSeconds) {
        RateLimitConfig config = new RateLimitConfig();
        config.setConfigKey(key);
        config.setSingleMax(max);
        config.setSingleWindowSeconds(windowSeconds);
        config.setUpdatedBy("admin");
        return config;
    }

    private RateLimitConfig createDualConfig(String key, long minuteMax, long minuteWindowSeconds, long dayMax, long dayWindowSeconds) {
        RateLimitConfig config = new RateLimitConfig();
        config.setConfigKey(key);
        config.setMinuteMax(minuteMax);
        config.setMinuteWindowSeconds(minuteWindowSeconds);
        config.setDayMax(dayMax);
        config.setDayWindowSeconds(dayWindowSeconds);
        config.setUpdatedBy("admin");
        return config;
    }
}
