package com.yizhaoqi.smartpai.entity;

import lombok.Data;

@Data
public class SearchRequest {
    private String query; // 搜索关键词
    private int topK;     // 返回的前 K 个结果
}
