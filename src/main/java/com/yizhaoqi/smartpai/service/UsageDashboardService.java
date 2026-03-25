package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class UsageDashboardService {

    private final UserRepository userRepository;
    private final UsageQuotaService usageQuotaService;

    public UsageDashboardService(UserRepository userRepository, UsageQuotaService usageQuotaService) {
        this.userRepository = userRepository;
        this.usageQuotaService = usageQuotaService;
    }

    public UsageOverview buildOverview(int days) {
        int normalizedDays = days <= 7 ? 7 : 30;
        List<User> users = userRepository.findAll();
        List<String> userIds = users.stream()
                .map(user -> String.valueOf(user.getId()))
                .toList();

        // fixme 如果用户较多，这里的 snapshots 的获取方案有oom的风险
        Map<String, UsageQuotaService.UserUsageSnapshot> snapshots = usageQuotaService.getSnapshots(userIds);
        List<DailyUsagePoint> trends = usageQuotaService.getDailyAggregates(userIds, normalizedDays).stream()
                .map(item -> new DailyUsagePoint(
                        item.day(),
                        item.chatRequestCount(),
                        item.llmUsedTokens(),
                        item.llmRequestCount(),
                        item.embeddingUsedTokens(),
                        item.embeddingRequestCount()
                ))
                .toList();

        DailyUsagePoint today = trends.isEmpty()
                ? new DailyUsagePoint("", 0, 0, 0, 0, 0)
                : trends.get(trends.size() - 1);

        List<UsageRankingItem> llmRankings = users.stream()
                .map(user -> toRankingItem(user, snapshots.get(String.valueOf(user.getId())), "llm"))
                .filter(item -> item.usedTokens() > 0)
                .sorted(Comparator.comparingLong(UsageRankingItem::usedTokens).reversed())
                .limit(5)
                .toList();

        List<UsageRankingItem> embeddingRankings = users.stream()
                .map(user -> toRankingItem(user, snapshots.get(String.valueOf(user.getId())), "embedding"))
                .filter(item -> item.usedTokens() > 0)
                .sorted(Comparator.comparingLong(UsageRankingItem::usedTokens).reversed())
                .limit(5)
                .toList();

        List<UsageAlert> alerts = users.stream()
                .flatMap(user -> buildAlerts(user, snapshots.get(String.valueOf(user.getId()))).stream())
                .sorted(Comparator
                        .comparing((UsageAlert alert) -> alert.level().equals("critical") ? 0 : 1)
                        .thenComparing(UsageAlert::usageRatio).reversed())
                .toList();

        return new UsageOverview(normalizedDays, today, trends, llmRankings, embeddingRankings, alerts);
    }

    private UsageRankingItem toRankingItem(User user, UsageQuotaService.UserUsageSnapshot snapshot, String scope) {
        UsageQuotaService.UserUsageSnapshot safeSnapshot = snapshot != null ? snapshot : emptySnapshot();
        UsageQuotaService.QuotaView quota = "embedding".equals(scope) ? safeSnapshot.embedding() : safeSnapshot.llm();
        return new UsageRankingItem(
                String.valueOf(user.getId()),
                user.getUsername(),
                scope,
                quota.usedTokens(),
                quota.limitTokens(),
                quota.remainingTokens(),
                quota.requestCount()
        );
    }

    private List<UsageAlert> buildAlerts(User user, UsageQuotaService.UserUsageSnapshot snapshot) {
        UsageQuotaService.UserUsageSnapshot safeSnapshot = snapshot != null ? snapshot : emptySnapshot();
        List<UsageAlert> alerts = new ArrayList<>(2);

        UsageAlert llmAlert = buildAlert(user, "llm", safeSnapshot.llm());
        if (llmAlert != null) {
            alerts.add(llmAlert);
        }

        UsageAlert embeddingAlert = buildAlert(user, "embedding", safeSnapshot.embedding());
        if (embeddingAlert != null) {
            alerts.add(embeddingAlert);
        }

        return alerts;
    }

    private UsageAlert buildAlert(User user, String scope, UsageQuotaService.QuotaView quota) {
        if (!quota.enabled() || quota.limitTokens() <= 0 || quota.usedTokens() <= 0) {
            return null;
        }

        double ratio = quota.limitTokens() == 0 ? 0d : (double) quota.usedTokens() / quota.limitTokens();
        if (quota.remainingTokens() == 0) {
            return new UsageAlert(
                    "critical",
                    String.valueOf(user.getId()),
                    user.getUsername(),
                    scope,
                    quota.usedTokens(),
                    quota.limitTokens(),
                    quota.remainingTokens(),
                    quota.requestCount(),
                    ratio,
                    "今日额度已耗尽"
            );
        }

        if (ratio >= 0.8d) {
            return new UsageAlert(
                    "warning",
                    String.valueOf(user.getId()),
                    user.getUsername(),
                    scope,
                    quota.usedTokens(),
                    quota.limitTokens(),
                    quota.remainingTokens(),
                    quota.requestCount(),
                    ratio,
                    "今日额度已接近上限"
            );
        }

        return null;
    }

    private UsageQuotaService.UserUsageSnapshot emptySnapshot() {
        return new UsageQuotaService.UserUsageSnapshot(
                "",
                0,
                new UsageQuotaService.QuotaView(false, 0, 0, 0, 0),
                new UsageQuotaService.QuotaView(false, 0, 0, 0, 0)
        );
    }

    public record UsageOverview(
            int days,
            DailyUsagePoint today,
            List<DailyUsagePoint> trends,
            List<UsageRankingItem> llmRankings,
            List<UsageRankingItem> embeddingRankings,
            List<UsageAlert> alerts
    ) {
    }

    public record DailyUsagePoint(
            String day,
            long chatRequestCount,
            long llmUsedTokens,
            long llmRequestCount,
            long embeddingUsedTokens,
            long embeddingRequestCount
    ) {
    }

    public record UsageRankingItem(
            String userId,
            String username,
            String scope,
            long usedTokens,
            long limitTokens,
            long remainingTokens,
            long requestCount
    ) {
    }

    public record UsageAlert(
            String level,
            String userId,
            String username,
            String scope,
            long usedTokens,
            long limitTokens,
            long remainingTokens,
            long requestCount,
            double usageRatio,
            String message
    ) {
    }
}
