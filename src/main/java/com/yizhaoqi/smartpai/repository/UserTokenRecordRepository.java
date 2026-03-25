package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DailyUsageStat;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 用户 Token 记录 Repository
 *
 * @author YiHui
 * @date 2026/3/18
 */
@Repository
public interface UserTokenRecordRepository extends JpaRepository<UserTokenRecord, Long> {
    /**
     * 查找用户指定日期、指定 Token 类型和变动类型的记录
     */
    Optional<UserTokenRecord> findByUserIdAndRecordDateAndTokenTypeAndChangeType(
            String userId,
            LocalDate recordDate,
            UserTokenRecord.TokenType tokenType,
            UserTokenRecord.ChangeType changeType
    );


    /**
     * 获取用户指定 Token 类型的 Token 总数（求和）
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0L) FROM UserTokenRecord r WHERE r.userId = :userId AND r.tokenType = :tokenType AND r.changeType = :changeType")
    long sumAmountByUserIdAndTokenTypeAndChangeType(
            @Param("userId") String userId,
            @Param("tokenType") UserTokenRecord.TokenType tokenType,
            @Param("changeType") UserTokenRecord.ChangeType changeType
    );

    /**
     * 查询用户最近 N 天指定 Token 类型的消耗统计（按日期分组）
     */
    @Query("SELECT new com.yizhaoqi.smartpai.model.DailyUsageStat( " +
            "r.recordDate, SUM(r.amount), SUM(r.requestCount)) " +
            "FROM UserTokenRecord r " +
            "WHERE r.tokenType = :tokenType AND r.changeType = 'CONSUME' " +
            "AND r.recordDate BETWEEN :startDate AND :endDate " +
            "GROUP BY r.recordDate ORDER BY r.recordDate ASC")
    List<DailyUsageStat> findDailyUsageStatsByDateRangeAndTokenType(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("tokenType") UserTokenRecord.TokenType tokenType
    );

    /**
     * 查询今日各用户的 Token 消耗统计（用于排行榜）
     */
    @Query("SELECT r " +
            "FROM UserTokenRecord r " +
            "WHERE r.tokenType = :tokenType AND r.changeType = 'CONSUME' " +
            "AND r.recordDate = :today " +
            "ORDER BY r.amount DESC")
    List<UserTokenRecord> findTodayTopConsumersByTokenType(
            @Param("today") LocalDate today,
            @Param("tokenType") UserTokenRecord.TokenType tokenType,
            Pageable pageable
    );

    /**
     * 查询用户最新记录中余额不足的记录（使用子查询）
     */
    @Query("SELECT u " +
            "FROM UserTokenRecord u " +
            "WHERE u.tokenType = :tokenType " +
            "AND u.changeType = 'CONSUME' " +
            "AND u.balanceAfter <= :minBalance " +
            "AND u.recordDate = (" +
            "    SELECT MAX(ur.recordDate) " +
            "    FROM UserTokenRecord ur " +
            "    WHERE ur.userId = u.userId " +
            "    AND ur.tokenType = :tokenType " +
            "    AND ur.changeType = 'CONSUME'" +
            ")")
    List<UserTokenRecord> findUsersWithLowBalance(
            @Param("tokenType") UserTokenRecord.TokenType tokenType,
            @Param("minBalance") long minBalance
    );

    /**
     * 分页查询用户所有 Token 记录
     */
    Page<UserTokenRecord> findByUserIdOrderByRecordDateDesc(String userId, Pageable pageable);
}
