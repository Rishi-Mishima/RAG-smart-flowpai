package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.FileTypeValidationService;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.UploadService;
import com.yizhaoqi.smartpai.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class UploadControllerTest {

    @Mock
    private UploadService uploadService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private KafkaConfig kafkaConfig;

    @Mock
    private UserService userService;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private FileTypeValidationService fileTypeValidationService;

    @Mock
    private ParseService parseService;

    private UploadController uploadController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uploadController = new UploadController(uploadService, kafkaTemplate);
        ReflectionTestUtils.setField(uploadController, "kafkaConfig", kafkaConfig);
        ReflectionTestUtils.setField(uploadController, "userService", userService);
        ReflectionTestUtils.setField(uploadController, "fileUploadRepository", fileUploadRepository);
        ReflectionTestUtils.setField(uploadController, "fileTypeValidationService", fileTypeValidationService);
        ReflectionTestUtils.setField(uploadController, "parseService", parseService);
        when(fileTypeValidationService.getSupportedFileTypes()).thenReturn(Set.of("pdf"));
    }

    @Test
    void testUploadChunkRejectsOversizedFileForNonAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "demo".getBytes());
        OrganizationTag orgTag = new OrganizationTag();
        orgTag.setTagId("TEAM_A");
        orgTag.setUploadMaxSizeBytes(1024L * 1024L);

        when(fileTypeValidationService.validateFileType("test.pdf"))
                .thenReturn(new FileTypeValidationService.FileTypeValidationResult(true, "ok", "PDF文档", "pdf"));
        when(userService.isAdminUser("1")).thenReturn(false);
        when(userService.getOrganizationTag("TEAM_A")).thenReturn(orgTag);

        var response = uploadController.uploadChunk(
                "md5",
                0,
                2L * 1024 * 1024,
                "test.pdf",
                1,
                "TEAM_A",
                false,
                file,
                "1"
        );

        assertEquals(413, response.getStatusCode().value());
        assertEquals(413, response.getBody().get("code"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("不超过"));
        verify(uploadService, never()).uploadChunk(anyString(), anyInt(), anyLong(), anyString(), any(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void testUploadChunkAllowsAdminToBypassOrgLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "demo".getBytes());

        when(fileTypeValidationService.validateFileType("test.pdf"))
                .thenReturn(new FileTypeValidationService.FileTypeValidationResult(true, "ok", "PDF文档", "pdf"));
        when(userService.isAdminUser("1")).thenReturn(true);
        when(uploadService.getUploadedChunks("md5", "1")).thenReturn(List.of(0));
        when(uploadService.getTotalChunks("md5", "1")).thenReturn(1);

        var response = uploadController.uploadChunk(
                "md5",
                0,
                20L * 1024 * 1024,
                "test.pdf",
                1,
                "TEAM_A",
                false,
                file,
                "1"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("uploaded", List.of(0), "progress", 100.0d), response.getBody().get("data"));
        verify(uploadService).uploadChunk("md5", 0, 20L * 1024 * 1024, "test.pdf", file, "TEAM_A", false, "1");
        verify(userService, never()).getOrganizationTag(anyString());
    }

    @Test
    void testUploadChunkRejectsWhenLaterChunkExceedsOrgLimitEvenIfTotalSizeIsUnderreported() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "demo".getBytes());
        OrganizationTag orgTag = new OrganizationTag();
        orgTag.setTagId("TEAM_A");
        orgTag.setUploadMaxSizeBytes(5L * 1024 * 1024L);

        when(userService.isAdminUser("1")).thenReturn(false);
        when(userService.getOrganizationTag("TEAM_A")).thenReturn(orgTag);

        var response = uploadController.uploadChunk(
                "md5",
                1,
                1024L,
                "test.pdf",
                2,
                "TEAM_A",
                false,
                file,
                "1"
        );

        assertEquals(413, response.getStatusCode().value());
        verify(uploadService, never()).uploadChunk(anyString(), anyInt(), anyLong(), anyString(), any(), anyString(), anyBoolean(), anyString());
    }
}
