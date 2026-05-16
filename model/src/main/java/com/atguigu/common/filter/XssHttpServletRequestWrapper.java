package com.atguigu.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * XSS攻击防护请求包装器
 * 
 * 功能说明：
 * 1. 对请求参数进行XSS过滤
 * 2. 防止脚本注入攻击
 * 3. 支持多种XSS攻击向量的检测和过滤
 * 
 * 安全特性：
 * - 覆盖HTML标签注入
 * - 覆盖JavaScript事件处理器
 * - 覆盖CSS表达式
 * - 覆盖SVG/MathML向量
 * - 覆盖数据URI攻击
 * - HTML实体编码防护
 * 
 * 注意事项：
 * - 此包装器仅过滤请求参数和头部，不处理请求体
 * - 对于JSON请求体，应在业务层进行处理
 * - 建议配合前端XSS防护使用
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(XssHttpServletRequestWrapper.class);

    /**
     * XSS攻击模式匹配
     * 覆盖常见的XSS攻击向量
     */
    private static final Pattern[] XSS_PATTERNS = {
            // Script标签
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            
            // 事件处理器（覆盖所有on*事件）
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            
            // JavaScript协议
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data\\s*:\\s*text/html", Pattern.CASE_INSENSITIVE),
            
            // CSS表达式
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("behavior\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("-moz-binding\\s*:", Pattern.CASE_INSENSITIVE),
            
            // HTML注入向量
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<applet[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<link[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<base[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE),
            
            // SVG向量
            Pattern.compile("<svg[^>]*onload[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<svg[^>]*>", Pattern.CASE_INSENSITIVE),
            
            // MathML向量
            Pattern.compile("<math[^>]*>", Pattern.CASE_INSENSITIVE),
            
            // 其他危险标签
            Pattern.compile("<img[^>]*onerror[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<body[^>]*onload[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<input[^>]*onfocus[^>]*>", Pattern.CASE_INSENSITIVE),
            
            // eval和类似函数
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("setTimeout\\s*\\([^)]*['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("setInterval\\s*\\([^)]*['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Function\\s*\\(", Pattern.CASE_INSENSITIVE)
    };

    // 需要特殊处理的参数名（不进行XSS过滤）
    private static final Pattern[] SKIP_PARAM_PATTERNS = {
            // 密码字段
            Pattern.compile("password", Pattern.CASE_INSENSITIVE),
            Pattern.compile("passwd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pwd", Pattern.CASE_INSENSITIVE),
            // Token字段
            Pattern.compile("token", Pattern.CASE_INSENSITIVE),
            Pattern.compile("secret", Pattern.CASE_INSENSITIVE),
            Pattern.compile("key", Pattern.CASE_INSENSITIVE)
    };

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String[] getParameterValues(String parameter) {
        String[] values = super.getParameterValues(parameter);
        if (values == null) {
            return null;
        }
        
        // 检查是否为需要跳过的参数
        if (shouldSkipParameter(parameter)) {
            return values;
        }
        
        int count = values.length;
        String[] encodedValues = new String[count];
        for (int i = 0; i < count; i++) {
            encodedValues[i] = stripXSS(values[i]);
        }
        return encodedValues;
    }

    @Override
    public String getParameter(String parameter) {
        String value = super.getParameter(parameter);
        
        // 检查是否为需要跳过的参数
        if (shouldSkipParameter(parameter)) {
            return value;
        }
        
        return stripXSS(value);
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        // 头部通常不需要过滤，但为了安全起见，过滤可能注入的头
        // 排除一些标准头部
        if (name != null && (name.equalsIgnoreCase("Authorization") 
                || name.equalsIgnoreCase("Cookie")
                || name.equalsIgnoreCase("X-Token"))) {
            return value;
        }
        return stripXSS(value);
    }

    /**
     * 检查参数是否需要跳过XSS过滤
     * 密码、Token等敏感字段不应被修改
     *
     * @param parameterName 参数名
     * @return true表示跳过过滤
     */
    private boolean shouldSkipParameter(String parameterName) {
        if (parameterName == null) {
            return false;
        }
        
        for (Pattern pattern : SKIP_PARAM_PATTERNS) {
            if (pattern.matcher(parameterName).matches()) {
                log.debug("跳过敏感参数的XSS过滤: {}", parameterName);
                return true;
            }
        }
        return false;
    }

    /**
     * XSS过滤核心方法
     * 移除所有潜在的XSS攻击脚本
     *
     * @param value 原始字符串
     * @return 过滤后的安全字符串
     */
    private String stripXSS(String value) {
        if (value == null) {
            return null;
        }
        
        if (value.isEmpty()) {
            return value;
        }

        // 应用所有XSS模式进行过滤
        String cleanValue = value;
        for (Pattern pattern : XSS_PATTERNS) {
            cleanValue = pattern.matcher(cleanValue).replaceAll("");
        }

        // HTML实体编码（仅对特殊字符进行编码）
        cleanValue = htmlEncode(cleanValue);

        return cleanValue;
    }

    /**
     * HTML实体编码
     * 将特殊字符转换为HTML实体，防止HTML注入
     * 
     * 注意：此方法会修改原始数据，应在输出时进行编码而非输入时
     * 当前实现保留此方法以兼容现有逻辑，建议后续优化
     *
     * @param value 原始字符串
     * @return 编码后的字符串
     */
    private String htmlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        StringBuilder encoded = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<':
                    encoded.append("&lt;");
                    break;
                case '>':
                    encoded.append("&gt;");
                    break;
                case '&':
                    // 避免双重编码：检查是否已经是HTML实体
                    if (isHtmlEntity(value, i)) {
                        encoded.append(c);
                    } else {
                        encoded.append("&amp;");
                    }
                    break;
                case '"':
                    encoded.append("&quot;");
                    break;
                case '\'':
                    encoded.append("&#x27;");
                    break;
                default:
                    encoded.append(c);
            }
        }
        return encoded.toString();
    }

    /**
     * 检查当前位置是否为HTML实体的开始
     * 用于避免双重编码
     *
     * @param value 字符串
     * @param index &符号的位置
     * @return true表示是HTML实体
     */
    private boolean isHtmlEntity(String value, int index) {
        // 检查是否是 &#x 或 &#X 或 &# 开头（数字实体）
        // 或者是 &字母 开头（命名实体）
        if (index + 1 < value.length()) {
            char next = value.charAt(index + 1);
            // 数字实体: &#x... 或 &#...
            if (next == '#') {
                return true;
            }
            // 命名实体: &lt; &gt; &amp; &quot; 等
            if (Character.isLetter(next)) {
                // 查找分号结束
                int semicolonIndex = value.indexOf(';', index);
                if (semicolonIndex > 0 && semicolonIndex - index <= 10) {
                    return true;
                }
            }
        }
        return false;
    }
}
