package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.utils.LogUtils;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档控制器类，处理文档相关操作请求
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ChatHandler chatHandler;

    /**
     * 删除文档及其相关数据
     * 
     * @param fileMd5 文件MD5
     * @param userId 当前用户ID
     * @param role 用户角色
     * @return 删除结果
     */
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DELETE_DOCUMENT");
        try {
            LogUtils.logBusiness("DELETE_DOCUMENT", userId, "接收到删除文档请求: fileMd5=%s, role=%s", fileMd5, role);
            
            // 获取文件信息
            Optional<FileUpload> fileOpt = fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, userId);
            if (fileOpt.isEmpty()) {
                LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_NOT_FOUND");
                monitor.end("删除失败：文档不存在");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文档不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            FileUpload file = fileOpt.get();
            
            // 权限检查：只有文件所有者或管理员可以删除
            if (!file.getUserId().equals(userId) && !"ADMIN".equals(role)) {
                LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_PERMISSION_DENIED");
                LogUtils.logBusiness("DELETE_DOCUMENT", userId, "用户无权删除文档: fileMd5=%s, fileOwner=%s", fileMd5, file.getUserId());
                monitor.end("删除失败：权限不足");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.FORBIDDEN.value());
                response.put("message", "没有权限删除此文档");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // 执行删除操作
            documentService.deleteDocument(fileMd5, userId);
            
            LogUtils.logFileOperation(userId, "DELETE", file.getFileName(), fileMd5, "SUCCESS");
            monitor.end("文档删除成功");
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("DELETE_DOCUMENT", userId, "删除文档失败: fileMd5=%s", e, fileMd5);
            monitor.end("删除失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "删除文档失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{fileMd5}/reindex")
    public ResponseEntity<?> reindexDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("REINDEX_DOCUMENT");
        try {
            LogUtils.logBusiness("REINDEX_DOCUMENT", userId, "接收到重建文档索引请求: fileMd5=%s, role=%s", fileMd5, role);

            Optional<FileUpload> fileOpt = fileUploadRepository.findFirstByFileMd5OrderByCreatedAtDesc(fileMd5);
            if (fileOpt.isEmpty()) {
                monitor.end("重建失败：文档不存在");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文档不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            FileUpload file = fileOpt.get();
            if (!file.getUserId().equals(userId) && !"ADMIN".equals(role)) {
                monitor.end("重建失败：权限不足");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.FORBIDDEN.value());
                response.put("message", "没有权限重建此文档索引");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            var result = documentService.reindexDocument(fileMd5, userId);
            monitor.end("文档索引重建成功");

            Map<String, Object> data = new HashMap<>();
            data.put("fileMd5", fileMd5);
            data.put("fileName", file.getFileName());
            data.put("actualEmbeddingTokens", result.actualEmbeddingTokens());
            data.put("actualChunkCount", result.actualChunkCount());
            data.put("modelVersion", result.modelVersion());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文档索引重建成功");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("REINDEX_DOCUMENT", userId, "重建文档索引失败: fileMd5=%s", e, fileMd5);
            monitor.end("重建失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "重建文档索引失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 
     * @param userId 当前用户ID
     * @param orgTags 用户所属组织标签
     * @return 可访问的文件列表
     */
    @GetMapping("/accessible")
    public ResponseEntity<?> getAccessibleFiles(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_ACCESSIBLE_FILES");
        try {
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "接收到获取可访问文件请求: orgTags=%s", orgTags);
            
            List<FileUpload> files = documentService.getAccessibleFiles(userId, orgTags);
            List<Map<String, Object>> fileData = convertFilesToResponse(files);
            
            LogUtils.logUserOperation(userId, "GET_ACCESSIBLE_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "成功获取可访问文件: fileCount=%d", files.size());
            monitor.end("获取可访问文件成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取可访问文件列表成功");
            response.put("data", fileData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ACCESSIBLE_FILES", userId, "获取可访问文件失败", e);
            monitor.end("获取可访问文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取可访问文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户上传的所有文件列表
     * 
     * @param userId 当前用户ID
     * @return 用户上传的文件列表
     */
    @GetMapping("/uploads")
    public ResponseEntity<?> getUserUploadedFiles(
            @RequestAttribute("userId") String userId) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_UPLOADED_FILES");
        try {
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId, "接收到获取用户上传文件请求");
            
            List<FileUpload> files = documentService.getUserUploadedFiles(userId);

            // 添加详细日志：追踪每个文件的MD5
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId, "开始处理文件列表，总数: %d", files.size());
            for (int i = 0; i < files.size(); i++) {
                FileUpload file = files.get(i);
                LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId,
                    "文件[%d]: fileName=%s, fileMd5=%s, totalSize=%d",
                    i, file.getFileName(), file.getFileMd5(), file.getTotalSize());
            }

            List<Map<String, Object>> fileData = convertFilesToResponse(files);
            
            LogUtils.logUserOperation(userId, "GET_USER_UPLOADED_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId, "成功获取用户上传文件: fileCount=%d", files.size());
            monitor.end("获取用户上传文件成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取用户上传文件列表成功");
            response.put("data", fileData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_UPLOADED_FILES", userId, "获取用户上传文件失败", e);
            monitor.end("获取用户上传文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取用户上传文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private List<Map<String, Object>> convertFilesToResponse(List<FileUpload> files) {
        return files.stream().map(file -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("fileMd5", file.getFileMd5());
            dto.put("fileName", file.getFileName());
            dto.put("totalSize", file.getTotalSize());
            dto.put("status", file.getStatus());
            dto.put("userId", file.getUserId());
            dto.put("orgTag", file.getOrgTag());
            dto.put("public", file.isPublic());
            dto.put("isPublic", file.isPublic());
            dto.put("createdAt", file.getCreatedAt());
            dto.put("mergedAt", file.getMergedAt());
            dto.put("estimatedEmbeddingTokens", file.getEstimatedEmbeddingTokens());
            dto.put("estimatedChunkCount", file.getEstimatedChunkCount());
            dto.put("actualEmbeddingTokens", file.getActualEmbeddingTokens());
            dto.put("actualChunkCount", file.getActualChunkCount());
            dto.put("orgTagName", getOrgTagName(file.getOrgTag()));
            return dto;
        }).collect(Collectors.toList());
    }
    
    /**
     * 根据文件名下载文件
     * 
     * @param fileName 文件名
     * @param token JWT token
     * @return 文件资源或错误响应
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadFileByName(
            @RequestParam String fileName,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DOWNLOAD_FILE_BY_NAME");
        try {
            // 验证token并获取用户信息
            RequestAuthContext authContext = resolveRequestAuthContext(authorization, token);
            String userId = authContext.userId();
            String orgTags = authContext.orgTags();
            
            LogUtils.logBusiness("DOWNLOAD_FILE_BY_NAME", userId != null ? userId : "anonymous", "接收到文件下载请求: fileName=%s", fileName);
            
            // 如果没有提供token或token无效，只允许下载公开文件
            if (userId == null) {
                // 查找公开文件
                Optional<FileUpload> publicFile = fileUploadRepository.findFirstByFileNameAndIsPublicTrueOrderByCreatedAtDesc(fileName);
                if (publicFile.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                FileUpload file = publicFile.get();
                String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
                
                if (downloadUrl == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法生成下载链接");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件下载链接生成成功");
                response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "downloadUrl", downloadUrl,
                    "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }
            
            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);
            
            // 根据文件名查找匹配的文件
            Optional<FileUpload> targetFile = accessibleFiles.stream()
                    .filter(file -> file.getFileName().equals(fileName))
                    .findFirst();
                    
            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("下载失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            FileUpload file = targetFile.get();
            
            // 生成下载链接或返回预签名URL
            String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
            
            if (downloadUrl == null) {
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_GENERATE_URL");
                monitor.end("下载失败：无法生成下载链接");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法生成下载链接");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "DOWNLOAD", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件下载链接生成成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件下载链接生成成功");
            response.put("data", Map.of(
                "fileName", file.getFileName(),
                "downloadUrl", downloadUrl,
                "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("DOWNLOAD_FILE_BY_NAME", userId, "文件下载失败: fileName=%s", e, fileName);
            monitor.end("下载失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件下载失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 预览文件内容
     *
     * @param fileName 文件名
     * @param fileMd5 文件MD5（可选，用于精确定位同名文件）
     * @param token JWT token (URL参数，用于向后兼容)
     * @return 文件预览内容或错误响应
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewFileByName(
            @RequestParam String fileName,
            @RequestParam(required = false) String fileMd5,
            @RequestParam(required = false) Integer pageNumber,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PREVIEW_FILE_BY_NAME");
        try {
            // 验证token并获取用户信息
            RequestAuthContext authContext = resolveRequestAuthContext(authorization, token);
            String userId = authContext.userId();
            String orgTags = authContext.orgTags();
            
            LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId != null ? userId : "anonymous",
                    "接收到文件预览请求: fileName=%s, fileMd5=%s, pageNumber=%s", fileName, fileMd5, pageNumber);

            FileUpload file = null;

            // 如果没有提供token或token无效，只允许预览公开文件
            if (userId == null) {
                // 优先使用MD5查找（如果提供）
                if (fileMd5 != null && !fileMd5.trim().isEmpty()) {
                    Optional<FileUpload> fileByMd5 = fileUploadRepository.findFirstByFileMd5AndIsPublicTrueOrderByCreatedAtDesc(fileMd5);
                    if (fileByMd5.isPresent()) {
                        file = fileByMd5.get();
                        LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "使用MD5找到公开文件: fileMd5=%s", fileMd5);
                    }
                }

                // 如果MD5未找到或未提供，降级到文件名查找
                if (file == null) {
                    Optional<FileUpload> publicFile = fileUploadRepository.findFirstByFileNameAndIsPublicTrueOrderByCreatedAtDesc(fileName);
                    if (publicFile.isPresent()) {
                        file = publicFile.get();
                        LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "使用文件名找到公开文件: fileName=%s", fileName);
                    }
                }

                if (file == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }

                Map<String, Object> previewData = buildPreviewResponse(file, pageNumber, false);
                if (previewData == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法获取文件预览内容");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件预览内容获取成功");
                response.put("data", previewData);
                LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous",
                        "预览响应已生成: fileMd5=%s, mode=%s, pageNumber=%s",
                        file.getFileMd5(),
                        Boolean.TRUE.equals(previewData.get("singlePageMode")) ? "single-page" : "full-document",
                        pageNumber);
                return ResponseEntity.ok(response);
            }

            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);

            // 优先使用MD5查找（如果提供）
            Optional<FileUpload> targetFile = Optional.empty();
            if (fileMd5 != null && !fileMd5.trim().isEmpty()) {
                final String md5 = fileMd5;
                targetFile = accessibleFiles.stream()
                        .filter(f -> f.getFileMd5().equals(md5))
                        .findFirst();
                if (targetFile.isPresent()) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId, "使用MD5找到文件: fileMd5=%s", fileMd5);
                }
            }

            // 如果MD5未找到或未提供，降级到文件名查找
            if (targetFile.isEmpty()) {
                targetFile = accessibleFiles.stream()
                        .filter(f -> f.getFileName().equals(fileName))
                        .findFirst();
                if (targetFile.isPresent()) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId, "使用文件名找到文件: fileName=%s", fileName);
                }
            }

            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("预览失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            file = targetFile.get();
            
            // 获取文件预览内容
            Map<String, Object> previewData = buildPreviewResponse(file, pageNumber, true);
            if (previewData == null) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_GET_CONTENT");
                monitor.end("预览失败：无法获取文件内容");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法获取文件预览内容");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "PREVIEW", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件预览内容获取成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件预览内容获取成功");
            response.put("data", previewData);
            LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId,
                    "预览响应已生成: fileMd5=%s, mode=%s, pageNumber=%s",
                    file.getFileMd5(),
                    Boolean.TRUE.equals(previewData.get("singlePageMode")) ? "single-page" : "full-document",
                    pageNumber);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("PREVIEW_FILE_BY_NAME", userId, "文件预览失败: fileName=%s", e, fileName);
            monitor.end("预览失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件预览失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/page-preview")
    public ResponseEntity<?> previewPdfPage(
            @RequestParam String fileMd5,
            @RequestParam Integer pageNumber,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PREVIEW_PDF_PAGE");
        try {
            LogUtils.logBusiness("PREVIEW_PDF_PAGE", userId,
                    "接收到 PDF 单页预览请求: fileMd5=%s, pageNumber=%s", fileMd5, pageNumber);

            FileUpload file = documentService.getAccessibleFiles(userId, orgTags).stream()
                    .filter(item -> item.getFileMd5().equals(fileMd5))
                    .findFirst()
                    .orElse(null);

            if (file == null) {
                monitor.end("预览失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            if (!"pdf".equalsIgnoreCase(getFileExtension(file.getFileName()))) {
                monitor.end("预览失败：仅支持 PDF 单页预览");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.BAD_REQUEST.value());
                response.put("message", "仅支持 PDF 单页预览");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            DocumentService.PdfSinglePagePreview preview = documentService.getPdfSinglePagePreview(fileMd5, pageNumber);
            byte[] pdfBytes = preview.content();
            String encodedFileName = URLEncoder.encode(file.getFileName().replace(".pdf", "") + "-page-" + pageNumber + ".pdf",
                    StandardCharsets.UTF_8);
            String cacheStatus = preview.cacheHit() ? "HIT" : "MISS";

            LogUtils.logBusiness("PREVIEW_PDF_PAGE", userId,
                    "PDF 单页预览响应: fileMd5=%s, pageNumber=%s, cache=%s, contentLength=%s",
                    fileMd5, pageNumber, cacheStatus, pdfBytes.length);

            monitor.end("PDF 单页预览生成成功");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800")
                    .header(HttpHeaders.ETAG, "\"" + fileMd5 + ":" + pageNumber + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                    .header("X-Preview-Mode", "single-page")
                    .header("X-Preview-Cache", cacheStatus)
                    .header("X-Preview-Page", String.valueOf(pageNumber))
                    .body(pdfBytes);
        } catch (IllegalArgumentException e) {
            monitor.end("预览失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.BAD_REQUEST.value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("PREVIEW_PDF_PAGE", userId,
                    "PDF 单页预览失败: fileMd5=%s, pageNumber=%s", e, fileMd5, pageNumber);
            monitor.end("预览失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "PDF 单页预览失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/download-by-md5")
    public ResponseEntity<?> downloadFileByMd5(
            @RequestParam String fileMd5,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String token) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DOWNLOAD_FILE_BY_MD5");
        try {
            RequestAuthContext authContext = resolveRequestAuthContext(authorization, token);
            String userId = authContext.userId();
            String orgTags = authContext.orgTags();

            LogUtils.logBusiness("DOWNLOAD_FILE_BY_MD5", userId != null ? userId : "anonymous", "接收到文件下载请求: fileMd5=%s", fileMd5);

            FileUpload file;
            if (userId == null) {
                file = fileUploadRepository.findFirstByFileMd5AndIsPublicTrueOrderByCreatedAtDesc(fileMd5)
                        .orElse(null);
            } else {
                file = documentService.getAccessibleFiles(userId, orgTags).stream()
                        .filter(item -> item.getFileMd5().equals(fileMd5))
                        .findFirst()
                        .orElse(null);
            }

            if (file == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
            if (downloadUrl == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法生成下载链接");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件下载链接生成成功");
            response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "downloadUrl", downloadUrl,
                    "fileSize", file.getTotalSize(),
                    "fileMd5", file.getFileMd5()
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}

            LogUtils.logBusinessError("DOWNLOAD_FILE_BY_MD5", userId, "文件下载失败: fileMd5=%s", e, fileMd5);
            monitor.end("下载失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件下载失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/reference-detail")
    public ResponseEntity<?> getReferenceDetail(
            @RequestParam String sessionId,
            @RequestParam Integer referenceNumber,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_REFERENCE_DETAIL");
        try {
            LogUtils.logBusiness("GET_REFERENCE_DETAIL", "system",
                    "接收到获取引用详情请求: sessionId=%s, referenceNumber=%s", sessionId, referenceNumber);

            ChatHandler.ReferenceInfo detail = chatHandler.getReferenceDetail(sessionId, referenceNumber);
            if (detail == null) {
                monitor.end("未找到引用映射");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "未找到对应的文件引用");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            RequestAuthContext authContext = resolveRequestAuthContext(authorization, null);
            if (authContext.userId() != null) {
                boolean hasAccess = documentService.getAccessibleFiles(authContext.userId(), authContext.orgTags()).stream()
                        .anyMatch(file -> file.getFileMd5().equals(detail.fileMd5()));
                if (!hasAccess) {
                    monitor.end("获取引用详情失败：无权限访问引用文件");
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.FORBIDDEN.value());
                    response.put("message", "无权限访问该引用文件");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("fileMd5", detail.fileMd5());
            data.put("fileName", detail.fileName());
            data.put("referenceNumber", referenceNumber);
            data.put("pageNumber", detail.pageNumber());
            data.put("anchorText", detail.anchorText());
            data.put("retrievalMode", detail.retrievalMode());
            data.put("retrievalLabel", detail.retrievalLabel());
            data.put("retrievalQuery", detail.retrievalQuery());
            data.put("matchedChunkText", detail.matchedChunkText());
            data.put("evidenceSnippet", detail.evidenceSnippet());
            data.put("score", detail.score());
            data.put("chunkId", detail.chunkId());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取引用详情成功");
            response.put("data", data);
            monitor.end("获取引用详情成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_REFERENCE_DETAIL", "system",
                    "获取引用详情失败: sessionId=%s, referenceNumber=%s", e, sessionId, referenceNumber);
            monitor.end("获取引用详情失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取引用详情失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private Map<String, Object> buildPreviewResponse(FileUpload file, Integer pageNumber, boolean preferSinglePagePreview) {
        String fileName = file.getFileName();
        String extension = getFileExtension(fileName);
        String previewType = getPreviewType(extension);

        Map<String, Object> payload = new HashMap<>();
        payload.put("fileName", fileName);
        payload.put("fileMd5", file.getFileMd5());
        payload.put("fileSize", file.getTotalSize());
        payload.put("previewType", previewType);

        if ("text".equals(previewType)) {
            String previewContent = documentService.getFilePreviewContent(file.getFileMd5(), fileName);
            if (previewContent == null) {
                return null;
            }
            payload.put("content", previewContent);
            return payload;
        }

        String previewUrl = documentService.generateDownloadUrl(file.getFileMd5());
        if (previewUrl == null) {
            return null;
        }

        if (preferSinglePagePreview && "pdf".equals(previewType) && pageNumber != null && pageNumber > 0) {
            payload.put("previewUrl", buildSinglePagePreviewUrl(file.getFileMd5(), pageNumber));
            payload.put("sourceUrl", previewUrl);
            payload.put("singlePageMode", true);
            payload.put("sourcePageNumber", pageNumber);
            return payload;
        }

        payload.put("previewUrl", previewUrl);
        return payload;
    }

    private String buildSinglePagePreviewUrl(String fileMd5, Integer pageNumber) {
        return "/api/v1/documents/page-preview?fileMd5="
                + URLEncoder.encode(fileMd5, StandardCharsets.UTF_8)
                + "&pageNumber="
                + pageNumber;
    }

    private String getPreviewType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "download";
        }

        String lowerCaseExtension = extension.toLowerCase();
        if ("pdf".equals(lowerCaseExtension)) {
            return "pdf";
        }

        if (List.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg").contains(lowerCaseExtension)) {
            return "image";
        }

        if (List.of("txt", "md", "json", "xml", "csv", "html", "htm", "css", "js", "java", "py", "sql", "yaml", "yml").contains(lowerCaseExtension)) {
            return "text";
        }

        return "download";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1);
    }

    private RequestAuthContext resolveRequestAuthContext(String authorization, String fallbackToken) {
        String jwtToken = extractBearerToken(authorization);
        if ((jwtToken == null || jwtToken.isBlank()) && fallbackToken != null && !fallbackToken.isBlank()) {
            jwtToken = fallbackToken.trim();
        }

        if (jwtToken == null || jwtToken.isBlank()) {
            return new RequestAuthContext(null, null);
        }

        return new RequestAuthContext(
                jwtUtils.extractUserIdFromToken(jwtToken),
                jwtUtils.extractOrgTagsFromToken(jwtToken)
        );
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }

        String trimmed = authorization.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring(7);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RequestAuthContext(String userId, String orgTags) {}
    
    /**
     * 根据tagId获取tagName
     *
     * @param tagId 组织标签ID
     * @return 组织标签名称，如果找不到则返回原tagId
     */
    private String getOrgTagName(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }
        
        try {
            Optional<OrganizationTag> tagOpt = organizationTagRepository.findByTagId(tagId);
            if (tagOpt.isPresent()) {
                return tagOpt.get().getName();
            } else {
                LogUtils.logBusiness("GET_ORG_TAG_NAME", "system", "找不到组织标签: tagId=%s", tagId);
                return tagId; // 如果找不到标签名称，返回原tagId
            }
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ORG_TAG_NAME", "system", "查询组织标签名称失败: tagId=%s", e, tagId);
            return tagId; // 发生错误时返回原tagId
        }
    }
} 
