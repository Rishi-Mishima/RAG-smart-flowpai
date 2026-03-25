package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RateLimitConfig;
import com.yizhaoqi.smartpai.repository.RateLimitConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitConfigService {

    private static final String CHAT_MESSAGE = "chat-message";
    private static final String LLM_GLOBAL_TOKEN = "llm-global-token";
    private static final String EMBEDDING_UPLOAD_TOKEN = "embedding-upload-token";
    private static final String EMBEDDING_QUERY_REQUEST = "embedding-query-request";
    private static final String EMBEDDING_QUERY_GLOBAL_TOKEN = "embedding-query-global-token";

    private final RateLimitProperties properties;
    private final RateLimitConfigRepository rateLimitConfigRepository;

    private volatile RateLimitSettingsView currentSettings;

    public RateLimitConfigService(RateLimitProperties properties, RateLimitConfigRepository rateLimitConfigRepository) {
        this.properties = properties;
        this.rateLimitConfigRepository = rateLimitConfigRepository;
        this.currentSettings = buildDefaultSettings();
    }

    @PostConstruct
    public void loadPersistedConfigs() {
        currentSettings = mergeOverrides(buildDefaultSettings(), rateLimitConfigRepository.findAll());
    }

    public RateLimitSettingsView getCurrentSettings() {
        return currentSettings;
    }

    public synchronized RateLimitSettingsView updateSettings(UpdateRateLimitRequest request, String updatedBy) {
        if (request == null) {
            throw new CustomException("限流配置不能为空", HttpStatus.BAD_REQUEST);
        }

        validateWindowLimit(request.chatMessage(), "聊天消息");
        validateTokenBudgetLimit(request.llmGlobalToken(), "LLM 全网 Token");
        validateTokenBudgetLimit(request.embeddingUploadToken(), "Embedding 上传 Token");
        validateDualWindowLimit(request.embeddingQueryRequest(), "Embedding 查询次数");
        validateTokenBudgetLimit(request.embeddingQueryGlobalToken(), "Embedding 查询全网 Token");

        persistWindowLimit(CHAT_MESSAGE, request.chatMessage(), updatedBy);
        persistTokenBudgetLimit(LLM_GLOBAL_TOKEN, request.llmGlobalToken(), updatedBy);
        persistTokenBudgetLimit(EMBEDDING_UPLOAD_TOKEN, request.embeddingUploadToken(), updatedBy);
        persistDualWindowLimit(EMBEDDING_QUERY_REQUEST, request.embeddingQueryRequest(), updatedBy);
        persistTokenBudgetLimit(EMBEDDING_QUERY_GLOBAL_TOKEN, request.embeddingQueryGlobalToken(), updatedBy);

        currentSettings = new RateLimitSettingsView(
                request.chatMessage(),
                request.llmGlobalToken(),
                request.embeddingUploadToken(),
                request.embeddingQueryRequest(),
                request.embeddingQueryGlobalToken()
        );
        return currentSettings;
    }

    private RateLimitSettingsView buildDefaultSettings() {
        return new RateLimitSettingsView(
                new WindowLimitView(
                        properties.getChatMessage().getMax(),
                        properties.getChatMessage().getWindowSeconds()
                ),
                new TokenBudgetView(
                        properties.getLlmGlobalToken().getMinuteMax(),
                        properties.getLlmGlobalToken().getMinuteWindowSeconds(),
                        properties.getLlmGlobalToken().getDayMax(),
                        properties.getLlmGlobalToken().getDayWindowSeconds()
                ),
                new TokenBudgetView(
                        properties.getEmbeddingUploadToken().getMinuteMax(),
                        properties.getEmbeddingUploadToken().getMinuteWindowSeconds(),
                        properties.getEmbeddingUploadToken().getDayMax(),
                        properties.getEmbeddingUploadToken().getDayWindowSeconds()
                ),
                new DualWindowLimitView(
                        properties.getEmbeddingQueryRequest().getMinuteMax(),
                        properties.getEmbeddingQueryRequest().getMinuteWindowSeconds(),
                        properties.getEmbeddingQueryRequest().getDayMax(),
                        properties.getEmbeddingQueryRequest().getDayWindowSeconds()
                ),
                new TokenBudgetView(
                        properties.getEmbeddingQueryGlobalToken().getMinuteMax(),
                        properties.getEmbeddingQueryGlobalToken().getMinuteWindowSeconds(),
                        properties.getEmbeddingQueryGlobalToken().getDayMax(),
                        properties.getEmbeddingQueryGlobalToken().getDayWindowSeconds()
                )
        );
    }

    private RateLimitSettingsView mergeOverrides(RateLimitSettingsView defaults, List<RateLimitConfig> configs) {
        WindowLimitView chatMessage = defaults.chatMessage();
        TokenBudgetView llmGlobalToken = defaults.llmGlobalToken();
        TokenBudgetView embeddingUploadToken = defaults.embeddingUploadToken();
        DualWindowLimitView embeddingQueryRequest = defaults.embeddingQueryRequest();
        TokenBudgetView embeddingQueryGlobalToken = defaults.embeddingQueryGlobalToken();

        for (RateLimitConfig config : configs) {
            if (config == null || config.getConfigKey() == null) {
                continue;
            }

            switch (config.getConfigKey()) {
                case CHAT_MESSAGE -> {
                    if (config.getSingleMax() != null && config.getSingleWindowSeconds() != null) {
                        chatMessage = new WindowLimitView(config.getSingleMax(), config.getSingleWindowSeconds());
                    }
                }
                case LLM_GLOBAL_TOKEN -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        llmGlobalToken = new TokenBudgetView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                case EMBEDDING_UPLOAD_TOKEN -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        embeddingUploadToken = new TokenBudgetView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                case EMBEDDING_QUERY_REQUEST -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        embeddingQueryRequest = new DualWindowLimitView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                case EMBEDDING_QUERY_GLOBAL_TOKEN -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        embeddingQueryGlobalToken = new TokenBudgetView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                default -> {
                    // Ignore unknown config rows so future expansions stay backward compatible.
                }
            }
        }

        return new RateLimitSettingsView(chatMessage, llmGlobalToken, embeddingUploadToken, embeddingQueryRequest, embeddingQueryGlobalToken);
    }

    private void persistWindowLimit(String key, WindowLimitView limit, String updatedBy) {
        RateLimitConfig config = rateLimitConfigRepository.findById(key).orElseGet(RateLimitConfig::new);
        config.setConfigKey(key);
        config.setSingleMax(limit.max());
        config.setSingleWindowSeconds(limit.windowSeconds());
        config.setMinuteMax(null);
        config.setMinuteWindowSeconds(null);
        config.setDayMax(null);
        config.setDayWindowSeconds(null);
        config.setUpdatedBy(updatedBy);
        rateLimitConfigRepository.save(config);
    }

    private void persistDualWindowLimit(String key, DualWindowLimitView limit, String updatedBy) {
        RateLimitConfig config = rateLimitConfigRepository.findById(key).orElseGet(RateLimitConfig::new);
        config.setConfigKey(key);
        config.setSingleMax(null);
        config.setSingleWindowSeconds(null);
        config.setMinuteMax(limit.minuteMax());
        config.setMinuteWindowSeconds(limit.minuteWindowSeconds());
        config.setDayMax(limit.dayMax());
        config.setDayWindowSeconds(limit.dayWindowSeconds());
        config.setUpdatedBy(updatedBy);
        rateLimitConfigRepository.save(config);
    }

    private void persistTokenBudgetLimit(String key, TokenBudgetView limit, String updatedBy) {
        RateLimitConfig config = rateLimitConfigRepository.findById(key).orElseGet(RateLimitConfig::new);
        config.setConfigKey(key);
        config.setSingleMax(null);
        config.setSingleWindowSeconds(null);
        config.setMinuteMax(limit.minuteMax());
        config.setMinuteWindowSeconds(limit.minuteWindowSeconds());
        config.setDayMax(limit.dayMax());
        config.setDayWindowSeconds(limit.dayWindowSeconds());
        config.setUpdatedBy(updatedBy);
        rateLimitConfigRepository.save(config);
    }

    private void validateWindowLimit(WindowLimitView limit, String label) {
        if (limit == null) {
            throw new CustomException(label + "配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (limit.max() <= 0 || limit.windowSeconds() <= 0) {
            throw new CustomException(label + "配置必须为大于 0 的整数", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDualWindowLimit(DualWindowLimitView limit, String label) {
        if (limit == null) {
            throw new CustomException(label + "配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (limit.minuteMax() <= 0 || limit.minuteWindowSeconds() <= 0
                || limit.dayMax() <= 0 || limit.dayWindowSeconds() <= 0) {
            throw new CustomException(label + "配置必须为大于 0 的整数", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayMax() < limit.minuteMax()) {
            throw new CustomException(label + "日限额不能小于分钟限额", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayWindowSeconds() < limit.minuteWindowSeconds()) {
            throw new CustomException(label + "日窗口不能小于分钟窗口", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateTokenBudgetLimit(TokenBudgetView limit, String label) {
        if (limit == null) {
            throw new CustomException(label + "配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (limit.minuteMax() <= 0 || limit.minuteWindowSeconds() <= 0
                || limit.dayMax() <= 0 || limit.dayWindowSeconds() <= 0) {
            throw new CustomException(label + "配置必须为大于 0 的整数", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayMax() < limit.minuteMax()) {
            throw new CustomException(label + "日预算不能小于分钟预算", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayWindowSeconds() < limit.minuteWindowSeconds()) {
            throw new CustomException(label + "日窗口不能小于分钟窗口", HttpStatus.BAD_REQUEST);
        }
    }

    public record WindowLimitView(int max, long windowSeconds) {
    }

    public record DualWindowLimitView(long minuteMax, long minuteWindowSeconds, long dayMax, long dayWindowSeconds) {
    }

    public record TokenBudgetView(long minuteMax, long minuteWindowSeconds, long dayMax, long dayWindowSeconds) {
    }

    public record RateLimitSettingsView(
            WindowLimitView chatMessage,
            TokenBudgetView llmGlobalToken,
            TokenBudgetView embeddingUploadToken,
            DualWindowLimitView embeddingQueryRequest,
            TokenBudgetView embeddingQueryGlobalToken
    ) {
    }

    public record UpdateRateLimitRequest(
            WindowLimitView chatMessage,
            TokenBudgetView llmGlobalToken,
            TokenBudgetView embeddingUploadToken,
            DualWindowLimitView embeddingQueryRequest,
            TokenBudgetView embeddingQueryGlobalToken
    ) {
    }
}
