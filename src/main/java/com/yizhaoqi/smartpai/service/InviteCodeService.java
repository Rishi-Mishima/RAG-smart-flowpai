package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.InviteCode;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.InviteCodeRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InviteCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteCodeService(InviteCodeRepository inviteCodeRepository, UserRepository userRepository) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InviteCode createInviteCode(String creatorUsername, String requestedCode, Integer maxUses, LocalDateTime expiresAt) {
        return createInviteCodes(creatorUsername, requestedCode, maxUses, expiresAt, 1).get(0);
    }

    @Transactional
    public List<InviteCode> createInviteCodes(String creatorUsername, String requestedCode, Integer maxUses, LocalDateTime expiresAt, Integer count) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));

        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create invite codes", HttpStatus.FORBIDDEN);
        }

        int normalizedCount = count == null || count <= 0 ? 1 : count;
        if (normalizedCount > 100) {
            throw new CustomException("Invite code batch size cannot exceed 100", HttpStatus.BAD_REQUEST);
        }

        if (normalizedCount > 1 && requestedCode != null && !requestedCode.isBlank()) {
            throw new CustomException("Custom code is only supported when batch size is 1", HttpStatus.BAD_REQUEST);
        }

        int normalizedMaxUses = maxUses == null || maxUses <= 0 ? 1 : maxUses;
        LocalDateTime normalizedExpiresAt = expiresAt;
        if (normalizedExpiresAt != null && normalizedExpiresAt.isBefore(LocalDateTime.now())) {
            throw new CustomException("Invite code expiry must be in the future", HttpStatus.BAD_REQUEST);
        }

        List<InviteCode> inviteCodes = new ArrayList<>(normalizedCount);
        Set<String> generatedCodes = new HashSet<>();

        for (int i = 0; i < normalizedCount; i++) {
            InviteCode inviteCode = new InviteCode();
            String code = resolveInviteCode(requestedCode, normalizedCount, generatedCodes);

            inviteCode.setCode(code);
            inviteCode.setMaxUses(normalizedMaxUses);
            inviteCode.setUsedCount(0);
            inviteCode.setExpiresAt(normalizedExpiresAt);
            inviteCode.setEnabled(true);
            inviteCode.setCreatedBy(creator);
            inviteCodes.add(inviteCode);
            generatedCodes.add(code);
        }

        return inviteCodeRepository.saveAll(inviteCodes);
    }

    @Transactional
    public void consume(String code, String username) {
        if (code == null || code.isBlank()) {
            throw new CustomException("INVITE_CODE_REQUIRED", HttpStatus.FORBIDDEN);
        }

        InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(normalizeCode(code))
                .orElseThrow(() -> new CustomException("INVITE_CODE_INVALID", HttpStatus.FORBIDDEN));

        if (!Boolean.TRUE.equals(inviteCode.getEnabled())) {
            throw new CustomException("INVITE_CODE_INVALID", HttpStatus.FORBIDDEN);
        }

        if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CustomException("INVITE_CODE_EXPIRED", HttpStatus.FORBIDDEN);
        }

        if (inviteCode.getUsedCount() >= inviteCode.getMaxUses()) {
            throw new CustomException("INVITE_CODE_EXHAUSTED", HttpStatus.FORBIDDEN);
        }

        inviteCode.setUsedCount(inviteCode.getUsedCount() + 1);
        inviteCodeRepository.save(inviteCode);
    }

    @Transactional
    public void disable(Long id, String adminUsername) {
        validateAdmin(adminUsername, "disable");

        InviteCode inviteCode = inviteCodeRepository.findById(id)
                .orElseThrow(() -> new CustomException("Invite code not found", HttpStatus.NOT_FOUND));
        inviteCode.setEnabled(false);
        inviteCodeRepository.save(inviteCode);
    }

    @Transactional
    public void delete(Long id, String adminUsername) {
        validateAdmin(adminUsername, "delete");

        InviteCode inviteCode = inviteCodeRepository.findById(id)
                .orElseThrow(() -> new CustomException("Invite code not found", HttpStatus.NOT_FOUND));
        inviteCodeRepository.delete(inviteCode);
    }

    @Transactional
    public InviteCode update(Long id, String adminUsername, String code, Integer maxUses, LocalDateTime expiresAt) {
        validateAdmin(adminUsername, "edit");

        InviteCode inviteCode = inviteCodeRepository.findById(id)
                .orElseThrow(() -> new CustomException("Invite code not found", HttpStatus.NOT_FOUND));

        if (inviteCode.getUsedCount() > 0) {
            throw new CustomException("Used invite codes cannot be edited", HttpStatus.BAD_REQUEST);
        }

        String normalizedCode = normalizeCode(code);
        InviteCode existingInviteCode = inviteCodeRepository.findByCode(normalizedCode).orElse(null);
        if (existingInviteCode != null && !existingInviteCode.getId().equals(id)) {
            throw new CustomException("Invite code already exists", HttpStatus.BAD_REQUEST);
        }

        int normalizedMaxUses = maxUses == null || maxUses <= 0 ? 1 : maxUses;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new CustomException("Invite code expiry must be in the future", HttpStatus.BAD_REQUEST);
        }

        inviteCode.setCode(normalizedCode);
        inviteCode.setMaxUses(normalizedMaxUses);
        inviteCode.setExpiresAt(expiresAt);

        return inviteCodeRepository.save(inviteCode);
    }

    public Map<String, Object> list(Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InviteCode> result = enabled == null ? inviteCodeRepository.findAll(pageable) : inviteCodeRepository.findByEnabled(enabled, pageable);

        return Map.of(
                "records", result.getContent(),
                "total", result.getTotalElements(),
                "pages", result.getTotalPages(),
                "current", page,
                "size", size
        );
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String resolveInviteCode(String requestedCode, int batchSize, Set<String> generatedCodes) {
        if (batchSize == 1 && requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            if (inviteCodeRepository.findByCode(normalized).isPresent()) {
                throw new CustomException("Invite code already exists", HttpStatus.BAD_REQUEST);
            }
            return normalized;
        }

        String generated;
        do {
            generated = generateCode(16);
        } while (generatedCodes.contains(generated) || inviteCodeRepository.findByCode(generated).isPresent());

        return generated;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException("Invite code cannot be empty", HttpStatus.BAD_REQUEST);
        }
        return code.trim().toUpperCase();
    }

    private void validateAdmin(String adminUsername, String action) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can " + action + " invite codes", HttpStatus.FORBIDDEN);
        }
    }
}
