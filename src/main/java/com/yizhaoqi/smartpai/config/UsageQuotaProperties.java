package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "usage-quota")
public class UsageQuotaProperties {

    private int retentionDays = 35;
    
    /**
     * Token 管理模式：true-使用用户全局 Token 余额模式，false-使用每日配额模式
     */
    private boolean useUserTokenBalance = false;
    
    private DailyTokenQuota llm = new DailyTokenQuota(true, 300_000);
    private DailyTokenQuota embedding = new DailyTokenQuota(true, 1_000_000);

    @Data
    public static class DailyTokenQuota {
        private boolean enabled = true;
        private long dayMaxTokens;
        private long initTokens;

        public DailyTokenQuota() {
        }

        public DailyTokenQuota(boolean enabled, long dayMaxTokens) {
            this.enabled = enabled;
            this.dayMaxTokens = dayMaxTokens;
        }
    }
}
