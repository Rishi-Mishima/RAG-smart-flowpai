package com.yizhaoqi.smartpai.config;

import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 确保在 BootstrapKnowledgeInitializer 之前进行初始化
@Order(2)
@Component
@ConditionalOnProperty(name = "elasticsearch.init.enabled", havingValue = "true", matchIfMissing = true)
public class EsIndexInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EsIndexInitializer.class);

    @Autowired
    private ElasticsearchClient esClient;

    @Value("classpath:es-mappings/knowledge_base.json") // 加载 JSON 文件
    private org.springframework.core.io.Resource mappingResource;

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    @Value("${elasticsearch.scheme:https}")
    private String scheme;

    @Value("${elasticsearch.username:elastic}")
    private String username;

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("正在初始化索引 'knowledge_base'... endpoint={}://{}:{}, username={}",
                    scheme, host, port, maskUsername(username));
            initializeIndex();
        } catch (Exception exception) {
            // 特别处理连接关闭异常，尝试重新连接
            if (exception instanceof ConnectionClosedException || (exception.getCause() != null && exception.getCause() instanceof ConnectionClosedException)) {
                logger.error("Elasticsearch连接已关闭，等待5秒后重试...");
                try {
                    Thread.sleep(5000); // 等待5秒后重试
                    // 重新尝试初始化索引
                    initializeIndex();
                } catch (Exception retryException) {
                    String diagnostic = buildDiagnosticMessage(retryException);
                    logger.error("重试初始化索引失败。{}", diagnostic, retryException);
                    throw new RuntimeException("初始化索引失败，重试也未能成功。" + diagnostic, retryException);
                }
            } else {
                String diagnostic = buildDiagnosticMessage(exception);
                logger.error("初始化索引失败。{}", diagnostic, exception);
                throw new RuntimeException("初始化索引失败。" + diagnostic, exception);
            }
        }
    }

    /**
     * 初始化索引的核心逻辑
     * @throws Exception
     */
    private void initializeIndex() throws Exception {
        // 检查索引是否存在
        BooleanResponse existsResponse = esClient.indices().exists(ExistsRequest.of(e -> e.index("knowledge_base")));
        if (!existsResponse.value()) {
            createIndex();
        } else {
            logger.info("索引 'knowledge_base' 已存在");
        }
    }

    /**
     * 创建索引
     * @throws Exception
     */
    private void createIndex() throws Exception {
        // 读取 JSON 文件内容，使用 InputStream 方式支持 JAR 包内资源
        String mappingJson;
        try (var inputStream = mappingResource.getInputStream()) {
            mappingJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 创建索引并应用映射
        CreateIndexRequest createIndexRequest = CreateIndexRequest.of(c -> c
                .index("knowledge_base") // 索引名称
                .withJson(new StringReader(mappingJson)) // 使用 JSON 文件定义映射
        );
        esClient.indices().create(createIndexRequest);
        logger.info("索引 'knowledge_base' 已创建");
    }

    private String buildDiagnosticMessage(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String rootMessage = safeMessage(rootCause);
        String normalizedMessage = rootMessage.toLowerCase(Locale.ROOT);
        String endpoint = scheme + "://" + host + ":" + port;
        List<String> hints = new ArrayList<>();

        hints.add("ES地址=" + endpoint);

        if (isConnectionProblem(rootCause, normalizedMessage)) {
            hints.add("当前看起来是连接失败，请先确认 Elasticsearch 已启动，并且 " + endpoint + " 能访问");
        }

        if (isSslMismatch(normalizedMessage)) {
            hints.add("当前更像是 HTTP/HTTPS 协议不匹配，请核对 ELASTICSEARCH_SCHEME 与实际 ES 配置");
        }

        if (isAuthenticationProblem(normalizedMessage)) {
            hints.add("当前更像是账号或密码不正确，请核对 ELASTICSEARCH_USERNAME / ELASTICSEARCH_PASSWORD");
        }

        if (normalizedMessage.contains("ik_max_word") || normalizedMessage.contains("ik_smart")) {
            hints.add("当前索引 mapping 依赖 IK 分词器，请确认 ES 已安装 analysis-ik 插件");
        }

        if (normalizedMessage.contains("dense_vector") && normalizedMessage.contains("dims")) {
            hints.add("当前更像是向量字段维度不匹配，请确认 embedding.dimension 与索引 mapping 中的 dims 一致");
        }

        if (hints.size() == 1) {
            hints.add("请查看根因异常后再排查 ES 协议、端口、认证和 mapping 配置");
        }

        return " 根因类型=" + rootCause.getClass().getSimpleName()
                + "，根因信息=" + rootMessage
                + "。排查建议：" + String.join("；", hints);
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isConnectionProblem(Throwable rootCause, String normalizedMessage) {
        return rootCause instanceof ConnectException
                || normalizedMessage.contains("connection refused")
                || normalizedMessage.contains("connect timed out")
                || normalizedMessage.contains("connection timed out")
                || normalizedMessage.contains("failed to connect")
                || normalizedMessage.contains("no such host")
                || normalizedMessage.contains("unknownhost")
                || normalizedMessage.contains("no reachable node")
                || normalizedMessage.contains("connection reset");
    }

    private boolean isSslMismatch(String normalizedMessage) {
        return normalizedMessage.contains("pkix")
                || normalizedMessage.contains("ssl")
                || normalizedMessage.contains("tls")
                || normalizedMessage.contains("handshake")
                || normalizedMessage.contains("plaintext connection")
                || normalizedMessage.contains("unrecognized ssl message");
    }

    private boolean isAuthenticationProblem(String normalizedMessage) {
        return normalizedMessage.contains("security_exception")
                || normalizedMessage.contains("authentication")
                || normalizedMessage.contains("unauthorized")
                || normalizedMessage.contains("status line [http/1.1 401")
                || normalizedMessage.contains("status line [http/1.1 403");
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return (message == null || message.isBlank()) ? "<无异常消息>" : message;
    }

    private String maskUsername(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return "<empty>";
        }
        if (rawUsername.length() <= 2) {
            return rawUsername.charAt(0) + "*";
        }
        return rawUsername.charAt(0) + "***" + rawUsername.charAt(rawUsername.length() - 1);
    }
}
