package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 文件上传实体类
 * 用于表示文件上传的相关信息
 */
@Data
@Entity
@Table(name = "file_upload")
public class FileUpload {
    /**
     * 文件的唯一标识符
     * 使用文件的MD5值来唯一确定一个文件
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 自增主键

    @Column(name = "file_md5", length = 32, nullable = false)
    private String fileMd5;

    /**
     * 文件的原始名称
     * 用于记录上传时文件的名称
     */
    private String fileName;

    /**
     * 文件的总大小
     * 以字节为单位记录文件的大小
     */
    private long totalSize;

    /**
     * 文件上传的状态
     * 0表示文件正在上传中，1表示文件上传已完成
     */
    private int status; // 0-上传中 1-已完成

    /**
     * 上传文件的用户的标识符
     * 用于记录哪个用户上传了文件
     */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
    
    /**
     * 文件所属组织标签
     * 用于标识文件归属的组织，支持基于组织标签的权限控制
     */
    @Column(name = "org_tag")
    private String orgTag;

    /**
     * 文件是否公开
     * true表示所有用户可访问，false表示仅组织内用户可访问
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Column(name = "estimated_embedding_tokens")
    private Long estimatedEmbeddingTokens;

    @Column(name = "estimated_chunk_count")
    private Integer estimatedChunkCount;

    @Column(name = "actual_embedding_tokens")
    private Long actualEmbeddingTokens;

    @Column(name = "actual_chunk_count")
    private Integer actualChunkCount;

    /**
     * 文件上传的创建时间
     * 自动记录文件上传开始的时间
     */
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * 文件合并完成的时间
     * 当文件上传状态为已完成时，自动记录完成的时间
     */
    @UpdateTimestamp
    private LocalDateTime mergedAt;
}
