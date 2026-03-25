package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.DailyReqCountStat;
import com.yizhaoqi.smartpai.model.DailyUsageStat;
import com.yizhaoqi.smartpai.model.UserDailyChatCount;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import com.yizhaoqi.smartpai.repository.UserDailyChatCountRepository;
import com.yizhaoqi.smartpai.repository.UserTokenRecordRepository;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 用户 Token 管理服务
 * 负责管理用户的全局可用 Token（LLM Token 和 Embedding Token）
 *
 * @author YiHui
 * @date 2026/3/18
 */
@Slf4j
@Service
public class UserTokenService {

    private static final Logger logger = LoggerFactory.getLogger(UserTokenService.class);

    /**
     * Redis Key 前缀：user:token:llm:{userId}
     */
    private static final String LLM_TOKEN_KEY_PREFIX = "user:token:llm:";

    /**
     * Redis Key 前缀：user:token:embedding:{userId}
     */
    private static final String EMBEDDING_TOKEN_KEY_PREFIX = "user:token:embedding:";

    private final StringRedisTemplate stringRedisTemplate;

    private final UsageQuotaProperties usageQuotaProperties;

    private final UserTokenRecordRepository userTokenRecordRepository;

    private final UserDailyChatCountRepository userDailyChatCountRepository;

    public UserTokenService(StringRedisTemplate stringRedisTemplate,
                            UsageQuotaProperties usageQuotaProperties,
                            UserTokenRecordRepository userTokenRecordRepository, UserDailyChatCountRepository userDailyChatCountRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.usageQuotaProperties = usageQuotaProperties;
        this.userTokenRecordRepository = userTokenRecordRepository;
        this.userDailyChatCountRepository = userDailyChatCountRepository;
    }

    /**
     * 获取用户的 LLM Token 余额
     *
     * @param userId 用户 ID
     * @return LLM Token 余额
     */
    public Long getLlmTokenBalance(String userId) {
        String key = buildLlmTokenKey(userId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(value)) {
            // 新用户，首次进来时查看余额
            long initToken = usageQuotaProperties.getLlm().getInitTokens();
            stringRedisTemplate.opsForValue().set(key, String.valueOf(initToken));
            // 记录 Token 增加
            recordTokenIncrease(userId, UserTokenRecord.TokenType.LLM, initToken, 0L, initToken, "注册赠送", null);
            return initToken;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("无法解析 LLM Token 余额：userId={}, value={}", userId, value);
            return 0L;
        }
    }

    /**
     * 获取用户的 Embedding Token 余额
     *
     * @param userId 用户 ID
     * @return Embedding Token 余额
     */
    public Long getEmbeddingTokenBalance(String userId) {
        String key = buildEmbeddingTokenKey(userId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(value)) {
            long initToken = usageQuotaProperties.getEmbedding().getInitTokens();
            stringRedisTemplate.opsForValue().set(key, String.valueOf(initToken));
            // 添加 Embedding Token 增加记录
            recordTokenIncrease(userId, UserTokenRecord.TokenType.EMBEDDING, initToken, 0L, initToken, "注册赠送", null);
            return initToken;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("无法解析 Embedding Token 余额：userId={}, value={}", userId, value);
            return 0L;
        }
    }

    /**
     * 消耗用户的 LLM Token
     *
     * @param userId 用户 ID
     * @param tokens 消耗的 token 数量
     * @throws CustomException 如果余额不足或用户不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeLlmTokens(String userId, int tokens) {
        if (tokens <= 0) {
            throw new CustomException("消耗的 Token 数量必须大于 0", HttpStatus.BAD_REQUEST);
        }

        String key = buildLlmTokenKey(userId);
        Long currentBalance = getLlmTokenBalance(userId);

        if (currentBalance < tokens) {
            logger.warn("用户 {} 的 LLM token 实际用量超过剩余额度：remain={}, consumer={}", userId, currentBalance, tokens);
        }

        stringRedisTemplate.opsForValue().increment(key, -tokens);
        logger.info("用户 {} 消耗 LLM Token: {}, 剩余：{}", userId, tokens, currentBalance - tokens);

        // 记录 Token 消耗（按天聚合）
        recordTokenConsume(userId, UserTokenRecord.TokenType.LLM, tokens, currentBalance, currentBalance - tokens);
    }

    /**
     * 消耗用户的 Embedding Token
     *
     * @param userId 用户 ID
     * @param tokens 消耗的 token 数量
     * @throws CustomException 如果余额不足或用户不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeEmbeddingTokens(String userId, int tokens) {
        if (tokens <= 0) {
            throw new CustomException("消耗的 Token 数量必须大于 0", HttpStatus.BAD_REQUEST);
        }

        String key = buildEmbeddingTokenKey(userId);
        Long currentBalance = getEmbeddingTokenBalance(userId);

        if (currentBalance < tokens) {
            logger.warn("用户 {} 的 Embedding token 实际用量超过剩余额度：remain={}, consumer={}", userId, currentBalance, tokens);
        }

        stringRedisTemplate.opsForValue().increment(key, -tokens);
        logger.info("用户 {} 消耗 Embedding Token: {}, 剩余：{}", userId, tokens, currentBalance - tokens);

        // 记录 Token 消耗（按天聚合）
        recordTokenConsume(userId, UserTokenRecord.TokenType.EMBEDDING, tokens, currentBalance, currentBalance - tokens);
    }


    // 用于总的请求次数记录
    private String buildQuotaKey(String scope, String userId) {
        return "quota:" + scope + ":requests:user:" + userId;
    }

    /**
     * 为用户增加总的请求次数
     */
    public void incrementUserTotalRequestCount(String scope, String userId) {
        String key = buildQuotaKey(scope, userId);
        stringRedisTemplate.opsForValue().increment(key, 1);
    }

    /**
     * 获取用户总的请求次数
     */
    public long getUserTotalRequestCount(String scope, String userId) {
        String key = buildQuotaKey(scope, userId);
        String val = stringRedisTemplate.opsForValue().get(key);
        return StringUtils.isBlank(val) ? 0 : Long.parseLong(val);
    }

    /**
     * 获取今日 Token 消耗排行榜
     *
     * @param tokenType Token 类型（LLM 或 EMBEDDING）
     * @param limit 返回数量限制
     * @return 今日消耗排行榜
     */
    public List<UserTokenRecord> getTodayTopConsumers(UserTokenRecord.TokenType tokenType, int limit) {
        LocalDate today = LocalDate.now();

        // 直接查询今日各用户的消耗统计并排序，数据库层面完成聚合和排序
        return userTokenRecordRepository.findTodayTopConsumersByTokenType(today, tokenType, Pageable.ofSize(limit));
    }

    /**
     * 为用户增加 LLM Token
     *
     * @param userId 用户 ID
     * @param tokens 增加的 token 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void addLlmTokens(String userId, long tokens) {
        if (tokens <= 0) {
            throw new CustomException("增加的 Token 数量必须大于 0", HttpStatus.BAD_REQUEST);
        }
        String key = buildLlmTokenKey(userId);
        Long currentBalance = getLlmTokenBalance(userId);

        // 记录 Token 增加
        recordTokenIncrease(userId, UserTokenRecord.TokenType.LLM, tokens, currentBalance, currentBalance + tokens, "购买套餐充值", null);

        stringRedisTemplate.opsForValue().increment(key, tokens);
        logger.info("用户 {} 增加 LLM Token: {}, 当前余额：{}", userId, tokens, currentBalance + tokens);
    }

    /**
     * 为用户增加 Embedding Token
     *
     * @param userId 用户 ID
     * @param tokens 增加的 token 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void addEmbeddingTokens(String userId, long tokens) {
        if (tokens <= 0) {
            throw new CustomException("增加的 Token 数量必须大于 0", HttpStatus.BAD_REQUEST);
        }

        String key = buildEmbeddingTokenKey(userId);
        Long currentBalance = getEmbeddingTokenBalance(userId);
        // 记录 Token 增加
        recordTokenIncrease(userId, UserTokenRecord.TokenType.EMBEDDING, tokens, currentBalance, currentBalance + tokens, "购买套餐充值", null);

        stringRedisTemplate.opsForValue().increment(key, tokens);
        logger.info("用户 {} 增加 Embedding Token: {}, 当前余额：{}", userId, tokens, currentBalance + tokens);
    }

    /**
     * 检查用户是否有足够的 LLM Token
     *
     * @param userId 用户 ID
     * @param tokens 需要的 token 数量
     * @return true-余额充足，false-余额不足
     */
    public boolean hasEnoughLlmTokens(String userId, int tokens) {
        Long balance = getLlmTokenBalance(userId);
        return balance >= tokens;
    }

    /**
     * 检查用户是否有足够的 Embedding Token
     *
     * @param userId 用户 ID
     * @param tokens 需要的 token 数量
     * @return true-余额充足，false-余额不足
     */
    public boolean hasEnoughEmbeddingTokens(String userId, int tokens) {
        Long balance = getEmbeddingTokenBalance(userId);
        return balance >= tokens;
    }

    /**
     * 构建 LLM Token Redis Key
     */
    private String buildLlmTokenKey(String userId) {
        return LLM_TOKEN_KEY_PREFIX + userId;
    }

    /**
     * 构建 Embedding Token Redis Key
     */
    private String buildEmbeddingTokenKey(String userId) {
        return EMBEDDING_TOKEN_KEY_PREFIX + userId;
    }

    /**
     * 记录 Token 增加
     */
    private void recordTokenIncrease(String userId,
                                     UserTokenRecord.TokenType tokenType,
                                     Long amount,
                                     Long balanceBefore,
                                     Long balanceAfter,
                                     String reason,
                                     String remark) {
        try {
            UserTokenRecord record = new UserTokenRecord();
            record.setUserId(userId);
            record.setRecordDate(LocalDate.now());
            record.setTokenType(tokenType);
            record.setChangeType(UserTokenRecord.ChangeType.INCREASE);
            record.setAmount(amount);
            record.setBalanceBefore(balanceBefore);
            record.setBalanceAfter(balanceAfter);
            record.setReason(reason == null ? "" : reason);
            record.setRemark(remark == null ? "" : remark);
            record.setRequestCount(0L);

            userTokenRecordRepository.save(record);
            logger.debug("记录 Token 增加：userId={}, type={}, amount={}", userId, tokenType, amount);
        } catch (Exception e) {
            logger.error("记录 Token 增加失败：userId={}, type={}, amount={}", userId, tokenType, amount, e);
        }
    }

    /**
     * 记录 Token 消耗（按天聚合）
     */
    private void recordTokenConsume(String userId,
                                    UserTokenRecord.TokenType tokenType,
                                    Integer amount,
                                    Long balanceBefore,
                                    Long balanceAfter) {
        try {
            LocalDate today = LocalDate.now();

            // 查找当天已存在的消耗记录
            userTokenRecordRepository.findByUserIdAndRecordDateAndTokenTypeAndChangeType(
                    userId, today, tokenType, UserTokenRecord.ChangeType.CONSUME
            ).ifPresentOrElse(
                    // 如果存在，则累加数量和请求次数
                    existingRecord -> {
                        existingRecord.setAmount(existingRecord.getAmount() + amount);
                        existingRecord.setBalanceAfter(balanceAfter);
                        existingRecord.setRequestCount(existingRecord.getRequestCount() + 1);
                        userTokenRecordRepository.save(existingRecord);
                        logger.debug("更新 Token 消耗记录：userId={}, type={}, totalAmount={}, totalRequestCount={}",
                                userId, tokenType, existingRecord.getAmount(), existingRecord.getRequestCount());
                    },
                    // 如果不存在，则创建新记录
                    () -> {
                        UserTokenRecord record = new UserTokenRecord();
                        record.setUserId(userId);
                        record.setRecordDate(today);
                        record.setTokenType(tokenType);
                        record.setChangeType(UserTokenRecord.ChangeType.CONSUME);
                        record.setAmount((long) amount);
                        record.setBalanceBefore(balanceBefore);
                        record.setBalanceAfter(balanceAfter);
                        record.setReason("对话使用");
                        record.setRequestCount(1L);
                        userTokenRecordRepository.save(record);
                        logger.debug("新建 Token 消耗记录：userId={}, type={}, amount={}", userId, tokenType, amount);
                    }
            );
        } catch (Exception e) {
            logger.error("记录 Token 消耗失败：userId={}, type={}, amount={}", userId, tokenType, amount, e);
        }
    }

    /**
     * 分页查询用户 Token 记录
     *
     * @param userId 用户 ID
     * @param page 页码（从 0 开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<UserTokenRecord> getUserTokenRecords(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "recordDate"));
        Page<UserTokenRecord> recordPage = userTokenRecordRepository.findByUserIdOrderByRecordDateDesc(userId, pageable);
        return recordPage;
    }

    public Long getUserLlmTotalIncreaseTokens(String userId) {
        return getUserTotalIncreaseTokens(userId, UserTokenRecord.TokenType.LLM);
    }

    public Long getUserEmbeddingTotalIncreaseTokens(String userId) {
        return getUserTotalIncreaseTokens(userId, UserTokenRecord.TokenType.EMBEDDING);
    }

    /**
     * 获取用户总 Token 增加量
     *
     * @param userId 用户 ID
     * @param tokenType Token 类型
     * @return 总 Token 增加量
     */
    public Long getUserTotalIncreaseTokens(String userId, UserTokenRecord.TokenType tokenType) {
        return userTokenRecordRepository.sumAmountByUserIdAndTokenTypeAndChangeType(
                userId, tokenType, UserTokenRecord.ChangeType.INCREASE
        );
    }


    // 用户的每天对话次数
    public long getUserDailyChatCount(String userId, LocalDate day) {
        Optional<UserDailyChatCount> record = userDailyChatCountRepository.findByUserIdAndRecordDate(userId, day);
        return record.map(UserDailyChatCount::getChatRequestCount).orElse(0L);
    }

    public void updateUserDailyChatCount(String userId, LocalDate day) {
        // 尝试更新或插入当日记录
        userDailyChatCountRepository.findByUserIdAndRecordDate(userId, day)
                .ifPresentOrElse(
                        record -> {
                            record.setChatRequestCount(record.getChatRequestCount() + 1);
                            userDailyChatCountRepository.save(record);
                        },
                        () -> {
                            UserDailyChatCount newRecord = new UserDailyChatCount();
                            newRecord.setUserId(userId);
                            newRecord.setRecordDate(day);
                            newRecord.setChatRequestCount(1L);
                            userDailyChatCountRepository.save(newRecord);
                        }
                );
    }


    /**
     * 根据类型查询每日统计数据
     */
    public List<DailyUsageStat> getDailyStatsByType(
            LocalDate startDate,
            LocalDate endDate,
            UserTokenRecord.TokenType tokenType
    ) {
        return userTokenRecordRepository.findDailyUsageStatsByDateRangeAndTokenType(
                startDate, endDate, tokenType
        );
    }

    public List<DailyReqCountStat> getDailyReqCountStats(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return userDailyChatCountRepository.findDailyChatCountStatsByDateRange(startDate, endDate);
    }

    /**
     * 获取 Token 余额不足的用户列表（用于预警）- 使用 JPQL 子查询
     *
     * @param tokenType Token 类型
     * @param minBalance 最低余额阈值
     * @return 需要预警的用户列表
     */
    public List<UserTokenRecord> getLowBalanceUsers(UserTokenRecord.TokenType tokenType,
                                                    long minBalance) {
        // 直接使用 Repository 的 JPQL 子查询方法
        return userTokenRecordRepository.findUsersWithLowBalance(tokenType, minBalance);
    }
}
