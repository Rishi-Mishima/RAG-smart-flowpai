package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import com.yizhaoqi.smartpai.model.DailyReqCountStat;
import com.yizhaoqi.smartpai.model.DailyUsageStat;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UsageBalanceQuotaService extends UsageQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UsageBalanceQuotaService.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UserTokenService userTokenService;

    public UsageBalanceQuotaService(StringRedisTemplate stringRedisTemplate,
                                    UsageQuotaProperties properties,
                                    UserTokenService userTokenService
    ) {
        super(stringRedisTemplate, properties);
        this.userTokenService = userTokenService;
    }

    public TokenReservation reserveLlmTokens(String userId, int estimatedPromptTokens, int maxCompletionTokens) {
        if (!isQuotaManaged(userId)) {
            return TokenReservation.noop("llm", userId);
        }

        int reserveTokens = Math.max(estimatedPromptTokens, 0) + Math.max(maxCompletionTokens, 0);
        reserveTokens = Math.max(reserveTokens, 1);

        // 检查用户余额是否充足
        if (!userTokenService.hasEnoughLlmTokens(userId, reserveTokens)) {
            Long balance = userTokenService.getLlmTokenBalance(userId);
            throw new RateLimitExceededException(
                    "LLM Token 余额不足，预估需要：" + reserveTokens + ", 当前余额：" + balance, 0);
        }

        // 用户余额模式下，不需要实际的 Redis 预留操作，只需要返回一个标记对象
        // 实际扣减在 settleReservation 中进行，因此也不需要进行异常的恢复逻辑
        return new TokenReservation(
                "llm", userId, "", "",
                reserveTokens, reserveTokens,
                0, false, true
        );
    }

    public TokenReservation reserveEmbeddingTokens(String userId, List<String> texts) {
        if (!isQuotaManaged(userId)) {
            return TokenReservation.noop("embedding", userId);
        }

        int estimatedTokens = Math.max(estimateEmbeddingTokens(texts), 1);

        // 检查用户余额是否充足
        if (!userTokenService.hasEnoughEmbeddingTokens(userId, estimatedTokens)) {
            Long balance = userTokenService.getEmbeddingTokenBalance(userId);
            throw new RateLimitExceededException(
                    "Embedding Token 余额不足，预估需要：" + estimatedTokens + ", 当前余额：" + balance, 0);
        }

        // 用户余额模式下，不需要实际的 Redis 预留操作
        return new TokenReservation(
                "embedding", userId, "", "",
                estimatedTokens, estimatedTokens,
                0, false, true
        );
    }

    /**
     * 记录用户聊天请求次数
     * @param userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordChatRequest(String userId) {
        if (!isQuotaManaged(userId)) {
            return;
        }

        userTokenService.updateUserDailyChatCount(userId, LocalDate.now());
    }

    /**
     * 结算用户的token消耗
     *
     * @param reservation
     * @param actualTokens
     */
    @Transactional(rollbackFor = Exception.class)
    public void settleReservation(TokenReservation reservation, int actualTokens) {
        if (reservation == null || reservation.noop()) {
            return;
        }

        if (reservation.quotaKey().isBlank()) {
            if (actualTokens <= 0) {
                return;
            }

            // 结算用户的token消耗
            try {
                // 更新总的请求次数
                userTokenService.incrementUserTotalRequestCount(reservation.scope(), reservation.userId());

                // 根据消耗token扣减用户余额
                if ("llm".equals(reservation.scope())) {
                    userTokenService.consumeLlmTokens(reservation.userId(), actualTokens);
                    logger.info("用户 {} 结算 LLM Token: {}, 剩余额度从预留中扣减",
                            reservation.userId(), actualTokens);
                } else if ("embedding".equals(reservation.scope())) {
                    userTokenService.consumeEmbeddingTokens(reservation.userId(), actualTokens);
                    logger.info("用户 {} 结算 Embedding Token: {}, 剩余额度从预留中扣减",
                            reservation.userId(), actualTokens);
                }
            } catch (Exception e) {
                logger.error("结算用户 Token 失败：userId={}, scope={}, tokens={}",
                        reservation.userId(), reservation.scope(), actualTokens, e);
                throw e;
            }
        } else {
            super.settleReservation(reservation, actualTokens);
        }
    }


    /**
     * 获取用户使用情况快照
     * @param userIds
     * @return
     */
    public Map<String, UserUsageSnapshot> getSnapshots(List<String> userIds) {
        Map<String, UserUsageSnapshot> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        for (String userId : userIds) {
            result.put(userId, new UserUsageSnapshot(
                    currentDay(),
                    userTokenService.getUserDailyChatCount(userId, LocalDate.now()),
                    buildQuotaView("llm", userId, properties.getLlm()),
                    buildQuotaView("embedding", userId, properties.getEmbedding())
            ));
        }
        return result;
    }

    /**
     * 获取用户额度视图
     * @param scope
     * @param userId
     * @param quota
     * @return
     */
    private QuotaView buildQuotaView(String scope, String userId, UsageQuotaProperties.DailyTokenQuota quota) {
        if (!isQuotaManaged(userId) || !quota.isEnabled()) {
            return new QuotaView(false, 0, 0, 0, 0);
        }

        // 用户的总token
        long limit, balance;
        if ("llm".equals(scope)) {
            balance = userTokenService.getLlmTokenBalance(userId);
            limit = userTokenService.getUserLlmTotalIncreaseTokens(userId);
        } else {
            balance = userTokenService.getEmbeddingTokenBalance(userId);
            limit = userTokenService.getUserEmbeddingTotalIncreaseTokens(userId);
        }
        long usedTokens = limit - balance;
        // 请求次数
        long requestCount = userTokenService.getUserTotalRequestCount(scope, userId);
        return new QuotaView(true, usedTokens, limit, balance, requestCount);
    }

    private String currentDay() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(DAY_FORMATTER);
    }

    private boolean isQuotaManaged(String userId) {
        return userId != null && !userId.isBlank() && !userId.startsWith("system");
    }


    /**
     * 用于后台统计每天的使用量
     *
     * @param userIds
     * @param days
     * @return
     */
    public List<DailyUsageAggregate> getDailyAggregates(List<String> userIds, int days) {
        int normalizedDays = Math.max(1, Math.min(days, properties.getRetentionDays()));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate startDay = today.minusDays(normalizedDays - 1);

        // 查询所有用户，在这个时间段内的数据统计
        List<DailyUsageStat> llmStat = userTokenService.getDailyStatsByType(startDay, today, UserTokenRecord.TokenType.LLM);
        List<DailyUsageStat> embeddingStat = userTokenService.getDailyStatsByType(startDay, today, UserTokenRecord.TokenType.EMBEDDING);

        List<DailyReqCountStat> reqCountStat = userTokenService.getDailyReqCountStats(startDay, today);

        // 将上面的数据，聚合成结果返回，需要开始时间到结束时间，如果中间缺了日期，则需要自动补全
        List<DailyUsageAggregate> result = new ArrayList<>();

        // 构建日期到统计数据的映射
        java.util.Map<LocalDate, DailyUsageStat> llmStatMap = llmStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyUsageStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        java.util.Map<LocalDate, DailyUsageStat> embeddingStatMap = embeddingStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyUsageStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        java.util.Map<LocalDate, DailyReqCountStat> reqCountStatMap = reqCountStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyReqCountStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        // 遍历所有日期，生成聚合结果
        for (LocalDate day = startDay; !day.isAfter(today); day = day.plusDays(1)) {
            String dayString = day.format(DAY_FORMATTER);

            DailyUsageStat llmStatForDay = llmStatMap.get(day);
            DailyUsageStat embeddingStatForDay = embeddingStatMap.get(day);
            DailyReqCountStat reqCountStatForDay = reqCountStatMap.get(day);

            result.add(new DailyUsageAggregate(
                    dayString,
                    reqCountStatForDay != null ? reqCountStatForDay.totalRequestCount() : 0L,
                    llmStatForDay != null ? llmStatForDay.totalAmount() : 0L,
                    llmStatForDay != null ? llmStatForDay.totalRequestCount() : 0L,
                    embeddingStatForDay != null ? embeddingStatForDay.totalAmount() : 0L,
                    embeddingStatForDay != null ? embeddingStatForDay.totalRequestCount() : 0L
            ));
        }

        return result;
    }
}
