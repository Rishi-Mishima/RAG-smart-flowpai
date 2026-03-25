package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

//@Service
public class UsageQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UsageQuotaService.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final double ASCII_TOKEN_RATIO = 0.30d;
    private static final double CJK_TOKEN_RATIO = 0.95d;
    private static final double OTHER_TOKEN_RATIO = 0.55d;

    protected final StringRedisTemplate stringRedisTemplate;
    protected final UsageQuotaProperties properties;

    public UsageQuotaService(StringRedisTemplate stringRedisTemplate, UsageQuotaProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public TokenReservation reserveLlmTokens(String userId, int estimatedPromptTokens, int maxCompletionTokens) {
        if (!isQuotaManaged(userId) || !properties.getLlm().isEnabled()) {
            return TokenReservation.noop("llm", userId);
        }

        int reserveTokens = Math.max(estimatedPromptTokens, 0) + Math.max(maxCompletionTokens, 0);
        reserveTokens = Math.max(reserveTokens, 1);

        return reserveDailyTokens("llm", userId, reserveTokens, properties.getLlm().getDayMaxTokens(), "LLM当日Token额度已达上限");
    }

    public TokenReservation reserveEmbeddingTokens(String userId, List<String> texts) {
        if (!isQuotaManaged(userId) || !properties.getEmbedding().isEnabled()) {
            return TokenReservation.noop("embedding", userId);
        }

        int estimatedTokens = estimateEmbeddingTokens(texts);
        return reserveDailyTokens("embedding", userId, Math.max(estimatedTokens, 1),
                properties.getEmbedding().getDayMaxTokens(), "Embedding当日Token额度已达上限");
    }

    public TokenReservationBundle reserveLlmTokensWithGlobalBudget(
            String userId,
            int estimatedPromptTokens,
            int maxCompletionTokens,
            long minuteLimit,
            long minuteWindowSeconds,
            long dayLimit,
            long dayWindowSeconds
    ) {
        int reserveTokens = Math.max(estimatedPromptTokens, 0) + Math.max(maxCompletionTokens, 0);
        reserveTokens = Math.max(reserveTokens, 1);

        List<TokenReservation> reservations = new java.util.ArrayList<>(3);
        try {
            addIfActive(reservations, reserveLlmTokens(userId, estimatedPromptTokens, maxCompletionTokens));
            addIfActive(reservations, reserveGlobalRollingTokens(
                    "llm",
                    "minute",
                    reserveTokens,
                    minuteLimit,
                    minuteWindowSeconds,
                    "LLM全网分钟Token预算已达上限"
            ));
            addIfActive(reservations, reserveGlobalRollingTokens(
                    "llm",
                    "day",
                    reserveTokens,
                    dayLimit,
                    dayWindowSeconds,
                    "LLM全网当日Token预算已达上限"
            ));
        } catch (RuntimeException exception) {
            abortReservedTokens(reservations);
            throw exception;
        }
        return TokenReservationBundle.of("llm", userId, reservations);
    }

    public TokenReservationBundle reserveEmbeddingTokensWithGlobalBudget(
            String userId,
            List<String> texts,
            String budgetScope,
            String minuteExceededMessage,
            String dayExceededMessage,
            long minuteLimit,
            long minuteWindowSeconds,
            long dayLimit,
            long dayWindowSeconds
    ) {
        int reserveTokens = Math.max(estimateEmbeddingTokens(texts), 1);

        List<TokenReservation> reservations = new java.util.ArrayList<>(3);
        try {
            addIfActive(reservations, reserveEmbeddingTokens(userId, texts));
            addIfActive(reservations, reserveGlobalRollingTokens(
                    budgetScope,
                    "minute",
                    reserveTokens,
                    minuteLimit,
                    minuteWindowSeconds,
                    minuteExceededMessage
            ));
            addIfActive(reservations, reserveGlobalRollingTokens(
                    budgetScope,
                    "day",
                    reserveTokens,
                    dayLimit,
                    dayWindowSeconds,
                    dayExceededMessage
            ));
        } catch (RuntimeException exception) {
            abortReservedTokens(reservations);
            throw exception;
        }
        return TokenReservationBundle.of(budgetScope, userId, reservations);
    }

    public void recordChatRequest(String userId) {
        if (!isQuotaManaged(userId)) {
            return;
        }

        incrementMetricKey(buildMetricKey("chat", userId), 1, secondsUntilEndOfDay());
    }

    public void settleReservation(TokenReservation reservation, int actualTokens) {
        if (reservation == null || reservation.noop()) {
            return;
        }

        long delta = (long) actualTokens - reservation.reservedTokens();
        if (delta == 0) {
            incrementMetricIfPresent(reservation);
            return;
        }

        if (!reservation.retainHistory() && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(reservation.quotaKey()))) {
            incrementMetricIfPresent(reservation);
            return;
        }

        Long total = stringRedisTemplate.opsForValue().increment(reservation.quotaKey(), delta);
        if (reservation.retainHistory()) {
            ensureExpiry(reservation.quotaKey(), retentionTtlSeconds());
        }
        incrementMetricIfPresent(reservation);

        if (total != null && total > reservation.limit()) {
            logger.warn("用户 {} 的 {} token 实际用量超过额度: total={}, limit={}",
                    reservation.userId(), reservation.scope(), total, reservation.limit());
        }
    }

    public void abortReservation(TokenReservation reservation) {
        if (reservation == null || reservation.noop()) {
            return;
        }

        if (!reservation.retainHistory() && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(reservation.quotaKey()))) {
            return;
        }

        stringRedisTemplate.opsForValue().increment(reservation.quotaKey(), -reservation.reservedTokens());
        if (reservation.retainHistory()) {
            ensureExpiry(reservation.quotaKey(), retentionTtlSeconds());
        }
    }

    public void settleReservation(TokenReservationBundle reservationBundle, int actualTokens) {
        if (reservationBundle == null || reservationBundle.noop()) {
            return;
        }

        for (TokenReservation reservation : reservationBundle.reservations()) {
            settleReservation(reservation, actualTokens);
        }
    }

    public void abortReservation(TokenReservationBundle reservationBundle) {
        if (reservationBundle == null || reservationBundle.noop()) {
            return;
        }

        for (TokenReservation reservation : reservationBundle.reservations()) {
            abortReservation(reservation);
        }
    }

    public UserUsageSnapshot getSnapshot(String userId) {
        Map<String, UserUsageSnapshot> snapshots = getSnapshots(List.of(userId));
        return snapshots.getOrDefault(userId, emptySnapshot());
    }

    public Map<String, UserUsageSnapshot> getSnapshots(List<String> userIds) {
        Map<String, UserUsageSnapshot> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        for (String userId : userIds) {
            result.put(userId, new UserUsageSnapshot(
                    currentDay(),
                    readCounter(buildMetricKey("chat", userId)),
                    buildQuotaView("llm", userId, properties.getLlm()),
                    buildQuotaView("embedding", userId, properties.getEmbedding())
            ));
        }
        return result;
    }

    public List<DailyUsageAggregate> getDailyAggregates(List<String> userIds, int days) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        int normalizedDays = Math.max(1, Math.min(days, properties.getRetentionDays()));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        return IntStream.range(0, normalizedDays)
                .mapToObj(offset -> today.minusDays(normalizedDays - 1L - offset))
                .map(day -> buildDailyAggregate(userIds, day))
                .toList();
    }

    public int estimateChatTokens(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Map<String, String> message : messages) {
            total += 8;
            total += estimateTextTokens(message.get("role"));
            total += estimateTextTokens(message.get("content"));
        }
        return total;
    }

    public int estimateEmbeddingTokens(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (String text : texts) {
            total += estimateTextTokens(text) + 4;
        }
        return (int) Math.ceil(total * 1.15d);
    }

    public int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int ascii = 0;
        int cjk = 0;
        int other = 0;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }

            Character.UnicodeScript script = Character.UnicodeScript.of(current);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                cjk++;
            } else if (current <= 0x7F) {
                ascii++;
            } else {
                other++;
            }
        }

        double estimated = ascii * ASCII_TOKEN_RATIO + cjk * CJK_TOKEN_RATIO + other * OTHER_TOKEN_RATIO + 12;
        return Math.max(1, (int) Math.ceil(estimated));
    }

    private TokenReservation reserveDailyTokens(String scope, String userId, int reserveTokens, long dailyLimit, String message) {
        String quotaKey = buildQuotaKey(scope, userId);
        long expiresInSeconds = secondsUntilEndOfDay();
        Long total = stringRedisTemplate.opsForValue().increment(quotaKey, reserveTokens);
        ensureExpiry(quotaKey, retentionTtlSeconds());

        if (total != null && total > dailyLimit) {
            stringRedisTemplate.opsForValue().increment(quotaKey, -reserveTokens);
            throw new RateLimitExceededException(message, expiresInSeconds);
        }

        return new TokenReservation(scope, userId, quotaKey, buildMetricKey(scope, userId), reserveTokens, dailyLimit, expiresInSeconds, false, true);
    }

    private TokenReservation reserveGlobalRollingTokens(
            String scope,
            String windowLabel,
            int reserveTokens,
            long limit,
            long windowSeconds,
            String message
    ) {
        if (limit <= 0 || windowSeconds <= 0) {
            return TokenReservation.noop(scope, "global");
        }

        String quotaKey = buildGlobalBudgetKey(scope, windowLabel);
        Long total = stringRedisTemplate.opsForValue().increment(quotaKey, reserveTokens);
        if (total != null && total == reserveTokens) {
            stringRedisTemplate.expire(quotaKey, windowSeconds, TimeUnit.SECONDS);
        }

        Long ttl = stringRedisTemplate.getExpire(quotaKey, TimeUnit.SECONDS);
        long expiresInSeconds = ttl == null || ttl < 0 ? windowSeconds : ttl;
        if (total != null && total > limit) {
            stringRedisTemplate.opsForValue().increment(quotaKey, -reserveTokens);
            throw new RateLimitExceededException(message, expiresInSeconds);
        }

        return new TokenReservation(scope + "-global-" + windowLabel, "global", quotaKey, "", reserveTokens, limit, expiresInSeconds, false, false);
    }

    private void addIfActive(List<TokenReservation> reservations, TokenReservation reservation) {
        if (reservation != null && !reservation.noop()) {
            reservations.add(reservation);
        }
    }

    private void abortReservedTokens(List<TokenReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }

        for (int index = reservations.size() - 1; index >= 0; index--) {
            abortReservation(reservations.get(index));
        }
    }

    private void incrementMetricIfPresent(TokenReservation reservation) {
        if (reservation.metricKey() != null && !reservation.metricKey().isBlank()) {
            incrementMetricKey(reservation.metricKey(), 1, reservation.expiresInSeconds());
        }
    }

    private void incrementMetricKey(String metricKey, long increment, long expiresInSeconds) {
        stringRedisTemplate.opsForValue().increment(metricKey, increment);
        ensureExpiry(metricKey, retentionTtlSeconds());
    }

    private QuotaView buildQuotaView(String scope, String userId, UsageQuotaProperties.DailyTokenQuota quota) {
        if (!isQuotaManaged(userId) || !quota.isEnabled()) {
            return new QuotaView(false, 0, 0, 0, 0);
        }

        long usedTokens = readCounter(buildQuotaKey(scope, userId));
        long requestCount = readCounter(buildMetricKey(scope, userId));
        long limitTokens = quota.getDayMaxTokens();
        long remainingTokens = Math.max(limitTokens - usedTokens, 0);
        return new QuotaView(true, usedTokens, limitTokens, remainingTokens, requestCount);
    }

    private long readCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("无法解析 usage counter: key={}, value={}", key, value);
            return 0L;
        }
    }

    private void ensureExpiry(String key, long expiresInSeconds) {
        if (expiresInSeconds <= 0) {
            expiresInSeconds = 86400;
        }
        stringRedisTemplate.expire(key, expiresInSeconds, TimeUnit.SECONDS);
    }

    private String buildQuotaKey(String scope, String userId) {
        return buildQuotaKey(scope, userId, currentDay());
    }

    private String buildMetricKey(String scope, String userId) {
        return buildMetricKey(scope, userId, currentDay());
    }

    private String buildQuotaKey(String scope, String userId, String day) {
        return "quota:" + scope + ":" + day + ":user:" + userId;
    }

    private String buildMetricKey(String scope, String userId, String day) {
        return "quota:" + scope + ":requests:" + day + ":user:" + userId;
    }

    private String buildGlobalBudgetKey(String scope, String windowLabel) {
        return "budget:" + scope + ":global:" + windowLabel;
    }

    private String currentDay() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(DAY_FORMATTER);
    }

    private long secondsUntilEndOfDay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        return Math.max(Duration.between(now, nextDay).getSeconds(), 1);
    }

    private long retentionTtlSeconds() {
        int retentionDays = Math.max(properties.getRetentionDays(), 1);
        LocalDateTime expireAt = LocalDate.now(ZoneId.systemDefault())
                .plusDays(retentionDays)
                .atTime(LocalTime.MAX);
        return Math.max(Duration.between(LocalDateTime.now(ZoneId.systemDefault()), expireAt).getSeconds(), 86400);
    }

    private boolean isQuotaManaged(String userId) {
        return userId != null && !userId.isBlank() && !userId.startsWith("system");
    }

    private UserUsageSnapshot emptySnapshot() {
        return new UserUsageSnapshot(currentDay(),
                0,
                new QuotaView(false, 0, 0, 0, 0),
                new QuotaView(false, 0, 0, 0, 0));
    }

    public record TokenReservation(
            String scope,
            String userId,
            String quotaKey,
            String metricKey,
            long reservedTokens,
            long limit,
            long expiresInSeconds,
            boolean noop,
            boolean retainHistory
    ) {
        public static TokenReservation noop(String scope, String userId) {
            return new TokenReservation(scope, userId, "", "", 0, 0, 0, true, false);
        }
    }

    public record TokenReservationBundle(
            String scope,
            String userId,
            List<TokenReservation> reservations,
            boolean noop
    ) {
        public static TokenReservationBundle of(String scope, String userId, List<TokenReservation> reservations) {
            List<TokenReservation> normalized = reservations == null ? List.of() : List.copyOf(reservations);
            return new TokenReservationBundle(scope, userId, normalized, normalized.isEmpty());
        }

        public static TokenReservationBundle noop(String scope, String userId) {
            return new TokenReservationBundle(scope, userId, List.of(), true);
        }
    }

    public record QuotaView(
            boolean enabled,
            long usedTokens,
            long limitTokens,
            long remainingTokens,
            long requestCount
    ) {
    }

    public record UserUsageSnapshot(
            String day,
            long chatRequestCount,
            QuotaView llm,
            QuotaView embedding
    ) {
    }

    public record DailyUsageAggregate(
            String day,
            long chatRequestCount,
            long llmUsedTokens,
            long llmRequestCount,
            long embeddingUsedTokens,
            long embeddingRequestCount
    ) {
    }

    private DailyUsageAggregate buildDailyAggregate(List<String> userIds, LocalDate day) {
        String dayString = day.format(DAY_FORMATTER);
        long chatRequestCount = 0L;
        long llmUsedTokens = 0L;
        long llmRequestCount = 0L;
        long embeddingUsedTokens = 0L;
        long embeddingRequestCount = 0L;

        for (String userId : userIds) {
            if (!isQuotaManaged(userId)) {
                continue;
            }

            chatRequestCount += readCounter(buildMetricKey("chat", userId, dayString));
            llmUsedTokens += readCounter(buildQuotaKey("llm", userId, dayString));
            llmRequestCount += readCounter(buildMetricKey("llm", userId, dayString));
            embeddingUsedTokens += readCounter(buildQuotaKey("embedding", userId, dayString));
            embeddingRequestCount += readCounter(buildMetricKey("embedding", userId, dayString));
        }

        return new DailyUsageAggregate(dayString, chatRequestCount, llmUsedTokens, llmRequestCount, embeddingUsedTokens, embeddingRequestCount);
    }
}
