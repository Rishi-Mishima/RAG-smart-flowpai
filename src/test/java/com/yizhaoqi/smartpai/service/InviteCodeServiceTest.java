package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.InviteCode;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.InviteCodeRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class InviteCodeServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InviteCodeService inviteCodeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConsumeSuccess() {
        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode("ABC123");
        inviteCode.setEnabled(true);
        inviteCode.setMaxUses(2);
        inviteCode.setUsedCount(1);

        when(inviteCodeRepository.findByCodeForUpdate("ABC123")).thenReturn(Optional.of(inviteCode));

        inviteCodeService.consume("abc123", "tester");

        assertEquals(2, inviteCode.getUsedCount());
        verify(inviteCodeRepository, times(1)).save(inviteCode);
    }

    @Test
    void testConsumeExpired() {
        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode("ABC123");
        inviteCode.setEnabled(true);
        inviteCode.setMaxUses(2);
        inviteCode.setUsedCount(0);
        inviteCode.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(inviteCodeRepository.findByCodeForUpdate("ABC123")).thenReturn(Optional.of(inviteCode));

        CustomException exception = assertThrows(CustomException.class,
                () -> inviteCodeService.consume("ABC123", "tester"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("INVITE_CODE_EXPIRED", exception.getMessage());
    }

    @Test
    void testCreateInviteCodeByAdmin() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        InviteCode inviteCode = inviteCodeService.createInviteCode("admin", null, 1, null);

        assertNull(inviteCode.getExpiresAt());
        verify(inviteCodeRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testCreateInviteCodeByNonAdmin() {
        User user = new User();
        user.setUsername("user");
        user.setRole(User.Role.USER);

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> inviteCodeService.createInviteCode("user", null, 1, null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
