package com.atguigu.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveDataMasker 单元测试类
 * 测试敏感数据脱敏功能
 */
@DisplayName("SensitiveDataMasker 单元测试")
class SensitiveDataMaskerTest {

    @Nested
    @DisplayName("maskPhone 方法测试")
    class MaskPhoneTests {

        @Test
        @DisplayName("应该正确脱敏手机号")
        void should_maskPhoneCorrectly() {
            // Given
            String phone = "13812345678";

            // When
            String masked = SensitiveDataMasker.maskPhone(phone);

            // Then
            assertEquals("138****5678", masked, "手机号脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留前3位和后4位")
        void should_keepFirst3AndLast4Digits() {
            // Given
            String phone = "18666668888";

            // When
            String masked = SensitiveDataMasker.maskPhone(phone);

            // Then
            assertTrue(masked.startsWith("186"), "应保留前3位");
            assertTrue(masked.endsWith("8888"), "应保留后4位");
            assertTrue(masked.contains("****"), "中间应为****");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"123456", "1234567", "1234567890"})
        @DisplayName("应该返回原值对于null、空字符串或长度不足的手机号")
        void should_returnOriginalValue_forInvalidPhone(String phone) {
            String masked = SensitiveDataMasker.maskPhone(phone);
            assertEquals(phone, masked, "无效手机号应返回原值");
        }

        @Test
        @DisplayName("应该正确处理11位手机号边界")
        void should_handleBoundaryLengthPhone() {
            String phone11 = "12345678901";
            String masked = SensitiveDataMasker.maskPhone(phone11);
            assertEquals("123****8901", masked, "11位手机号应正确脱敏");
        }
    }

    @Nested
    @DisplayName("maskEmail 方法测试")
    class MaskEmailTests {

        @Test
        @DisplayName("应该正确脱敏邮箱")
        void should_maskEmailCorrectly() {
            // Given
            String email = "example@domain.com";

            // When
            String masked = SensitiveDataMasker.maskEmail(email);

            // Then
            assertEquals("e***@domain.com", masked, "邮箱脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留首字符和@之后的内容")
        void should_keepFirstCharAndAfterAt() {
            // Given
            String email = "testuser@example.org";

            // When
            String masked = SensitiveDataMasker.maskEmail(email);

            // Then
            assertTrue(masked.startsWith("t"), "应保留首字符");
            assertTrue(masked.contains("@example.org"), "应保留@及之后的内容");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"noatsymbol", "@"})
        @DisplayName("应该返回原值对于无效邮箱")
        void should_returnOriginalValue_forInvalidEmail(String email) {
            // When
            String masked = SensitiveDataMasker.maskEmail(email);

            // Then
            assertEquals(email, masked, "无效邮箱应返回原值");
        }

        @Test
        @DisplayName("应该正确处理短邮箱名")
        void should_handleShortEmailName() {
            // Given
            String email = "a@b.com";

            // When
            String masked = SensitiveDataMasker.maskEmail(email);

            // Then
            assertEquals(email, masked, "过短的邮箱名应返回原值");
        }
    }

    @Nested
    @DisplayName("maskPhoneOrEmail 方法测试")
    class MaskPhoneOrEmailTests {

        @Test
        @DisplayName("应该自动识别并脱敏手机号")
        void should_autoDetectAndMaskPhone() {
            // Given
            String phone = "13812345678";

            // When
            String masked = SensitiveDataMasker.maskPhoneOrEmail(phone);

            // Then
            assertEquals("138****5678", masked, "应自动识别为手机号并脱敏");
        }

        @Test
        @DisplayName("应该自动识别并脱敏邮箱")
        void should_autoDetectAndMaskEmail() {
            // Given
            String email = "test@example.com";

            // When
            String masked = SensitiveDataMasker.maskPhoneOrEmail(email);

            // Then
            assertEquals("t***@example.com", masked, "应自动识别为邮箱并脱敏");
        }

        @Test
        @DisplayName("应该返回null对于null输入")
        void should_returnNullForNullInput() {
            // When
            String masked = SensitiveDataMasker.maskPhoneOrEmail(null);

            // Then
            assertNull(masked, "null输入应返回null");
        }
    }

    @Nested
    @DisplayName("maskCode 方法测试")
    class MaskCodeTests {

        @Test
        @DisplayName("应该正确脱敏验证码")
        void should_maskCodeCorrectly() {
            // Given
            String code = "123456";

            // When
            String masked = SensitiveDataMasker.maskCode(code);

            // Then
            assertEquals("1****", masked, "验证码脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留首字符")
        void should_keepFirstChar() {
            // Given
            String code = "987654321";

            // When
            String masked = SensitiveDataMasker.maskCode(code);

            // Then
            assertTrue(masked.startsWith("9"), "应保留首字符");
            assertTrue(masked.contains("****"), "应包含****");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"1"})
        @DisplayName("应该返回原值对于null、空字符串或长度不足的验证码")
        void should_returnOriginalValue_forInvalidCode(String code) {
            // When
            String masked = SensitiveDataMasker.maskCode(code);

            // Then
            assertEquals(code, masked, "无效验证码应返回原值");
        }
    }

    @Nested
    @DisplayName("maskPassword 方法测试")
    class MaskPasswordTests {

        @Test
        @DisplayName("应该返回固定长度的星号（无参数版本）")
        void should_returnFixedAsterisks() {
            // When
            String masked = SensitiveDataMasker.maskPassword();

            // Then
            assertEquals("******", masked, "密码脱敏应返回固定星号");
        }

        @Test
        @DisplayName("应该忽略输入参数返回固定星号")
        void should_ignoreInputAndReturnFixedAsterisks() {
            // When
            String masked = SensitiveDataMasker.maskPassword("anyPassword123");

            // Then
            assertEquals("******", masked, "密码脱敏应忽略输入返回固定星号");
        }

        @Test
        @DisplayName("应该忽略null输入")
        void should_ignoreNullInput() {
            // When
            String masked = SensitiveDataMasker.maskPassword(null);

            // Then
            assertEquals("******", masked, "null密码也应返回固定星号");
        }
    }

    @Nested
    @DisplayName("maskIdCard 方法测试")
    class MaskIdCardTests {

        @Test
        @DisplayName("应该正确脱敏18位身份证号")
        void should_maskIdCardCorrectly() {
            // Given
            String idCard = "110101199001011234";

            // When
            String masked = SensitiveDataMasker.maskIdCard(idCard);

            // Then
            assertEquals("1101**********1234", masked, "身份证号脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留前4位和后4位")
        void should_keepFirst4AndLast4Digits() {
            // Given
            String idCard = "123456789012345678";

            // When
            String masked = SensitiveDataMasker.maskIdCard(idCard);

            // Then
            assertTrue(masked.startsWith("1234"), "应保留前4位");
            assertTrue(masked.endsWith("5678"), "应保留后4位");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"1234567"})
        @DisplayName("应该返回原值对于无效身份证号")
        void should_returnOriginalValue_forInvalidIdCard(String idCard) {
            String masked = SensitiveDataMasker.maskIdCard(idCard);
            assertEquals(idCard, masked, "无效身份证号应返回原值");
        }

        @Test
        @DisplayName("应该正确脱敏15位身份证号")
        void should_mask15DigitIdCardCorrectly() {
            String idCard = "110101900101123";
            String masked = SensitiveDataMasker.maskIdCard(idCard);
            assertEquals("1101*********23", masked, "15位身份证号脱敏结果不正确");
        }

        @Test
        @DisplayName("15位身份证号应保留前4位和后2位")
        void should_keepFirst4AndLast2Digits_for15DigitIdCard() {
            String idCard = "123456789012345";
            String masked = SensitiveDataMasker.maskIdCard(idCard);
            assertTrue(masked.startsWith("1234"), "应保留前4位");
            assertTrue(masked.endsWith("45"), "应保留后2位");
        }
    }

    @Nested
    @DisplayName("maskBankCard 方法测试")
    class MaskBankCardTests {

        @Test
        @DisplayName("应该正确脱敏银行卡号")
        void should_maskBankCardCorrectly() {
            // Given
            String bankCard = "6222021234567890123";

            // When
            String masked = SensitiveDataMasker.maskBankCard(bankCard);

            // Then
            assertEquals("6222****0123", masked, "银行卡号脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留前4位和后4位")
        void should_keepFirst4AndLast4Digits() {
            // Given
            String bankCard = "1234567890123456";

            // When
            String masked = SensitiveDataMasker.maskBankCard(bankCard);

            // Then
            assertTrue(masked.startsWith("1234"), "应保留前4位");
            assertTrue(masked.endsWith("3456"), "应保留后4位");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"1234567", "123456789012345", "12345678901234567890"})
        @DisplayName("应该返回原值对于无效银行卡号")
        void should_returnOriginalValue_forInvalidBankCard(String bankCard) {
            String masked = SensitiveDataMasker.maskBankCard(bankCard);
            assertEquals(bankCard, masked, "无效银行卡号应返回原值");
        }

        @Test
        @DisplayName("应该正确脱敏16位银行卡号")
        void should_mask16DigitBankCardCorrectly() {
            String bankCard = "1234567890123456";
            String masked = SensitiveDataMasker.maskBankCard(bankCard);
            assertEquals("1234****3456", masked, "16位银行卡号脱敏结果不正确");
        }

        @Test
        @DisplayName("应该正确脱敏19位银行卡号")
        void should_mask19DigitBankCardCorrectly() {
            String bankCard = "6222021234567890123";
            String masked = SensitiveDataMasker.maskBankCard(bankCard);
            assertEquals("6222****0123", masked, "19位银行卡号脱敏结果不正确");
        }
    }

    @Nested
    @DisplayName("maskName 方法测试")
    class MaskNameTests {

        @Test
        @DisplayName("应该正确脱敏两字姓名")
        void should_maskTwoCharNameCorrectly() {
            // Given
            String name = "张三";

            // When
            String masked = SensitiveDataMasker.maskName(name);

            // Then
            assertEquals("张*", masked, "两字姓名脱敏结果不正确");
        }

        @Test
        @DisplayName("应该正确脱敏三字姓名")
        void should_maskThreeCharNameCorrectly() {
            // Given
            String name = "王小明";

            // When
            String masked = SensitiveDataMasker.maskName(name);

            // Then
            assertEquals("王**", masked, "三字姓名脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留姓氏")
        void should_keepSurname() {
            // Given
            String name = "李四";

            // When
            String masked = SensitiveDataMasker.maskName(name);

            // Then
            assertTrue(masked.startsWith("李"), "应保留姓氏");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"张"})
        @DisplayName("应该返回原值对于无效姓名")
        void should_returnOriginalValue_forInvalidName(String name) {
            // When
            String masked = SensitiveDataMasker.maskName(name);

            // Then
            assertEquals(name, masked, "无效姓名应返回原值");
        }
    }

    @Nested
    @DisplayName("maskAddress 方法测试")
    class MaskAddressTests {

        @Test
        @DisplayName("应该正确脱敏地址")
        void should_maskAddressCorrectly() {
            // Given
            String address = "北京市朝阳区某某街道某某小区";

            // When
            String masked = SensitiveDataMasker.maskAddress(address);

            // Then
            assertEquals("北京市朝阳区****", masked, "地址脱敏结果不正确");
        }

        @Test
        @DisplayName("应该保留前6个字符")
        void should_keepFirst6Chars() {
            String address = "上海市浦东新区某某路123号";

            String masked = SensitiveDataMasker.maskAddress(address);

            assertTrue(masked.startsWith("上海市浦东新"), "应保留前6个字符");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"12345"})
        @DisplayName("应该返回原值对于无效地址")
        void should_returnOriginalValue_forInvalidAddress(String address) {
            // When
            String masked = SensitiveDataMasker.maskAddress(address);

            // Then
            assertEquals(address, masked, "无效地址应返回原值");
        }
    }

    @Nested
    @DisplayName("maskSensitiveInfo 方法测试")
    class MaskSensitiveInfoTests {

        @Test
        @DisplayName("应该脱敏文本中的手机号")
        void should_maskPhoneInText() {
            // Given
            String text = "用户手机号13812345678已注册";

            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);

            // Then
            assertTrue(masked.contains("138****5678"), "应脱敏手机号");
            assertFalse(masked.contains("13812345678"), "不应包含原始手机号");
        }

        @Test
        @DisplayName("应该脱敏文本中的邮箱")
        void should_maskEmailInText() {
            // Given
            String text = "发送邮件到test@example.com";

            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);

            // Then
            assertTrue(masked.contains("t***@"), "应脱敏邮箱");
        }

        @Test
        @DisplayName("应该脱敏文本中的身份证号")
        void should_maskIdCardInText() {
            // Given
            String text = "身份证号110101199001011234验证通过";

            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);

            // Then
            assertTrue(masked.contains("1101**********1234"), "应脱敏身份证号");
        }

        @Test
        @DisplayName("应该脱敏文本中的密码字段")
        void should_maskPasswordFieldInText() {
            // Given
            String text = "password=admin123";

            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);

            // Then
            assertTrue(masked.contains("password=******"), "应脱敏密码字段");
        }

        @Test
        @DisplayName("应该脱敏文本中的secret字段")
        void should_maskSecretFieldInText() {
            // Given
            String text = "secret=mySecretKey";

            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);

            // Then
            assertTrue(masked.contains("secret=******"), "应脱敏secret字段");
        }

        @Test
        @DisplayName("应该返回null对于null输入")
        void should_returnNullForNullInput() {
            // When
            String masked = SensitiveDataMasker.maskSensitiveInfo(null);

            // Then
            assertNull(masked, "null输入应返回null");
        }

        @Test
        @DisplayName("应该同时脱敏多种敏感信息")
        void should_maskMultipleSensitiveInfo() {
            String text = "用户13812345678，邮箱test@example.com，密码password=abc123";
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);
            assertTrue(masked.contains("138****5678"), "应脱敏手机号");
            assertTrue(masked.contains("t***@"), "应脱敏邮箱");
            assertTrue(masked.contains("password=******"), "应脱敏密码");
        }

        @Test
        @DisplayName("18位银行卡号应由银行卡正则匹配而非身份证正则")
        void should_matchBankCardRegex_for18DigitBankCard() {
            String text = "银行卡622848123456789012已绑定";
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);
            assertTrue(masked.contains("6228****9012"), "18位银行卡号应由银行卡正则匹配");
            assertFalse(masked.contains("6228**********9012"), "18位银行卡号不应由身份证正则匹配");
        }

        @Test
        @DisplayName("18位身份证号应由身份证正则匹配")
        void should_matchIdCardRegex_for18DigitIdCard() {
            String text = "身份证110101199001011234验证通过";
            String masked = SensitiveDataMasker.maskSensitiveInfo(text);
            assertTrue(masked.contains("1101**********1234"), "18位身份证号应由身份证正则匹配");
        }
    }
}
