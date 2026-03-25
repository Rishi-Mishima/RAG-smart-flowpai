package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.InviteCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteCode i where i.code = :code")
    Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

    Page<InviteCode> findByEnabled(Boolean enabled, Pageable pageable);
}
