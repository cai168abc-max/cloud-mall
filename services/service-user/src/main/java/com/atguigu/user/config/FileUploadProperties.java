package com.atguigu.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传配置属性
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadProperties {

    /**
     * 文件存储根路径
     */
    private String path = "/data/uploads";

    /**
     * 最大文件大小（字节），默认5MB
     */
    private long maxSize = 5 * 1024 * 1024;

    /**
     * 允许的文件类型（扩展名）
     */
    private List<String> allowedTypes = new ArrayList<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    /**
     * 上传频率限制（每分钟次数）
     */
    private int rateLimit = 10;

    /**
     * 文件访问基础URL
     */
    private String baseUrl = "/api/file";

    /**
     * 设置允许的文件类型（防御性拷贝）
     */
    public void setAllowedTypes(final List<String> allowedTypes) {
        this.allowedTypes = new ArrayList<>(allowedTypes);
    }

    /**
     * 获取允许的文件类型（返回副本，防止外部修改）
     */
    public List<String> getAllowedTypes() {
        return new ArrayList<>(allowedTypes);
    }
}
