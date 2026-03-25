package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "model_provider_configs",
        indexes = {
                @Index(name = "idx_model_provider_scope", columnList = "config_scope"),
                @Index(name = "idx_model_provider_scope_provider", columnList = "config_scope,provider_code", unique = true)
        }
)
public class ModelProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_scope", nullable = false, length = 32)
    private String configScope;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "api_style", nullable = false, length = 64)
    private String apiStyle;

    @Column(name = "api_base_url", nullable = false, length = 512)
    private String apiBaseUrl;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "api_key_ciphertext", length = 2048)
    private String apiKeyCiphertext;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
