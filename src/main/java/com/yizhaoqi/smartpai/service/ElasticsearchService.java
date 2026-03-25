package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.yizhaoqi.smartpai.entity.EsDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// Elasticsearch操作封装服务
@Service
public class ElasticsearchService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);

    @Autowired
    private ElasticsearchClient esClient;

    /**
     * 批量索引文档到Elasticsearch中
     * 通过接收一个EsDocument对象列表，将这些文档批量索引到名为"knowledge_base"的索引中
     * 使用Elasticsearch的Bulk API来执行批量索引操作，以提高索引效率
     *
     * @param documents 文档列表，每个文档都将被索引到Elasticsearch中
     */
    public void bulkIndex(List<EsDocument> documents) {
        try {
            logger.info("开始批量索引文档到Elasticsearch，文档数量: {}", documents.size());
            
            // 将文档列表转换为批量操作列表，每个文档都对应一个索引操作
            List<BulkOperation> bulkOperations = documents.stream()
                    .map(doc -> BulkOperation.of(op -> op.index(idx -> idx
                            .index("knowledge_base") // 指定索引名称
                            .id(doc.getId()) // 使用文档的ID作为Elasticsearch中的文档ID
                            .document(doc) // 将文档对象作为数据源
                    )))
                    .toList();

            // 创建BulkRequest对象，并将批量操作列表添加到请求中
            BulkRequest request = BulkRequest.of(b -> b.operations(bulkOperations));
            
            // 执行批量索引操作
            BulkResponse response = esClient.bulk(request);
            
            // 检查响应结果
            if (response.errors()) {
                logger.error("批量索引过程中发生错误:");
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        logger.error("文档索引失败 - ID: {}, 错误: {}", item.id(), item.error().reason());
                    }
                }
                throw new RuntimeException("批量索引部分失败，请检查日志");
            } else {
                logger.info("批量索引成功完成，文档数量: {}", documents.size());
            }
        } catch (Exception e) {
            logger.error("批量索引失败，文档数量: {}", documents.size(), e);
            // 如果发生异常，抛出运行时异常，表明批量索引失败
            throw new RuntimeException("批量索引失败", e);
        }
    }

    /**
     * 根据file_md5删除文档
     * @param fileMd5 文件指纹
     */
    public void deleteByFileMd5(String fileMd5) {
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index("knowledge_base")
                    .query(q -> q.term(t -> t.field("fileMd5").value(fileMd5)))
            );
            esClient.deleteByQuery(request);
        } catch (Exception e) {
            throw new RuntimeException("删除文档失败", e);
        }
    }

    public long countByFileMd5(String fileMd5) {
        try {
            CountResponse response = esClient.count(c -> c
                    .index("knowledge_base")
                    .query(q -> q.term(t -> t.field("fileMd5").value(fileMd5)))
            );
            return response.count();
        } catch (Exception e) {
            throw new RuntimeException("统计文档失败", e);
        }
    }
}
