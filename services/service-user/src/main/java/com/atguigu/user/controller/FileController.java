package com.atguigu.user.controller;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.result.R;
import com.atguigu.user.service.FileStorageService;
import com.atguigu.user.service.UserAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件管理控制器
 * 提供文件上传和访问接口
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileStorageService fileStorageService;
    private final UserAuthService userAuthService;

    /**
     * 上传头像
     *
     * @param file 头像文件
     * @return 上传结果，包含头像URL
     */
    @PostMapping("/avatar")
    public R uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 权限校验
        UserInfo currentUser = UserContext.get();
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }

        try {
            // 上传文件
            String avatarUrl = fileStorageService.uploadFile(file, currentUser.getId());

            // 更新用户头像URL
            userAuthService.updateAvatarUrl(currentUser.getId(), avatarUrl);

            Map<String, Object> data = new HashMap<>();
            data.put("avatarUrl", avatarUrl);
            return R.ok("头像上传成功", data);

        } catch (IllegalArgumentException e) {
            log.warn("头像上传参数错误: userId={}, error={}", currentUser.getId(), e.getMessage());
            return R.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("头像上传状态错误: userId={}, error={}", currentUser.getId(), e.getMessage());
            return R.error(429, e.getMessage());
        } catch (Exception e) {
            log.error("头像上传失败: userId={}", currentUser.getId(), e);
            return R.internalServerError("头像上传失败，请稍后重试");
        }
    }

    /**
     * 通用文件上传
     *
     * @param file 文件
     * @return 上传结果，包含文件URL
     */
    @PostMapping("/upload")
    public R uploadFile(@RequestParam("file") MultipartFile file) {
        // 权限校验
        UserInfo currentUser = UserContext.get();
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }

        try {
            String fileUrl = fileStorageService.uploadFile(file, currentUser.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("fileUrl", fileUrl);
            data.put("fileName", file.getOriginalFilename());
            data.put("fileSize", file.getSize());
            return R.ok("文件上传成功", data);

        } catch (IllegalArgumentException e) {
            log.warn("文件上传参数错误: userId={}, error={}", currentUser.getId(), e.getMessage());
            return R.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("文件上传状态错误: userId={}, error={}", currentUser.getId(), e.getMessage());
            return R.error(429, e.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败: userId={}", currentUser.getId(), e);
            return R.internalServerError("文件上传失败，请稍后重试");
        }
    }

    /**
     * 访问文件
     * 路径格式: /api/file/{year}/{month}/{day}/{userId}/{filename}
     *
     * @param response HTTP响应
     * @param year     年份
     * @param month    月份
     * @param day      日期
     * @param userId   用户ID
     * @param filename 文件名
     */
    @GetMapping("/{year}/{month}/{day}/{userId}/{filename}")
    public void getFile(
            HttpServletResponse response,
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String day,
            @PathVariable String userId,
            @PathVariable String filename) {

        // 构建相对路径
        String relativePath = String.format("%s/%s/%s/%s/%s", year, month, day, userId, filename);

        // 获取物理路径
        String physicalPath = fileStorageService.getPhysicalPath(relativePath);
        Path filePath = Paths.get(physicalPath);

        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 获取文件MIME类型
        String contentType = getContentType(filename);

        // 设置响应头
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=31536000"); // 缓存1年
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodeFilename(filename) + "\"");

        // 流式输出文件
        try (FileInputStream fis = new FileInputStream(physicalPath);
             OutputStream os = response.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();

        } catch (IOException e) {
            log.error("文件读取失败: {}", physicalPath, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String extension = "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filename.substring(lastDot + 1).toLowerCase();
        }

        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    /**
     * 编码文件名（处理中文等特殊字符）
     */
    private String encodeFilename(String filename) {
        try {
            return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            log.warn("文件名编码异常: filename={}", filename, e);
            return filename;
        }
    }
}
