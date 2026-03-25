package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.MinioConfig;
import com.yizhaoqi.smartpai.model.ChunkInfo;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.ChunkInfoRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import io.micrometer.common.util.StringUtils;
import io.minio.*;
import io.minio.http.Method;
import io.minio.GetObjectResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    // 用于缓存已上传分片的信息
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 用于与 MinIO 服务器交互
    @Autowired
    private MinioClient minioClient;

    // 用于操作文件上传记录的 Repository
    @Autowired
    private FileUploadRepository fileUploadRepository;

    // 用于操作分片信息的 Repository
    @Autowired
    private ChunkInfoRepository chunkInfoRepository;

    @Autowired
    private MinioConfig minioConfig;

    /**
     * 上传文件分片
     *
     * @param fileMd5 文件的 MD5 值，用于唯一标识文件
     * @param chunkIndex 分片索引，表示这是文件的第几个分片
     * @param totalSize 文件总大小
     * @param fileName 文件名称
     * @param file 要上传的分片文件
     * @param orgTag 组织标签，指定文件所属的组织
     * @param isPublic 是否公开，标识文件访问权限
     * @param userId 上传用户ID
     * @throws IOException 如果文件读取失败
     */
    public void uploadChunk(String fileMd5, int chunkIndex, long totalSize, String fileName, 
                           MultipartFile file, String orgTag, boolean isPublic, String userId) throws IOException {
        // 获取文件类型信息
        String fileType = getFileType(fileName);
        String contentType = file.getContentType();

        // 开始处理分片上传请求
        logger.info("[uploadChunk] Starting to process chunked upload request => fileMd5: {}, chunkIndex: {}, totalSize: {}, fileName: {}, fileType: {}, contentType: {}, fileSize: {}, orgTag: {}, isPublic: {}, userId: {}",
                   fileMd5, chunkIndex, totalSize, fileName, fileType, contentType, file.getSize(), orgTag, isPublic, userId);
        
        try {
            // 检查 file_upload 表中是否存在该 file_md5
            boolean fileExists = fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, userId).isPresent();
            logger.debug("检查文件记录是否存在 => fileMd5: {}, fileName: {}, fileType: {}, exists: {}", fileMd5, fileName, fileType, fileExists);
            
            if (!fileExists) {
                logger.info("创建新的文件记录 => fileMd5: {}, fileName: {}, fileType: {}, totalSize: {}, userId: {}, orgTag: {}, isPublic: {}", 
                          fileMd5, fileName, fileType, totalSize, userId, orgTag, isPublic);
                // 插入 file_upload 表
                FileUpload fileUpload = new FileUpload();
                fileUpload.setFileMd5(fileMd5);
                fileUpload.setFileName(fileName); // 文件名可以从请求中获取
                fileUpload.setTotalSize(totalSize); // 文件总大小
                fileUpload.setStatus(0); // 0 表示上传中
                fileUpload.setUserId(userId); // 设置上传用户ID
                fileUpload.setOrgTag(orgTag); // 设置组织标签
                fileUpload.setPublic(isPublic); // 设置是否公开
                try {
                    fileUploadRepository.save(fileUpload);
                    logger.info("文件记录创建成功 => fileMd5: {}, fileName: {}, fileType: {}", fileMd5, fileName, fileType);
                } catch (Exception e) {
                    logger.error("创建文件记录失败 => fileMd5: {}, fileName: {}, fileType: {}, 错误: {}", fileMd5, fileName, fileType, e.getMessage(), e);
                    throw new RuntimeException("创建文件记录失败: " + e.getMessage(), e);
                }
            }

            // 检查分片是否已经上传
            boolean chunkUploaded = isChunkUploaded(fileMd5, chunkIndex, userId);
            logger.debug("检查分片是否已上传 => fileMd5: {}, fileName: {}, chunkIndex: {}, isUploaded: {}", 
                      fileMd5, fileName, chunkIndex, chunkUploaded);
                      
            // 检查数据库中是否存在分片信息
            boolean chunkInfoExists = false;
            try {
                List<ChunkInfo> chunkInfos = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5);
                chunkInfoExists = chunkInfos.stream()
                    .anyMatch(chunk -> chunk.getChunkIndex() == chunkIndex);
                logger.debug("检查数据库中分片信息 => fileMd5: {}, fileName: {}, chunkIndex: {}, exists: {}", 
                          fileMd5, fileName, chunkIndex, chunkInfoExists);
            } catch (Exception e) {
                logger.warn("检查数据库中分片信息失败 => fileMd5: {}, fileName: {}, chunkIndex: {}, 错误: {}", 
                          fileMd5, fileName, chunkIndex, e.getMessage(), e);
                // 失败时假设不存在，继续处理
                chunkInfoExists = false;
            }
            
            String chunkMd5 = null;
            String storagePath = null;
            
            if (chunkUploaded) {
                logger.warn("分片已在Redis中标记为已上传 => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}", fileMd5, fileName, fileType, chunkIndex);
                
                // 如果分片已上传但数据库中不存在记录，需要创建记录
                if (!chunkInfoExists) {
                    logger.info("分片已上传但数据库无记录，需要补充分片信息 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                    
                    // 计算分片的MD5值
//                    byte[] fileBytes = file.getBytes();
//                    chunkMd5 = DigestUtils.md5Hex(fileBytes);
                    chunkMd5 = DigestUtils.md5Hex(file.getInputStream());
                    
                    // 构建存储路径
                    storagePath = "chunks/" + fileMd5 + "/" + chunkIndex;
                    
                    // 检查MinIO中是否存在该分片
                    try {
                        StatObjectResponse stat = minioClient.statObject(
                            StatObjectArgs.builder()
                                .bucket("uploads")
                                .object(storagePath)
                                .build()
                        );
                        logger.info("MinIO中存在分片文件 => fileMd5: {}, fileName: {}, chunkIndex: {}, path: {}, size: {}", 
                                  fileMd5, fileName, chunkIndex, storagePath, stat.size());
                    } catch (Exception e) {
                        logger.warn("MinIO中不存在分片文件，需要重新上传 => fileMd5: {}, fileName: {}, chunkIndex: {}, 错误: {}", 
                                  fileMd5, fileName, chunkIndex, e.getMessage());
                        // 如果MinIO中不存在，将chunkUploaded设为false以触发上传流程
                        chunkUploaded = false;
                    }
                } else {
                    logger.info("分片已上传且数据库有记录，跳过处理 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                    return; // 完全跳过处理
                }
            }
            
            // 如果分片未上传或需要重新上传
            if (!chunkUploaded) {
                // 计算分片的 MD5 值
                logger.debug("计算分片MD5 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                byte[] fileBytes = file.getBytes();
                chunkMd5 = DigestUtils.md5Hex(fileBytes);
                logger.debug("分片MD5计算完成 => fileMd5: {}, fileName: {}, chunkIndex: {}, chunkMd5: {}", 
                           fileMd5, fileName, chunkIndex, chunkMd5);
                           
                // 构建分片的存储路径
                storagePath = "chunks/" + fileMd5 + "/" + chunkIndex;
                logger.debug("构建分片存储路径 => fileName: {}, path: {}", fileName, storagePath);

                try {
                    // 存储到 MinIO
                    logger.info("开始上传分片到MinIO => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}, bucket: uploads, path: {}, size: {}, contentType: {}", 
                              fileMd5, fileName, fileType, chunkIndex, storagePath, file.getSize(), contentType);
                    
                    PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                            .bucket("uploads")
                            .object(storagePath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build();
                    
                    minioClient.putObject(putObjectArgs);
                    logger.info("分片上传到MinIO成功 => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}", fileMd5, fileName, fileType, chunkIndex);
                } catch (Exception e) {
                    logger.error("分片上传到MinIO失败 => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}, 错误类型: {}, 错误信息: {}", 
                              fileMd5, fileName, fileType, chunkIndex, e.getClass().getName(), e.getMessage(), e);
                    
                    // 详细记录不同类型的MinIO错误
                    if (e instanceof io.minio.errors.ErrorResponseException) {
                        io.minio.errors.ErrorResponseException ere = (io.minio.errors.ErrorResponseException) e;
                        logger.error("MinIO错误响应详情 => fileName: {}, code: {}, message: {}, resource: {}, requestId: {}", 
                                 fileName, ere.errorResponse().code(), ere.errorResponse().message(), 
                                 ere.errorResponse().resource(), ere.errorResponse().requestId());
                    }
                    
                    throw new RuntimeException("上传分片到MinIO失败: " + e.getMessage(), e);
                }

                // 标记分片已上传
                try {
                    logger.debug("标记分片为已上传 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                    markChunkUploaded(fileMd5, chunkIndex, userId);
                    logger.debug("分片标记完成 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                } catch (Exception e) {
                    logger.error("标记分片已上传失败 => fileMd5: {}, fileName: {}, chunkIndex: {}, 错误: {}", 
                              fileMd5, fileName, chunkIndex, e.getMessage(), e);
                    // 这里不抛出异常，因为分片已经上传成功，即使标记失败也不影响后续操作
                }
            }
            
            // 不管分片是否已上传，都确保数据库中有分片信息
            if (!chunkInfoExists && chunkMd5 != null && storagePath != null) {
                try {
                    logger.debug("保存分片信息到数据库 => fileMd5: {}, fileName: {}, chunkIndex: {}, chunkMd5: {}, storagePath: {}", 
                              fileMd5, fileName, chunkIndex, chunkMd5, storagePath);
                    saveChunkInfo(fileMd5, chunkIndex, chunkMd5, storagePath);
                    logger.info("分片信息已保存到数据库 => fileMd5: {}, fileName: {}, chunkIndex: {}", fileMd5, fileName, chunkIndex);
                } catch (Exception e) {
                    logger.error("保存分片信息到数据库失败 => fileMd5: {}, fileName: {}, chunkIndex: {}, 错误: {}", 
                              fileMd5, fileName, chunkIndex, e.getMessage(), e);
                    throw new RuntimeException("保存分片信息失败: " + e.getMessage(), e);
                }
            }
            
            logger.info("分片处理完成 => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}", fileMd5, fileName, fileType, chunkIndex);
        } catch (Exception e) {
            logger.error("分片上传过程中发生错误 => fileMd5: {}, fileName: {}, fileType: {}, chunkIndex: {}, 错误类型: {}, 错误信息: {}", 
                       fileMd5, fileName, fileType, chunkIndex, e.getClass().getName(), e.getMessage(), e);
            throw e; // 重新抛出异常供上层处理
        }
    }

    /**
     * 根据文件名获取文件类型
     *
     * @param fileName 文件名
     * @return 文件类型
     */
    private String getFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "unknown";
        }
        
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        
        // 根据文件扩展名返回文件类型
        switch (extension) {
            case "pdf":
                return "PDF文档";
            case "doc":
            case "docx":
                return "Word文档";
            case "xls":
            case "xlsx":
                return "Excel表格";
            case "ppt":
            case "pptx":
                return "PowerPoint演示文稿";
            case "txt":
                return "文本文件";
            case "md":
                return "Markdown文档";
            case "jpg":
            case "jpeg":
                return "JPEG图片";
            case "png":
                return "PNG图片";
            case "gif":
                return "GIF图片";
            case "bmp":
                return "BMP图片";
            case "svg":
                return "SVG图片";
            case "mp4":
                return "MP4视频";
            case "avi":
                return "AVI视频";
            case "mov":
                return "MOV视频";
            case "wmv":
                return "WMV视频";
            case "mp3":
                return "MP3音频";
            case "wav":
                return "WAV音频";
            case "flac":
                return "FLAC音频";
            case "zip":
                return "ZIP压缩包";
            case "rar":
                return "RAR压缩包";
            case "7z":
                return "7Z压缩包";
            case "tar":
                return "TAR压缩包";
            case "gz":
                return "GZ压缩包";
            case "json":
                return "JSON文件";
            case "xml":
                return "XML文件";
            case "csv":
                return "CSV文件";
            case "html":
            case "htm":
                return "HTML文件";
            case "css":
                return "CSS文件";
            case "js":
                return "JavaScript文件";
            case "java":
                return "Java源码";
            case "py":
                return "Python源码";
            case "cpp":
            case "c":
                return "C/C++源码";
            case "sql":
                return "SQL文件";
            default:
                return extension.toUpperCase() + "文件";
        }
    }

    /**
     * 检查指定分片是否已上传（单个查询版本，性能较低）
     * 注意：对于批量查询建议使用 getUploadedChunks() 方法
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     * @param userId 用户ID
     * @return 分片是否已上传
     */
    public boolean isChunkUploaded(String fileMd5, int chunkIndex, String userId) {
        logger.debug("检查分片是否已上传 => fileMd5: {}, chunkIndex: {}, userId: {}", fileMd5, chunkIndex, userId);
        try {
            if (chunkIndex < 0) {
                logger.error("无效的分片索引 => fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex);
                throw new IllegalArgumentException("chunkIndex must be non-negative");
            }
            String redisKey = "upload:" + userId + ":" + fileMd5;
            boolean isUploaded = redisTemplate.opsForValue().getBit(redisKey, chunkIndex);
            logger.debug("分片上传状态 => fileMd5: {}, chunkIndex: {}, userId: {}, isUploaded: {}", 
                      fileMd5, chunkIndex, userId, isUploaded);
            return isUploaded;
        } catch (Exception e) {
            logger.error("检查分片上传状态失败 => fileMd5: {}, chunkIndex: {}, userId: {}, 错误: {}", 
                      fileMd5, chunkIndex, userId, e.getMessage(), e);
            return false; // 或者根据业务需求返回其他值
        }
    }

    /**
     * 标记指定分片为已上传
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     * @param userId 用户ID
     */
    public void markChunkUploaded(String fileMd5, int chunkIndex, String userId) {
        logger.debug("标记分片为已上传 => fileMd5: {}, chunkIndex: {}, userId: {}", fileMd5, chunkIndex, userId);
        try {
            if (chunkIndex < 0) {
                logger.error("无效的分片索引 => fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex);
                throw new IllegalArgumentException("chunkIndex must be non-negative");
            }
            String redisKey = "upload:" + userId + ":" + fileMd5;
            redisTemplate.opsForValue().setBit(redisKey, chunkIndex, true);
            logger.debug("分片已标记为已上传 => fileMd5: {}, chunkIndex: {}, userId: {}", fileMd5, chunkIndex, userId);
        } catch (Exception e) {
            logger.error("标记分片为已上传失败 => fileMd5: {}, chunkIndex: {}, userId: {}, 错误: {}", 
                      fileMd5, chunkIndex, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark chunk as uploaded", e);
        }
    }

    /**
     * 删除文件所有分片上传标记
     *
     * @param fileMd5 文件的 MD5 值
     * @param userId 用户ID
     */
    public void deleteFileMark(String fileMd5, String userId) {
        logger.debug("删除文件所有分片上传标记 => fileMd5: {}, userId: {}", fileMd5, userId);
        try {
            String redisKey = "upload:" + userId + ":" + fileMd5;
            redisTemplate.delete(redisKey);
            logger.info("文件分片上传标记已删除 => fileMd5: {}, userId: {}", fileMd5, userId);
        } catch (Exception e) {
            logger.error("删除文件分片上传标记失败 => fileMd5: {}, userId: {}, 错误: {}", fileMd5, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete file mark", e);
        }
    }


    /**
     * 获取已上传的分片列表
     *
     * @param fileMd5 文件的 MD5 值
     * @param userId 用户ID
     * @return 包含已上传分片索引的列表
     */
    public List<Integer> getUploadedChunks(String fileMd5, String userId) {
        logger.info("获取已上传分片列表 => fileMd5: {}, userId: {}", fileMd5, userId);
        List<Integer> uploadedChunks = new ArrayList<>();
        try {
            int totalChunks = getTotalChunks(fileMd5, userId);
            logger.debug("文件总分片数 => fileMd5: {}, userId: {}, totalChunks: {}", fileMd5, userId, totalChunks);
            
            if (totalChunks == 0) {
                logger.warn("文件总分片数为0 => fileMd5: {}, userId: {}", fileMd5, userId);
                return uploadedChunks;
            }
            
            // 优化：一次性获取所有分片状态
            String redisKey = "upload:" + userId + ":" + fileMd5;
            byte[] bitmapData = redisTemplate.execute((RedisCallback<byte[]>) connection -> {
                return connection.get(redisKey.getBytes());
            });
            
            if (bitmapData == null) {
                logger.info("Redis中无分片状态记录 => fileMd5: {}, userId: {}", fileMd5, userId);
                return uploadedChunks;
            }
            
            // 解析bitmap，找出已上传的分片
            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                if (isBitSet(bitmapData, chunkIndex)) {
                    uploadedChunks.add(chunkIndex);
                }
            }
            
            logger.info("获取到已上传分片列表 => fileMd5: {}, userId: {}, 已上传数量: {}, 总分片数: {}, 优化方式: 一次性获取", 
                      fileMd5, userId, uploadedChunks.size(), totalChunks);
            return uploadedChunks;
        } catch (Exception e) {
            logger.error("获取已上传分片列表失败 => fileMd5: {}, userId: {}, 错误: {}", fileMd5, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to get uploaded chunks", e);
        }
    }

    /**
     * 检查bitmap中指定位置是否为1
     *
     * @param bitmapData bitmap数据
     * @param bitIndex 位索引
     * @return 指定位置是否为1
     */
    private boolean isBitSet(byte[] bitmapData, int bitIndex) {
        try {
            int byteIndex = bitIndex / 8;
            int bitPosition = 7 - (bitIndex % 8); // Redis bitmap的位顺序是从高位到低位
            
            if (byteIndex >= bitmapData.length) {
                return false; // 超出范围的位默认为0
            }
            
            return (bitmapData[byteIndex] & (1 << bitPosition)) != 0;
        } catch (Exception e) {
            logger.error("检查bitmap位状态失败 => bitIndex: {}, 错误: {}", bitIndex, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取文件的总分片数
     *
     * @param fileMd5 文件的 MD5 值
     * @param userId 用户ID
     * @return 文件的总分片数
     */
    public int getTotalChunks(String fileMd5, String userId) {
        logger.info("计算文件总分片数 => fileMd5: {}, userId: {}", fileMd5, userId);
        try {
            Optional<FileUpload> fileUpload = fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, userId);
            
            if (fileUpload.isEmpty()) {
                logger.warn("文件记录不存在，无法计算分片数 => fileMd5: {}, userId: {}", fileMd5, userId);
                return 0;
            }
            
            long totalSize = fileUpload.get().getTotalSize();
            // 默认每个分片5MB
            int chunkSize = 5 * 1024 * 1024;
            int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
            
            logger.info("文件总分片数计算结果 => fileMd5: {}, userId: {}, totalSize: {}, chunkSize: {}, totalChunks: {}", 
                      fileMd5, userId, totalSize, chunkSize, totalChunks);
            return totalChunks;
        } catch (Exception e) {
            logger.error("计算文件总分片数失败 => fileMd5: {}, userId: {}, 错误: {}", fileMd5, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to calculate total chunks", e);
        }
    }

    /**
     * 保存分片信息到数据库
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     * @param chunkMd5 分片的 MD5 值
     * @param storagePath 分片的存储路径
     */
    private void saveChunkInfo(String fileMd5, int chunkIndex, String chunkMd5, String storagePath) {
        logger.debug("保存分片信息到数据库 => fileMd5: {}, chunkIndex: {}, chunkMd5: {}, storagePath: {}", 
                   fileMd5, chunkIndex, chunkMd5, storagePath);
        try {
            ChunkInfo chunkInfo = new ChunkInfo();
            chunkInfo.setFileMd5(fileMd5);
            chunkInfo.setChunkIndex(chunkIndex);
            chunkInfo.setChunkMd5(chunkMd5);
            chunkInfo.setStoragePath(storagePath);
            
            chunkInfoRepository.save(chunkInfo);
            logger.debug("分片信息已保存 => fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex);
        } catch (Exception e) {
            logger.error("保存分片信息失败 => fileMd5: {}, chunkIndex: {}, 错误: {}", 
                      fileMd5, chunkIndex, e.getMessage(), e);
            throw new RuntimeException("Failed to save chunk info", e);
        }
    }

    /**
     * 合并所有分片
     *
     * @param fileMd5 文件的 MD5 值
     * @param fileName 文件名
     * @param userId 用户ID
     * @return 合成文件的访问 URL
     */

    //没有自己拼文件，而是让 MinIO 合并
    public String mergeChunks(String fileMd5, String fileName, String userId) {
        String fileType = getFileType(fileName);
        logger.info("开始合并文件分片 => fileMd5: {}, fileName: {}, fileType: {}, userId: {}", fileMd5, fileName, fileType, userId);
        try {
            // 查询所有分片信息
            logger.debug("查询分片信息 => fileMd5: {}, fileName: {}", fileMd5, fileName);
            List<ChunkInfo> chunks = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5);
            logger.info("查询到分片信息 => fileMd5: {}, fileName: {}, fileType: {}, 分片数量: {}", fileMd5, fileName, fileType, chunks.size());
            
            // 检查分片数量是否与预期一致 - 校验分片完整性
            int expectedChunks = getTotalChunks(fileMd5, userId);
            if (chunks.size() != expectedChunks) {
                logger.error("分片数量不匹配 => fileMd5: {}, fileName: {}, fileType: {}, 期望: {}, 实际: {}", 
                          fileMd5, fileName, fileType, expectedChunks, chunks.size());
                throw new RuntimeException(String.format(
                    "分片数量不匹配，期望: %d, 实际: %d", expectedChunks, chunks.size()));
            }
            
            List<String> partPaths = chunks.stream()
                    .map(ChunkInfo::getStoragePath)
                    .collect(Collectors.toList());
            logger.debug("分片路径列表 => fileMd5: {}, fileName: {}, 路径数量: {}", fileMd5, fileName, partPaths.size());

            // 检查每个分片是否存在
            logger.info("开始检查每个分片是否存在 => fileMd5: {}, fileName: {}, fileType: {}", fileMd5, fileName, fileType);
            for (int i = 0; i < partPaths.size(); i++) {
                String path = partPaths.get(i);
                try {
                    StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder()
                            .bucket("uploads")
                            .object(path)
                            .build()
                    );
                    logger.debug("分片存在 => fileName: {}, index: {}, path: {}, size: {}", fileName, i, path, stat.size());
                } catch (Exception e) {
                    logger.error("分片不存在或无法访问 => fileName: {}, index: {}, path: {}, 错误: {}", 
                              fileName, i, path, e.getMessage(), e);
                    throw new RuntimeException("分片 " + i + " 不存在或无法访问: " + e.getMessage(), e);
                }
            }
            logger.info("分片检查完成，所有分片都存在 => fileMd5: {}, fileName: {}, fileType: {}", fileMd5, fileName, fileType);
            
            // 使用 MD5 作为 MinIO 对象路径，确保同名不同内容的文件不会互相覆盖
            String mergedPath = "merged/" + fileMd5;
            logger.info("开始合并分片 => fileMd5: {}, fileName: {}, fileType: {}, 合并后路径: {}", fileMd5, fileName, fileType, mergedPath);
            
            try {
                // 合并分片
                List<ComposeSource> sources = partPaths.stream()
                        .map(path -> ComposeSource.builder().bucket("uploads").object(path).build())
                        .collect(Collectors.toList());
                
                logger.debug("构建合并请求 => fileMd5: {}, fileName: {}, targetPath: {}, sourcePaths: {}", 
                          fileMd5, fileName, mergedPath, partPaths);
                
                minioClient.composeObject(
                        ComposeObjectArgs.builder()
                                .bucket("uploads")
                                .object(mergedPath)
                                .sources(sources)
                                .build()
                );
                logger.info("分片合并成功 => fileMd5: {}, fileName: {}, fileType: {}, mergedPath: {}", fileMd5, fileName, fileType, mergedPath);
                
                // 检查合并后的文件
                StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket("uploads")
                        .object(mergedPath)
                        .build()
                );
                logger.info("合并文件信息 => fileMd5: {}, fileName: {}, fileType: {}, path: {}, size: {}", fileMd5, fileName, fileType, mergedPath, stat.size());

                // 清理分片文件 - 删除分片（节省存储）
                logger.info("开始清理分片文件 => fileMd5: {}, fileName: {}, 分片数量: {}", fileMd5, fileName, partPaths.size());
                for (String path : partPaths) {
                    try {
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket("uploads")
                                        .object(path)
                                        .build()
                        );
                        logger.debug("分片文件已删除 => fileName: {}, path: {}", fileName, path);
                    } catch (Exception e) {
                        // 记录错误但不中断流程
                        logger.warn("删除分片文件失败，将继续处理 => fileName: {}, path: {}, 错误: {}", fileName, path, e.getMessage());
                    }
                }
                logger.info("分片文件清理完成 => fileMd5: {}, fileName: {}, fileType: {}", fileMd5, fileName, fileType);

                // 删除 Redis 中的分片状态记录 - 删除 Redis 状态
                logger.info("删除Redis中的分片状态记录 => fileMd5: {}, fileName: {}, userId: {}", fileMd5, fileName, userId);
                deleteFileMark(fileMd5, userId);
                logger.info("分片状态记录已删除 => fileMd5: {}, fileName: {}, userId: {}", fileMd5, fileName, userId);

                // 更新文件状态
                logger.info("更新文件状态为已完成 => fileMd5: {}, fileName: {}, fileType: {}, userId: {}", fileMd5, fileName, fileType, userId);
                FileUpload fileUpload = fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(fileMd5, userId)
                        .orElseThrow(() -> {
                            logger.error("更新文件状态失败，文件记录不存在 => fileMd5: {}, fileName: {}", fileMd5, fileName);
                            return new RuntimeException("文件记录不存在: " + fileMd5);
                        });
                fileUpload.setStatus(1); // 已完成
                fileUpload.setMergedAt(LocalDateTime.now());
                fileUploadRepository.save(fileUpload);
                logger.info("文件状态已更新为已完成 => fileMd5: {}, fileName: {}, fileType: {}", fileMd5, fileName, fileType);

                // 生成预签名 URL（有效期为 1 小时）
                //不走后端，直接访问 MinIO
                logger.info("开始生成预签名URL => fileMd5: {}, fileName: {}, path: {}", fileMd5, fileName, mergedPath);
                String presignedUrl = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket("uploads")
                                .object(mergedPath)
                                .expiry(1, TimeUnit.HOURS) // 设置有效期为 1 小时
                                .build()
                );
                logger.info("预签名URL已生成 => fileMd5: {}, fileName: {}, fileType: {}, URL: {}", fileMd5, fileName, fileType, presignedUrl);
                
                return presignedUrl;
            } catch (Exception e) {
                logger.error("合并文件失败 => fileMd5: {}, fileName: {}, fileType: {}, 错误类型: {}, 错误信息: {}", 
                          fileMd5, fileName, fileType, e.getClass().getName(), e.getMessage(), e);
                throw new RuntimeException("合并文件失败: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("文件合并过程中发生错误 => fileMd5: {}, fileName: {}, fileType: {}, 错误类型: {}, 错误信息: {}", 
                      fileMd5, fileName, fileType, e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("文件合并失败: " + e.getMessage(), e);
        }
    }

    public GetObjectResponse getMergedFileStream(String fileMd5) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket("uploads")
                        .object("merged/" + fileMd5)
                        .build()
        );
    }

    /**
     * 转换为公开 URL
     * @param minioUrl
     * @return
     */
    public String transToPublicUrl(String minioUrl) {
        if (StringUtils.isBlank(minioUrl) || Objects.equals(minioConfig.getEndpoint(), minioConfig.getPublicUrl())) {
            return minioUrl;
        }
        return minioUrl.replaceFirst(minioConfig.getEndpoint(), minioConfig.getPublicUrl());
    }
}
