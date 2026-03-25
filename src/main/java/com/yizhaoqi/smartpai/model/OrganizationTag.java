package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "organization_tags")
public class OrganizationTag {
    @Id
    @Column(name = "tag_id")
    private String tagId; // 标签唯一标识

    @Column(nullable = false)
    private String name; // 标签名称

    @Column(columnDefinition = "TEXT")
    private String description; // 描述

    @Column(name = "parent_tag", length = 255)
    private String parentTag; // 父标签ID

    @Column(name = "upload_max_size_bytes")
    private Long uploadMaxSizeBytes; // 非管理员上传文件大小上限，null 表示不限制

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 创建者ID

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间
} 
