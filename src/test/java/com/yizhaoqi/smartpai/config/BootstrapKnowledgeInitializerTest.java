package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.ElasticsearchService;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapKnowledgeInitializerTest {

    @Mock
    private ParseService parseService;

    @Mock
    private VectorizationService vectorizationService;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BootstrapKnowledgeInitializer initializer;

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipWhenBootstrapDocumentAlreadyReady() throws Exception {
        Path pdfPath = createPdfLikeFile("paismart.pdf", "paismart ready");
        String fileMd5 = md5For(pdfPath);

        FileUpload existingFile = new FileUpload();
        existingFile.setFileMd5(fileMd5);
        existingFile.setFileName("paismart.pdf");
        existingFile.setOrgTag("default");
        existingFile.setPublic(true);
        existingFile.setStatus(1);
        existingFile.setUserId("1");

        configureInitializer(pdfPath);
        mockAdminUser(1L, "admin");
        when(fileUploadRepository.findByUserIdAndFileNameOrderByCreatedAtDesc("1", "paismart.pdf"))
                .thenReturn(Collections.emptyList());
        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, "1"))
                .thenReturn(Optional.of(existingFile));
        when(fileUploadRepository.countByFileMd5AndUserId(fileMd5, "1")).thenReturn(1L);
        when(documentVectorRepository.countByFileMd5(fileMd5)).thenReturn(2L);
        when(documentVectorRepository.countByFileMd5AndPageNumberIsNotNull(fileMd5)).thenReturn(2L);
        when(elasticsearchService.countByFileMd5(fileMd5)).thenReturn(2L);

        initializer.run();

        verify(parseService, never()).parseAndSave(anyString(), any(), anyString(), anyString(), anyBoolean());
        verify(vectorizationService, never()).vectorize(anyString(), anyString(), anyString(), anyBoolean(), anyString());
        verify(fileUploadRepository, never()).save(any(FileUpload.class));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldImportWhenBootstrapDocumentMissing() throws Exception {
        Path pdfPath = createPdfLikeFile("paismart.pdf", "paismart import");
        String fileMd5 = md5For(pdfPath);

        configureInitializer(pdfPath);
        mockAdminUser(1L, "admin");
        when(fileUploadRepository.findByUserIdAndFileNameOrderByCreatedAtDesc("1", "paismart.pdf"))
                .thenReturn(Collections.emptyList());
        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, "1"))
                .thenReturn(Optional.empty());
        when(fileUploadRepository.countByFileMd5AndUserId(fileMd5, "1")).thenReturn(0L);
        when(documentVectorRepository.countByFileMd5(fileMd5)).thenReturn(0L);
        when(documentVectorRepository.countByFileMd5AndPageNumberIsNotNull(fileMd5)).thenReturn(0L);
        when(elasticsearchService.countByFileMd5(fileMd5)).thenReturn(0L);
        doNothing().when(vectorizationService).vectorize(fileMd5, "1", "default", true, "system-bootstrap");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        initializer.run();

        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(parseService).parseAndSave(anyString(), any(), anyString(), anyString(), anyBoolean());
        verify(vectorizationService).vectorize(fileMd5, "1", "default", true, "system-bootstrap");

        ArgumentCaptor<FileUpload> captor = ArgumentCaptor.forClass(FileUpload.class);
        verify(fileUploadRepository).save(captor.capture());

        FileUpload savedFile = captor.getValue();
        assertEquals(fileMd5, savedFile.getFileMd5());
        assertEquals("paismart.pdf", savedFile.getFileName());
        assertEquals("1", savedFile.getUserId());
        assertEquals("default", savedFile.getOrgTag());
        assertTrue(savedFile.isPublic());
    }

    @Test
    void shouldDeleteDuplicateBootstrapRecordsBeforeSkippingImport() throws Exception {
        Path pdfPath = createPdfLikeFile("paismart.pdf", "paismart duplicate");
        String fileMd5 = md5For(pdfPath);

        FileUpload newest = new FileUpload();
        newest.setId(1L);
        newest.setFileMd5(fileMd5);
        newest.setFileName("paismart.pdf");
        newest.setOrgTag("default");
        newest.setPublic(true);
        newest.setStatus(1);
        newest.setUserId("1");

        FileUpload duplicate = new FileUpload();
        duplicate.setId(2L);
        duplicate.setFileMd5(fileMd5);
        duplicate.setFileName("paismart.pdf");
        duplicate.setOrgTag("default");
        duplicate.setPublic(true);
        duplicate.setStatus(1);
        duplicate.setUserId("1");

        configureInitializer(pdfPath);
        mockAdminUser(1L, "admin");
        when(fileUploadRepository.findByUserIdAndFileNameOrderByCreatedAtDesc("1", "paismart.pdf"))
                .thenReturn(List.of(newest, duplicate));
        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, "1"))
                .thenReturn(Optional.of(newest));
        when(fileUploadRepository.countByFileMd5AndUserId(fileMd5, "1")).thenReturn(1L);
        when(documentVectorRepository.countByFileMd5(fileMd5)).thenReturn(2L);
        when(documentVectorRepository.countByFileMd5AndPageNumberIsNotNull(fileMd5)).thenReturn(2L);
        when(elasticsearchService.countByFileMd5(fileMd5)).thenReturn(2L);

        initializer.run();

        verify(fileUploadRepository).deleteAll(List.of(duplicate));
        verify(parseService, never()).parseAndSave(anyString(), any(), anyString(), anyString(), anyBoolean());
        verify(vectorizationService, never()).vectorize(anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }

    private void configureInitializer(Path pdfPath) {
        ReflectionTestUtils.setField(initializer, "bootstrapDocumentPath", pdfPath.toString());
        ReflectionTestUtils.setField(initializer, "bootstrapOrgTag", "default");
        ReflectionTestUtils.setField(initializer, "bootstrapPublic", true);
        ReflectionTestUtils.setField(initializer, "bootstrapUserId", "system-bootstrap");
        ReflectionTestUtils.setField(initializer, "minioBucketName", "uploads");
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
    }

    private Path createPdfLikeFile(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }

    private String md5For(Path path) {
        return (String) ReflectionTestUtils.invokeMethod(initializer, "calculateMd5", path);
    }

    private void mockAdminUser(Long id, String username) {
        User adminUser = new User();
        adminUser.setId(id);
        adminUser.setUsername(username);
        adminUser.setRole(User.Role.ADMIN);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(adminUser));
    }
}
