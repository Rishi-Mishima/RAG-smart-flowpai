package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ParseService 的单元测试类 (不依赖Spring Context)
 * 专门测试 splitLongSentence 方法的功能
 */
class ParseServiceUnitTest {

    private ParseService parseService;

    @BeforeEach
    void setUp() {
        parseService = new ParseService();
        // 设置配置值
        ReflectionTestUtils.setField(parseService, "chunkSize", 1000);
        ReflectionTestUtils.setField(parseService, "bufferSize", 8192);
        ReflectionTestUtils.setField(parseService, "maxMemoryThreshold", 0.8);
    }

    @Test
    void testSplitLongSentence_BasicFunctionality() throws Exception {
        // 测试基本功能
        String sentence = "这是一个测试句子，用来验证分词效果。";
        int chunkSize = 15;

        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证拼接后等于原文
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed);
        
        System.out.println("=== 基本功能测试 ===");
        System.out.println("原文: " + sentence + " (长度: " + sentence.length() + ")");
        System.out.println("分块数量: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println("分块 " + i + ": " + result.get(i) + " (长度: " + result.get(i).length() + ")");
        }
    }

    @Test
    void testSplitLongSentence_EdgeCases() throws Exception {
        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);

        // 测试空字符串
        @SuppressWarnings("unchecked")
        List<String> emptyResult = (List<String>) method.invoke(parseService, "", 100);
        assertTrue(emptyResult.isEmpty() || (emptyResult.size() == 1 && emptyResult.get(0).isEmpty()));

        // 测试单个字符
        @SuppressWarnings("unchecked")
        List<String> singleCharResult = (List<String>) method.invoke(parseService, "测", 10);
        assertEquals(1, singleCharResult.size());
        assertEquals("测", singleCharResult.get(0));

        // 测试很长的文本
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longText.append("这是第").append(i).append("段文本。");
        }
        
        @SuppressWarnings("unchecked")
        List<String> longResult = (List<String>) method.invoke(parseService, longText.toString(), 30);
        assertTrue(longResult.size() > 1);
        
        // 验证拼接
        String reconstructed = String.join("", longResult);
        assertEquals(longText.toString(), reconstructed);

        System.out.println("=== 边界情况测试 ===");
        System.out.println("长文本分块数量: " + longResult.size());
    }

    @Test
    void testSplitLongSentence_ChunkSizeValidation() throws Exception {
        String sentence = "这是用来测试分块大小限制的句子，包含标点符号和数字123。";
        
        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);

        // 测试不同的分块大小
        int[] chunkSizes = {5, 10, 20, 50};
        
        for (int chunkSize : chunkSizes) {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);
            
            // 验证每个分块（除了最后一个）都不超过限制
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).length() <= chunkSize, 
                    "分块大小 " + chunkSize + " 时，分块 " + i + " 长度超限: " + result.get(i).length());
            }
            
            // 验证拼接结果
            String reconstructed = String.join("", result);
            assertEquals(sentence, reconstructed, "分块大小 " + chunkSize + " 时拼接结果不匹配");
            
            System.out.println("分块大小 " + chunkSize + " -> 分块数量: " + result.size());
        }
    }

    @Test
    void testSplitLongSentence_Performance() throws Exception {
        // 性能测试
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeText.append("这是一个用于性能测试的长句子，包含各种中文字符和标点符号。");
        }
        
        String sentence = largeText.toString();
        int chunkSize = 100;
        
        Method method = ParseService.class.getDeclaredMethod("splitLongSentence", String.class, int.class);
        method.setAccessible(true);

        long startTime = System.currentTimeMillis();
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(parseService, sentence, chunkSize);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertNotNull(result);
        assertTrue(result.size() > 1);
        
        // 验证拼接结果
        String reconstructed = String.join("", result);
        assertEquals(sentence, reconstructed);

        System.out.println("=== 性能测试 ===");
        System.out.println("原文长度: " + sentence.length());
        System.out.println("分块数量: " + result.size());
        System.out.println("处理时间: " + duration + "ms");
        
        // 性能断言：处理时间应该在合理范围内
        assertTrue(duration < 5000, "处理时间过长: " + duration + "ms");
    }
}