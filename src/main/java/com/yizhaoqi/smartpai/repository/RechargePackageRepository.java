package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.RechargePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 充值套餐数据访问层
 * @author YiHui
 * @date 2026/3/18
 */
@Repository
public interface RechargePackageRepository extends JpaRepository<RechargePackage, Integer> {

    /**
     * 查询所有启用的套餐（未删除）
     */
    List<RechargePackage> findAllByEnabledTrueAndDeletedFalseOrderBySortOrderAsc();

    /**
     * 查询面向用户展示的启用套餐，排除内部最小金额基准套餐。
     */
    List<RechargePackage> findAllByEnabledTrueAndDeletedFalseAndPackagePriceGreaterThanOrderBySortOrderAsc(Long price);

    /**
     * 查询所有套餐（包含禁用，不包含已删除）
     */
    List<RechargePackage> findAllByDeletedFalseOrderBySortOrderAsc();

    /**
     * 根据价格查询，用于查询一分钱对应的套餐，用于计算用户输入自定义充值金额的场景
     */
    Optional<RechargePackage> findByPackagePriceAndEnabledIsTrueAndDeletedFalse(Integer price);

    /**
     * 根据套餐 ID 查询（确保未删除）
     */
    Optional<RechargePackage> findById(Integer id);
}
