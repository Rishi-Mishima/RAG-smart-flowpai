package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AppAuthProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.model.RegistrationMode;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    @Mock
    private AppAuthProperties appAuthProperties;

    @Mock
    private AppAuthProperties.Registration registration;

    @Mock
    private InviteCodeService inviteCodeService;

    @Mock
    private UsageQuotaService usageQuotaService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(userService, "globalUploadMaxFileSize", "50MB");
        when(appAuthProperties.getRegistration()).thenReturn(registration);
        when(registration.getMode()).thenReturn(RegistrationMode.OPEN);
        when(registration.isInviteRequired()).thenReturn(false);
    }

    @Test
    void testRegisterUserSuccessWhenOpenRegistration() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(true);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);

        userService.registerUser("testuser", "password123", null);

        verify(userRepository, atLeastOnce()).save(any(User.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(2)).save(userCaptor.capture());
        User savedUser = userCaptor.getAllValues().get(userCaptor.getAllValues().size() - 1);
        Set<String> savedOrgTags = new HashSet<>(Arrays.asList(savedUser.getOrgTags().split(",")));
        assertEquals(Set.of("DEFAULT", "PRIVATE_testuser"), savedOrgTags);
        assertEquals("PRIVATE_testuser", savedUser.getPrimaryOrg());
        verify(orgTagCacheService).cacheUserOrgTags("testuser", List.of("DEFAULT", "PRIVATE_testuser"));
        verify(orgTagCacheService).cacheUserPrimaryOrg("testuser", "PRIVATE_testuser");
        verify(inviteCodeService, never()).consume(anyString(), anyString());
    }

    @Test
    void testRegisterUserClosed() {
        when(registration.getMode()).thenReturn(RegistrationMode.CLOSED);

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals("REGISTRATION_CLOSED", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void testRegisterUserInviteRequired() {
        when(registration.getMode()).thenReturn(RegistrationMode.INVITE_ONLY);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(true);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);

        userService.registerUser("testuser", "password123", "INVITE-001");

        verify(inviteCodeService, times(1)).consume("INVITE-001", "testuser");
    }

    @Test
    void testRegisterUserUsernameExists() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void testAuthenticateUserSuccess() {
        String rawPassword = "password123";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        User user = new User();
        user.setUsername("testuser");
        user.setPassword(encodedPassword);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        String username = userService.authenticateUser("testuser", rawPassword);
        assertEquals("testuser", username);
    }

    @Test
    void testAuthenticateUserInvalidCredentials() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.authenticateUser("testuser", "wrongpassword"));

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void testEnsureDefaultOrgRequiresAdminWhenMissing() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(registration.getMode()).thenReturn(RegistrationMode.OPEN);
        when(registration.isInviteRequired()).thenReturn(false);

        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of());

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }

    @Test
    void testCreateOrganizationTagStoresUploadLimit() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        OrganizationTag parentTag = new OrganizationTag();
        parentTag.setTagId("ROOT");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(organizationTagRepository.existsByTagId("TEAM_A")).thenReturn(false);
        when(organizationTagRepository.findByTagId("ROOT")).thenReturn(Optional.of(parentTag));
        when(organizationTagRepository.save(any(OrganizationTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationTag saved = userService.createOrganizationTag("TEAM_A", "Team A", "desc", "ROOT", 20L, "admin");

        assertEquals(20L * 1024 * 1024, saved.getUploadMaxSizeBytes());
        verify(orgTagCacheService).invalidateAllEffectiveTagsCache();
    }

    @Test
    void testCreateOrganizationTagAllowsUnlimitedUploadSize() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(organizationTagRepository.existsByTagId("TEAM_A")).thenReturn(false);
        when(organizationTagRepository.save(any(OrganizationTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationTag saved = userService.createOrganizationTag("TEAM_A", "Team A", "desc", null, null, "admin");

        assertNull(saved.getUploadMaxSizeBytes());
    }

    @Test
    void testCreateOrganizationTagRejectsInvalidUploadLimit() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(organizationTagRepository.existsByTagId("TEAM_A")).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userService.createOrganizationTag("TEAM_A", "Team A", "desc", null, 0L, "admin")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("上传大小上限必须大于 0 MB", exception.getMessage());
    }

    @Test
    void testCreateOrganizationTagRejectsLimitOverGlobalMax() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(organizationTagRepository.existsByTagId("TEAM_A")).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userService.createOrganizationTag("TEAM_A", "Team A", "desc", null, 60L, "admin")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("上传大小上限不能超过系统全局限制 50 MB", exception.getMessage());
    }

    @Test
    void testGetUserListKeepsTotalCountAcrossPages() {
        List<User> users = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(index -> {
                    User user = new User();
                    user.setId((long) index);
                    user.setUsername("user-" + index);
                    user.setRole(User.Role.USER);
                    user.setCreatedAt(LocalDateTime.of(2026, 3, 1, 0, 0).minusMinutes(index));
                    return user;
                })
                .toList();

        when(userRepository.findAll(any(Sort.class))).thenReturn(users);
        when(usageQuotaService.getSnapshots(anyList())).thenReturn(Map.of());
        when(usageQuotaService.getSnapshot(anyString())).thenReturn(null);

        Map<String, Object> result = userService.getUserList(null, null, null, 2, 10);

        assertEquals(25L, result.get("totalElements"));
        assertEquals(3, result.get("totalPages"));
        assertEquals(10, result.get("size"));
        assertEquals(2, result.get("number"));
        assertEquals(10, ((List<?>) result.get("content")).size());
        verify(userRepository).findAll(any(Sort.class));
    }
}
