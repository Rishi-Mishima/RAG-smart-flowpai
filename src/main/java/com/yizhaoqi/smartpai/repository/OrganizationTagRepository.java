package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.OrganizationTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
    Optional<OrganizationTag> findByTagId(String tagId);
    List<OrganizationTag> findByParentTag(String parentTag);
    boolean existsByTagId(String tagId);
} 