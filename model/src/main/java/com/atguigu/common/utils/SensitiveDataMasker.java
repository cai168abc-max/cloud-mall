package com.atguigu.common.utils;

/**
 * 敏感数据脱敏工具类
 * 用于日志输出、API响应等场景的敏感信息脱敏处理
 */
public class SensitiveDataMasker {
    
    private SensitiveDataMasker() {
        // 工具类，禁止实例化
    }
    
    /**
     * 手机号脱敏
     * 保留前3位和后4位，中间用****替代
     * 例如：13812345678 -> 138****5678
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        if (phone.length() <= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 1);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
    
    /**
     * 邮箱脱敏
     * 保留首字符和@及之后的内容，中间用***替代
     * 例如：example@domain.com -> e***@domain.com
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱地址
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) {
            return email;
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }
    
    /**
     * 手机号或邮箱脱敏（自动识别）
     * 根据是否包含@符号自动判断是邮箱还是手机号
     *
     * @param phoneOrEmail 手机号或邮箱地址
     * @return 脱敏后的字符串
     */
    public static String maskPhoneOrEmail(String phoneOrEmail) {
        if (phoneOrEmail == null) {
            return null;
        }
        if (phoneOrEmail.contains("@")) {
            return maskEmail(phoneOrEmail);
        } else {
            return maskPhone(phoneOrEmail);
        }
    }
    
    /**
     * 验证码脱敏
     * 保留首字符，其余用****替代
     * 例如：123456 -> 1****
     *
     * @param code 验证码
     * @return 脱敏后的验证码
     */
    public static String maskCode(String code) {
        if (code == null || code.length() < 2) {
            return code;
        }
        return code.substring(0, 1) + "****";
    }
    
    /**
     * 密码脱敏
     * 统一返回固定长度的星号
     *
     * @return 固定返回******
     */
    public static String maskPassword() {
        return "******";
    }
    
    /**
     * 密码脱敏（带参数版本）
     * 忽略传入的密码参数，统一返回固定长度的星号
     *
     * @param password 密码（将被忽略）
     * @return 固定返回******
     */
    public static String maskPassword(String password) {
        return "******";
    }
    
    /**
     * 身份证号脱敏
     * 保留前6位和后4位，中间用*替代
     * 例如：110101199001011234 -> 110101********1234
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        int maskLen = idCard.length() - 10;
        if (maskLen <= 0) {
            return idCard;
        }
        StringBuilder mask = new StringBuilder();
        for (int i = 0; i < maskLen; i++) {
            mask.append("*");
        }
        return idCard.substring(0, 6) + mask + idCard.substring(idCard.length() - 4);
    }
    
    /**
     * 银行卡号脱敏
     * 保留前4位和后4位，中间用****替代
     * 例如：6222021234567890123 -> 6222****0123
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "****" + bankCard.substring(bankCard.length() - 4);
    }
    
    /**
     * 姓名脱敏
     * 保留姓氏，名字用*替代
     * 例如：张三 -> 张*，王小明 -> 王**
     *
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        StringBuilder masked = new StringBuilder(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            masked.append("*");
        }
        return masked.toString();
    }
    
    /**
     * 地址脱敏
     * 保留省市区，详细地址用****替代
     * 例如：北京市朝阳区某某街道某某小区 -> 北京市朝阳区****
     *
     * @param address 地址
     * @return 脱敏后的地址
     */
    public static String maskAddress(String address) {
        if (address == null || address.length() <= 6) {
            return address;
        }
        // 简单实现：保留前6个字符
        return address.substring(0, 6) + "****";
    }
    
    /**
     * 敏感信息脱敏（通用方法）
     * 用于处理异常堆栈等包含多种敏感信息的文本
     * 
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String maskSensitiveInfo(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        
        // 手机号脱敏（11位数字）
        result = result.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        
        // 邮箱脱敏
        result = result.replaceAll("([a-zA-Z0-9])[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", "$1***@$2");
        
        // 身份证号脱敏（18位，支持X/x结尾）
        result = result.replaceAll("(\\d{6})\\d{8}(\\d{3}[\\dXx])", "$1********$2");
        
        // 银行卡号脱敏（16-19位）
        result = result.replaceAll("(\\d{4})\\d{8,11}(\\d{4})", "$1****$2");
        
        // 密码相关字段脱敏
        result = result.replaceAll("(password|passwd|pwd)[\"']?\\s*[:=]\\s*[\"']?[^,}\\s\"']+", "$1=******");
        result = result.replaceAll("(secret|token|key)[\"']?\\s*[:=]\\s*[\"']?[^,}\\s\"']+", "$1=******");
        
        return result;
    }
}
