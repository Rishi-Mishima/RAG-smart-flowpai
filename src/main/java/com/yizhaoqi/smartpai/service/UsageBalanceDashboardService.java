package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import com.yizhaoqi.smartpai.repository.UserRepository;

import java.util.Comparator;
import java.util.List;

public class UsageBalanceDashboardService extends UsageDashboardService {
    private static final int RANK_LIMIT = 5;

    private final UserRepository userRepository;
    private final UsageQuotaService usageQuotaService;
    private final UserTokenService userTokenService;

    public UsageBalanceDashboardService(UserRepository userRepository,
                                        UsageQuotaService usageQuotaService,
                                        UserTokenService userTokenService) {
        super(userRepository, usageQuotaService);
        this.userRepository = userRepository;
        this.usageQuotaService = usageQuotaService;
        this.userTokenService = userTokenService;
    }

    public UsageOverview buildOverview(int days) {
        int normalizedDays = days <= 7 ? 7 : 30;
        List<DailyUsagePoint> trends = usageQuotaService.getDailyAggregates(List.of(), normalizedDays).stream()
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

        // 从数据库查询今日 LLM Token 消耗前 5 名用户
        var llmTopConsumers = userTokenService.getTodayTopConsumers(UserTokenRecord.TokenType.LLM, RANK_LIMIT);

        // 转换为排行榜项（需要关联用户信息）
        List<UsageRankingItem> llmRankings = llmTopConsumers.stream()
                .map(item -> buildRankingItem(item, "llm"))
                .filter(item -> item.usedTokens() > 0)
                .toList();

        // 从数据库查询今日 Embedding Token 消耗前 5 名用户
        var embeddingTopConsumers = userTokenService.getTodayTopConsumers(UserTokenRecord.TokenType.EMBEDDING, RANK_LIMIT);
        // 转换为排行榜项
        List<UsageRankingItem> embeddingRankings = embeddingTopConsumers.stream()
                .map(item -> buildRankingItem(item, "embedding"))
                .filter(item -> item.usedTokens() > 0)
                .toList();

        // 从数据库中查找需要预警的用户（LLM 和 Embedding）
        List<UsageAlert> llmAlerts = buildTokenAlerts(UserTokenRecord.TokenType.LLM);
        List<UsageAlert> embeddingAlerts = buildTokenAlerts(UserTokenRecord.TokenType.EMBEDDING);

        // 合并并排序告警
        List<UsageAlert> alerts = new java.util.ArrayList<>();
        alerts.addAll(llmAlerts);
        alerts.addAll(embeddingAlerts);
        alerts = alerts.stream()
                .sorted(Comparator
                        .comparing((UsageAlert alert) -> alert.level().equals("critical") ? 0 : 1)
                        .thenComparing(UsageAlert::usageRatio).reversed())
                .toList();

        return new UsageOverview(normalizedDays, today, trends, llmRankings, embeddingRankings, alerts);
    }

    /**
     * 构建 排行榜项
     */
    private UsageRankingItem buildRankingItem(UserTokenRecord consumer, String scope) {
        // 根据 userId 查询用户信息
        User user = userRepository.findById(Long.valueOf(consumer.getUserId())).orElse(null);
        String username = user != null ? user.getUsername() : "Unknown User";

        return new UsageRankingItem(
                consumer.getUserId(),
                username,
                scope,
                consumer.getAmount(),
                consumer.getBalanceBefore(),
                consumer.getBalanceAfter(),  // remaining - 从数据库无法直接获取
                consumer.getRequestCount()
        );
    }

    /**
     * 构建 Token 预警列表
     */
    private List<UsageAlert> buildTokenAlerts(UserTokenRecord.TokenType tokenType) {
        // 从数据库查询需要预警的用户（余额低于 1000 或使用比例超过 80%）
        var alertItems = userTokenService.getLowBalanceUsers(tokenType, 9300L);

        // 转换为 UsageAlert
        return alertItems.stream()
                .map(item -> {
                    User user = userRepository.findById(Long.parseLong(item.getUserId())).orElse(null);
                    String username = user != null ? user.getUsername() : "Unknown User";
                    String scope = tokenType == UserTokenRecord.TokenType.LLM ? "llm" : "embedding";

                    // 判断告警级别
                    String level = item.getAmount() <= 0 ? "critical" : "warning";
                    String message = item.getAmount() <= 0 ? "剩余额度已耗尽" : "剩余额度已接近上限";

                    return new UsageAlert(
                            level,
                            item.getUserId(),
                            username,
                            scope,
                            item.getAmount(),
                            item.getBalanceBefore(),
                            item.getBalanceAfter(),
                            item.getRequestCount(),
                            // 保留两位小数
                            Math.round(item.getBalanceAfter() / (double) item.getBalanceBefore() * 100) / 100.0,
                            message
                    );
                })
                .toList();
    }
}
