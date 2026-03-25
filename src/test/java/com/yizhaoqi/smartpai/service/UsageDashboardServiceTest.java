package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsageQuotaService usageQuotaService;

    @InjectMocks
    private UsageDashboardService usageDashboardService;

    @Test
    void shouldNotFailWhenNoAlertIsGenerated() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole(User.Role.ADMIN);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(usageQuotaService.getSnapshots(List.of("1"))).thenReturn(Map.of(
                "1",
                new UsageQuotaService.UserUsageSnapshot(
                        "2026-03-07",
                        0,
                        new UsageQuotaService.QuotaView(true, 0, 300000, 300000, 0),
                        new UsageQuotaService.QuotaView(true, 0, 1000000, 1000000, 0)
                )
        ));
        when(usageQuotaService.getDailyAggregates(List.of("1"), 7)).thenReturn(List.of(
                new UsageQuotaService.DailyUsageAggregate("2026-03-07", 0, 0, 0, 0, 0)
        ));

        UsageDashboardService.UsageOverview overview = assertDoesNotThrow(() -> usageDashboardService.buildOverview(7));

        assertEquals(0, overview.alerts().size());
    }

    @Test
    void shouldFallbackToEmptySnapshotWhenSnapshotMissing() {
        User user = new User();
        user.setId(2L);
        user.setUsername("tester");
        user.setRole(User.Role.USER);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(usageQuotaService.getSnapshots(List.of("2"))).thenReturn(Map.of());
        when(usageQuotaService.getDailyAggregates(List.of("2"), 7)).thenReturn(List.of(
                new UsageQuotaService.DailyUsageAggregate("2026-03-07", 0, 0, 0, 0, 0)
        ));

        UsageDashboardService.UsageOverview overview = assertDoesNotThrow(() -> usageDashboardService.buildOverview(7));

        assertEquals(0, overview.llmRankings().size());
        assertEquals(0, overview.embeddingRankings().size());
        assertEquals(0, overview.alerts().size());
    }
}
