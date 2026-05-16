package com.atguigu.user.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 * 提供文件上传、访问等核心功能
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file   上传的文件
     * @param userId 用户ID
     * @return 文件访问URL
     * @throws IllegalArgumentException 文件类型不支持或文件过大
     * @throws IllegalStateException    上传频率超限或存储失败
     */
    String uploadFile(MultipartFile file, Long userId);

    /**
     * 获取文件的物理存储路径
     *
     * @param relativePath 相对路径（如 2024/01/15/123/uuid.jpg）
     * @return 物理存储路径
     */
    String getPhysicalPath(String relativePath);

    /**
     * 检查文件是否存在
     *
     * @param relativePath 相对路径
     * @return 是否存在
     */
    boolean fileExists(String relativePath);

    /**
     * 删除文件
     *
     * @param relativePath 相对路径
     * @return 是否删除成功
     */
    boolean deleteFile(String relativePath);
}
