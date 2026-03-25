package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ParseService 的测试类
 * 主要测试 splitLongSentence 方法的功能
 */
@SpringBootTest
class ParseServiceTest {

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @InjectMocks
    private ParseService parseService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 设置配置值
        ReflectionTestUtils.setField(parseService, "chunkSize", 1000);
        ReflectionTestUtils.setField(parseService, "bufferSize", 8192);
        ReflectionTestUtils.setField(parseService, "maxMemoryThreshold", 0.8);
    }

    @Test
    void testSplitLongSentence_NormalChineseText() throws Exception {
        // 准备测试数据 - 正常中文文本
        String sentence = "这是一个测试句子，用来验证HanLP分词功能是否正常工作。我们需要确保它能够正确地进行语义切割，而不是简单的字符分割。";
        int chunkSize = 30;

        // 使用反射调用私有方法
        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果
        assertNotNull(result, "分割结果不应为空");
        assertFalse(result.isEmpty(), "分割结果不应为空列表");
        
        // 验证每个分块的长度都不超过限制（除了最后一个可能较短）
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).length() <= chunkSize, 
                "分块 " + i + " 的长度超过了限制: " + result.get(i).length());
        }
        
        // 验证所有分块拼接后等于原文
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed, "分割后重新拼接应该等于原文");
        
        // 打印结果用于调试
        System.out.println("原文长度: " + sentence.length());
        System.out.println("分块数量: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println("分块 " + i + " (长度:" + result.get(i).length() + "): " + result.get(i));
        }
    }

    @Test
    void testSplitLongSentence_ShortText() throws Exception {
        // 准备测试数据 - 短文本
        String sentence = "短文本测试";
        int chunkSize = 100;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果 - 短文本应该只有一个分块
        assertEquals(1, result.size(), "短文本应该只有一个分块");
        assertEquals(sentence, result.get(0), "短文本分块内容应该等于原文");
    }

    @Test
    void testSplitLongSentence_EmptyText() throws Exception {
        // 准备测试数据 - 空文本
        String sentence = "";
        int chunkSize = 100;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果 - 空文本应该返回空列表或包含一个空字符串
        assertTrue(result.isEmpty() || (result.size() == 1 && result.get(0).isEmpty()), 
            "空文本应该返回空列表或包含一个空字符串");
    }

    @Test
    void testSplitLongSentence_MixedLanguage() throws Exception {
        // 准备测试数据 - 中英文混合
        String sentence = "这是一个Chinese and English混合的text文本，用来测试mixed language处理能力。";
        int chunkSize = 25;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证拼接后等于原文
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed, "混合语言文本分割后重新拼接应该等于原文");
        
        System.out.println("混合语言测试 - 原文长度: " + sentence.length());
        System.out.println("分块数量: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println("分块 " + i + ": " + result.get(i));
        }
    }

    @Test
    void testSplitLongSentence_VerySmallChunkSize() throws Exception {
        // 准备测试数据 - 非常小的分块大小
        String sentence = "测试极小分块";
        int chunkSize = 3;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证拼接后等于原文
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed, "极小分块测试重新拼接应该等于原文");
    }

    @Test
    void testSplitLongSentence_LongText() throws Exception {
        // 准备测试数据 - 长文本
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            longText.append("这是一个很长的测试文本，用来验证HanLP分词在处理长文本时的性能和准确性。");
            longText.append("我们希望它能够智能地根据语义进行分割，而不是简单地按照字符数量进行切分。");
        }
        
        String sentence = longText.toString();
        int chunkSize = 50;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.size() > 1, "长文本应该被分割成多个块");
        
        // 验证拼接后等于原文
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed, "长文本分割后重新拼接应该等于原文");
        
        System.out.println("长文本测试 - 原文长度: " + sentence.length());
        System.out.println("分块数量: " + result.size());
    }
}