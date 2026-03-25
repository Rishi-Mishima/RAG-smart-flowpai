package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageQuotaServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UsageQuotaService usageQuotaService;

    @BeforeEach
    void setUp() {
        UsageQuotaProperties properties = new UsageQuotaProperties();
        properties.getLlm().setDayMaxTokens(250);
        properties.getEmbedding().setDayMaxTokens(500);
        usageQuotaService = new UsageQuotaService(stringRedisTemplate, properties);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
    }

    @Test
    void shouldRollbackWhenLlmQuotaExceeded() {
        when(valueOperations.increment(anyString(), eq(300L))).thenReturn(300L);

        assertThrows(RateLimitExceededException.class,
                () -> usageQuotaService.reserveLlmTokens("42", 100, 200));

        verify(valueOperations).increment(anyString(), eq(-300L));
    }

    @Test
    void shouldAdjustReservedTokensOnSettlement() {
        when(valueOperations.increment(anyString(), eq(220L))).thenReturn(220L);
        UsageQuotaService.TokenReservation reservation = usageQuotaService.reserveLlmTokens("42", 100, 120);

        when(valueOperations.increment(anyString(), eq(-40L))).thenReturn(180L);
        when(valueOperations.increment(anyString(), eq(1L))).thenReturn(1L);

        usageQuotaService.settleReservation(reservation, 180);

        verify(valueOperations).increment(anyString(), eq(-40L));
        verify(valueOperations).increment(anyString(), eq(1L));
    }
}
