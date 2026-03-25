package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.ModelProviderConfig;
import com.yizhaoqi.smartpai.repository.ModelProviderConfigRepository;
import com.yizhaoqi.smartpai.utils.SecretCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ModelProviderConfigServiceTest {

    private final Map<String, ModelProviderConfig> store = new LinkedHashMap<>();

    private ModelProviderConfigRepository repository;
    private ModelProviderConfigService service;

    @BeforeEach
    void setUp() {
        store.clear();
        repository = Mockito.mock(ModelProviderConfigRepository.class);

        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(store.values()));
        when(repository.findByConfigScopeOrderByProviderCodeAsc(any())).thenAnswer(invocation -> {
            String scope = invocation.getArgument(0, String.class);
            return store.values().stream()
                    .filter(item -> scope.equals(item.getConfigScope()))
                    .sorted((left, right) -> left.getProviderCode().compareTo(right.getProviderCode()))
                    .toList();
        });
        when(repository.findByConfigScopeAndProviderCode(any(), any())).thenAnswer(invocation -> {
            String scope = invocation.getArgument(0, String.class);
            String provider = invocation.getArgument(1, String.class);
            return Optional.ofNullable(store.get(scope + ":" + provider));
        });
        when(repository.findByConfigScopeAndActiveTrue(any())).thenAnswer(invocation -> {
            String scope = invocation.getArgument(0, String.class);
            return store.values().stream()
                    .filter(item -> scope.equals(item.getConfigScope()) && item.isActive())
                    .findFirst();
        });
        when(repository.save(any(ModelProviderConfig.class))).thenAnswer(invocation -> {
            ModelProviderConfig entity = invocation.getArgument(0, ModelProviderConfig.class);
            store.put(entity.getConfigScope() + ":" + entity.getProviderCode(), entity);
            return entity;
        });

        SecretCryptoService secretCryptoService = new SecretCryptoService();
        ReflectionTestUtils.setField(secretCryptoService, "base64Secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        secretCryptoService.init();

        service = new ModelProviderConfigService(repository, secretCryptoService);
        ReflectionTestUtils.setField(service, "deepSeekApiUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(service, "deepSeekApiKey", "sk-default-deepseek");
        ReflectionTestUtils.setField(service, "deepSeekModel", "deepseek-chat");
        ReflectionTestUtils.setField(service, "embeddingApiUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        ReflectionTestUtils.setField(service, "embeddingApiKey", "sk-default-embedding");
        ReflectionTestUtils.setField(service, "embeddingModel", "text-embedding-v4");
        ReflectionTestUtils.setField(service, "embeddingDimension", 2048);
        service.reloadSettings();
    }

    @Test
    void shouldExposeDefaultProviders() {
        ModelProviderConfigService.ModelProviderSettingsView settings = service.getCurrentSettings();

        assertEquals("deepseek", settings.llm().activeProvider());
        assertEquals("aliyun", settings.embedding().activeProvider());
        assertEquals(3, settings.llm().providers().size());
        assertEquals(2, settings.embedding().providers().size());
        assertTrue(settings.llm().providers().stream().anyMatch(item -> item.provider().equals("qwen")));
        assertTrue(settings.embedding().providers().stream().anyMatch(item -> item.provider().equals("zhipu")));
    }

    @Test
    void shouldUpdateActiveLlmProviderAndPersistEncryptedApiKey() {
        ModelProviderConfigService.UpdateScopeRequest request = new ModelProviderConfigService.UpdateScopeRequest(
                "qwen",
                List.of(
                        new ModelProviderConfigService.ProviderUpsertRequest("deepseek", "https://api.deepseek.com/v1", "deepseek-chat", "", null, true),
                        new ModelProviderConfigService.ProviderUpsertRequest("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-flash", "sk-qwen-updated", null, true),
                        new ModelProviderConfigService.ProviderUpsertRequest("zhipu", "https://open.bigmodel.cn/api/paas/v4", "glm-4.5-air", "", null, true)
                )
        );

        ModelProviderConfigService.ScopeSettingsView updated = service.updateScope(ModelProviderConfigService.SCOPE_LLM, request, "admin");
        ModelProviderConfigService.ActiveProviderView activeProvider = service.getActiveProvider(ModelProviderConfigService.SCOPE_LLM);

        assertEquals("qwen", updated.activeProvider());
        assertEquals("qwen", activeProvider.provider());
        assertEquals("qwen-flash", activeProvider.model());
        assertEquals("sk-qwen-updated", activeProvider.apiKey());

        ModelProviderConfig persisted = store.get("llm:qwen");
        assertNotNull(persisted.getApiKeyCiphertext());
        assertFalse(persisted.getApiKeyCiphertext().contains("sk-qwen-updated"));
    }

    @Test
    void shouldRejectUnsafeEmbeddingProviderSwitch() {
        ModelProviderConfigService.UpdateScopeRequest request = new ModelProviderConfigService.UpdateScopeRequest(
                "zhipu",
                List.of(
                        new ModelProviderConfigService.ProviderUpsertRequest("aliyun", "https://dashscope.aliyuncs.com/compatible-mode/v1", "text-embedding-v4", "", 2048, true),
                        new ModelProviderConfigService.ProviderUpsertRequest("zhipu", "https://open.bigmodel.cn/api/paas/v4", "embedding-3", "", 2048, true)
                )
        );

        CustomException exception = assertThrows(CustomException.class,
                () -> service.updateScope(ModelProviderConfigService.SCOPE_EMBEDDING, request, "admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertTrue(exception.getMessage().contains("重嵌入"));
    }
}
