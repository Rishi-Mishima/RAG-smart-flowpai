package com.yizhaoqi.smartpai.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MinIO 文件迁移工具
 * 将旧路径（文件名）迁移到新路径（MD5）
 *
 * 使用方法：
 * 1. 在 Controller 或 Service 中注入此工具
 * 2. 调用 migrateAllFiles() 方法
 * 3. 观察日志输出
 *
 * 注意：迁移完成后可以删除此工具类
 */
@Component
public class MinioMigrationUtil {

    private static final Logger logger = LoggerFactory.getLogger(MinioMigrationUtil.class);

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private ElasticsearchClient esClient;

    /**
     * 迁移所有文件从旧路径到新路径
     *
     * @return 迁移报告
     */
    public MigrationReport migrateAllFiles() {
        MigrationReport report = new MigrationReport();

        logger.info("========================================");
        logger.info("开始 MinIO 文件迁移");
        logger.info("========================================");

        try {
            // 获取所有已完成上传的文件
            List<FileUpload> allFiles = fileUploadRepository.findAll();
            logger.info("找到 {} 个文件记录", allFiles.size());

            for (FileUpload file : allFiles) {
                migrateFile(file, report);
            }

            logger.info("========================================");
            logger.info("迁移完成");
            logger.info("成功: {}, 跳过: {}, 失败: {}",
                report.successCount, report.skipCount, report.errorCount);
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("迁移过程中发生错误", e);
        }

        return report;
    }

    /**
     * 迁移单个文件
     */
    private void migrateFile(FileUpload file, MigrationReport report) {
        String oldPath = "merged/" + file.getFileName();
        String newPath = "merged/" + file.getFileMd5();

        logger.info("处理文件: {} (MD5: {})", file.getFileName(), file.getFileMd5());

        try {
            // 1. 检查旧路径是否存在
            if (!objectExists(oldPath)) {
                logger.debug("  旧路径不存在，可能已迁移: {}", oldPath);
                report.skipCount++;
                return;
            }

            // 2. 检查新路径是否已存在
            if (objectExists(newPath)) {
                logger.warn("  新路径已存在，删除旧路径: {}", newPath);
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket("uploads")
                        .object(oldPath)
                        .build()
                );
                report.skipCount++;
                return;
            }

            // 3. 复制文件到新路径
            logger.debug("  复制: {} -> {}", oldPath, newPath);
            minioClient.copyObject(
                CopyObjectArgs.builder()
                    .bucket("uploads")
                    .object(newPath)
                    .source(
                        CopySource.builder()
                            .bucket("uploads")
                            .object(oldPath)
                            .build()
                    )
                    .build()
            );

            // 4. 删除旧路径
            logger.debug("  删除旧路径: {}", oldPath);
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket("uploads")
                    .object(oldPath)
                    .build()
            );

            logger.info("  ✅ 迁移成功");
            report.successCount++;

        } catch (Exception e) {
            logger.error("  ❌ 迁移失败: {}", e.getMessage());
            report.errorCount++;
            report.addError(file.getFileName(), e.getMessage());
        }
    }

    /**
     * 检查对象是否存在
     */
    private boolean objectExists(String objectPath) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket("uploads")
                    .object(objectPath)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清空所有数据（危险操作，仅用于测试环境）
     */
    public void clearAllData() {
        logger.warn("========================================");
        logger.warn("开始清空所有数据");
        logger.warn("========================================");

        try {
            // 1. 清空 ElasticSearch
            logger.info("清空 ElasticSearch 索引...");
            DeleteByQueryRequest deleteRequest = DeleteByQueryRequest.of(d -> d
                .index("knowledge_base")
                .query(Query.of(q -> q.matchAll(m -> m)))
            );
            esClient.deleteByQuery(deleteRequest);
            logger.info("✅ ElasticSearch 已清空");

            // 2. 清空 MySQL 表
            logger.info("清空 MySQL 表...");
            fileUploadRepository.deleteAll();
            logger.info("✅ MySQL 表已清空");

            // 3. 清空 MinIO merged 目录
            logger.info("清空 MinIO merged 目录...");
            List<FileUpload> files = fileUploadRepository.findAll();
            for (FileUpload file : files) {
                try {
                    minioClient.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket("uploads")
                            .object("merged/" + file.getFileMd5())
                            .build()
                    );
                } catch (Exception e) {
                    // 忽略错误
                }
                try {
                    minioClient.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket("uploads")
                            .object("merged/" + file.getFileName())
                            .build()
                    );
                } catch (Exception e) {
                    // 忽略错误
                }
            }
            logger.info("✅ MinIO merged 目录已清空");

            logger.warn("========================================");
            logger.warn("所有数据已清空");
            logger.warn("========================================");

        } catch (Exception e) {
            logger.error("清空数据时出错", e);
        }
    }

    /**
     * 迁移报告
     */
    public static class MigrationReport {
        public int successCount = 0;
        public int skipCount = 0;
        public int errorCount = 0;
        private StringBuilder errors = new StringBuilder();

        public void addError(String fileName, String error) {
            errors.append(String.format("  - %s: %s\n", fileName, error));
        }

        public String getErrors() {
            return errors.length() > 0 ? errors.toString() : "无错误";
        }

        @Override
        public String toString() {
            return String.format(
                "迁移报告:\n  成功: %d\n  跳过: %d\n  失败: %d\n错误详情:\n%s",
                successCount, skipCount, errorCount, getErrors()
            );
        }
    }
}
