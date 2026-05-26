package com.atguigu.user.service.impl;

import com.atguigu.user.config.FileUploadProperties;
import com.atguigu.user.service.FileStorageService;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 本地文件存储服务实现
 * 实现文件类型校验、大小校验、上传频率限制、UUID文件名生成等功能
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageServiceImpl.class);

    private final FileUploadProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 允许的图片MIME类型
     */
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    ));

    /**
     * 文件魔数（用于验证文件真实类型）
     */
    private static final String[] JPEG_MAGIC = {"FFD8FF"};
    private static final String PNG_MAGIC = "89504E47";
    private static final String GIF_MAGIC = "47494638";
    private static final String WEBP_MAGIC = "52494646";

    /**
     * Redis上传频率限制key前缀
     */
    private static final String UPLOAD_RATE_LIMIT_KEY_PREFIX = "upload:rate:";

    /**
     * Sentinel资源名称
     */
    private static final String SENTINEL_RESOURCE_UPLOAD = "file-upload";

    @PostConstruct
    public void init() {
        // 确保上传目录存在
        Path uploadPath = Paths.get(properties.getPath());
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建文件上传目录: {}", uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("创建文件上传目录失败: {}", uploadPath.toAbsolutePath(), e);
            throw new IllegalStateException("无法创建文件上传目录: " + uploadPath.toAbsolutePath(), e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, Long userId) {
        // 1. Sentinel限流
        Entry entry = null;
        try {
            entry = SphU.entry(SENTINEL_RESOURCE_UPLOAD);

            // 2. 基础校验
            validateFile(file);

            // 3. 上传频率限制（Redis计数）
            checkUploadRateLimit(userId);

            // 4. 生成存储路径
            String relativePath = generateRelativePath(file, userId);
            Path physicalPath = Paths.get(properties.getPath(), relativePath);

            // 5. 创建目录
            createDirectoryIfNeeded(physicalPath.getParent());

            // 6. 保存文件
            saveFile(file, physicalPath);

            // 7. 返回访问URL
            String accessUrl = properties.getBaseUrl() + "/" + relativePath;
            log.info("文件上传成功: userId={}, path={}, size={}", userId, relativePath, file.getSize());

            return accessUrl;

        } catch (BlockException e) {
            log.warn("文件上传被限流: userId={}", userId);
            throw new IllegalStateException("上传请求过于频繁，请稍后再试");
        } catch (IOException e) {
            log.error("文件保存失败: userId={}", userId, e);
            throw new IllegalStateException("文件保存失败，请稍后重试");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    @Override
    public String getPhysicalPath(String relativePath) {
        // 防止路径遍历攻击
        if (isPathTraversalAttack(relativePath)) {
            log.warn("检测到路径遍历攻击尝试: {}", relativePath);
            throw new SecurityException("非法路径访问");
        }
        return Paths.get(properties.getPath(), relativePath).toString();
    }

    @Override
    public boolean fileExists(String relativePath) {
        if (isPathTraversalAttack(relativePath)) {
            return false;
        }
        Path path = Paths.get(properties.getPath(), relativePath);
        return Files.exists(path);
    }

    @Override
    public boolean deleteFile(String relativePath) {
        if (isPathTraversalAttack(relativePath)) {
            log.warn("检测到路径遍历攻击尝试: {}", relativePath);
            return false;
        }
        try {
            Path path = Paths.get(properties.getPath(), relativePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("文件删除成功: {}", relativePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("文件删除失败: {}", relativePath, e);
            return false;
        }
    }

    /**
     * 校验文件
     */
    private void validateFile(MultipartFile file) {
        // 空文件检查
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 文件大小检查
        if (file.getSize() > properties.getMaxSize()) {
            throw new IllegalArgumentException(
                    String.format("文件大小超过限制，最大允许%dMB", properties.getMaxSize() / 1024 / 1024));
        }

        // 文件扩展名检查
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!properties.getAllowedTypes().contains(extension)) {
            throw new IllegalArgumentException(
                    String.format("不支持的文件类型: %s，允许的类型: %s", extension, properties.getAllowedTypes()));
        }

        // MIME类型检查
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("文件MIME类型不支持");
        }

        // 文件魔数校验（防止伪造扩展名）
        try {
            if (!validateFileMagicNumber(file)) {
                throw new IllegalArgumentException("文件内容与扩展名不匹配，可能存在安全风险");
            }
        } catch (IOException e) {
            log.error("读取文件魔数失败", e);
            throw new IllegalArgumentException("文件校验失败");
        }
    }

    /**
     * 校验文件魔数
     */
    private boolean validateFileMagicNumber(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length < 8) {
            return false;
        }

        String hexHeader = bytesToHex(Arrays.copyOfRange(bytes, 0, 8));
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();

        return switch (extension) {
            case "jpg", "jpeg" ->
                // JPEG文件以FFD8FF开头
                    hexHeader.startsWith("FFD8FF");
            case "png" ->
                // PNG文件以89504E47开头
                    hexHeader.startsWith("89504E47");
            case "gif" ->
                // GIF文件以47494638开头
                    hexHeader.startsWith("47494638");
            case "webp" ->
                // WEBP文件以52494646开头，后面包含57454250
                    hexHeader.startsWith("52494646");
            default -> false;
        };
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * 检查上传频率限制
     */
    private void checkUploadRateLimit(Long userId) {
        String key = UPLOAD_RATE_LIMIT_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            // 第一次上传，设置过期时间为60秒
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        if (count != null && count > properties.getRateLimit()) {
            throw new IllegalStateException(
                    String.format("上传频率超限，每分钟最多上传%d次", properties.getRateLimit()));
        }
    }

    /**
     * 生成相对存储路径
     * 格式: {年}/{月}/{日}/{userId}/{uuid}.{ext}
     */
    private String generateRelativePath(MultipartFile file, Long userId) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = getFileExtension(file.getOriginalFilename());

        return String.format("%s/%s/%s/%s/%s.%s", year, month, day, userId, uuid, extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 创建目录（如果不存在）
     */
    private void createDirectoryIfNeeded(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            log.debug("创建目录: {}", directory);
        }
    }

    /**
     * 保存文件
     */
    private void saveFile(MultipartFile file, Path targetPath) throws IOException {
        // 检查磁盘空间
        checkDiskSpace(targetPath);

        try {
            // 使用临时文件 + 原子移动，防止文件损坏
            Path tempPath = Paths.get(targetPath.getParent().toString(), targetPath.getFileName() + ".tmp");
            file.transferTo(tempPath.toFile());

            // 原子性移动
            Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            log.error("文件保存失败: {}", targetPath, e);
            // 清理可能残留的临时文件
            Path tempPath = Paths.get(targetPath.getParent().toString(), targetPath.getFileName() + ".tmp");
            Files.deleteIfExists(tempPath);
            throw e;
        }
    }

    /**
     * 检查磁盘空间
     */
    private void checkDiskSpace(Path targetPath) {
        File parentDir = targetPath.getParent().toFile();
        if (!parentDir.exists()) {
            parentDir = new File(properties.getPath());
        }

        long freeSpace = parentDir.getFreeSpace();
        long minRequiredSpace = 100 * 1024 * 1024; // 最少需要100MB空闲空间

        if (freeSpace < minRequiredSpace) {
            log.error("磁盘空间不足: 可用空间={}MB, 需要空间={}MB",
                    freeSpace / 1024 / 1024, minRequiredSpace / 1024 / 1024);
            throw new IllegalStateException("磁盘空间不足，无法上传文件");
        }
    }

    /**
     * 检测路径遍历攻击
     */
    private boolean isPathTraversalAttack(String path) {
        if (path == null) {
            return true;
        }
        // 检查路径遍历字符
        return path.contains("..") || (path.contains("/") && path.startsWith("/")) || path.contains("\\");
    }
}
