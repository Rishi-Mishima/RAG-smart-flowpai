package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DailyReqCountStat;
import com.yizhaoqi.smartpai.model.UserDailyChatCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 用户每日对话次数 Repository
 *
 * @author YiHui
 * @date 2026/3/18
 */
@Repository
public interface UserDailyChatCountRepository extends JpaRepository<UserDailyChatCount, Long> {

    /**
     * 查找用户指定日期的对话记录
     */
    Optional<UserDailyChatCount> findByUserIdAndRecordDate(String userId, LocalDate recordDate);

    /**
     * 查询用户最近 N 天指定 Token 类型的消耗统计（按日期分组）
     */
    @Query("SELECT new com.yizhaoqi.smartpai.model.DailyReqCountStat( " +
            "r.recordDate, SUM(r.chatRequestCount)) " +
            "FROM UserDailyChatCount r " +
            "WHERE r.recordDate BETWEEN :startDate AND :endDate " +
            "GROUP BY r.recordDate ORDER BY r.recordDate ASC")
    List<DailyReqCountStat> findDailyChatCountStatsByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


}

