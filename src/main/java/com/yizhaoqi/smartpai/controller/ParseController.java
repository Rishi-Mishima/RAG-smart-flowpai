package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.utils.LogUtils;
import io.jsonwebtoken.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

//POST /api/v1/parse
//👉 本质就是一个 文件解析接口（API）


@RestController
@RequestMapping("/api/v1/parse")

//@RestController → 返回 JSON / 字符串（不是页面）
//@RequestMapping → 给整个类加路径前缀

public class ParseController {

    /**
     依赖注入 Service 层
     👉 Controller 不做业务逻辑
     👉 只负责调用 Service
     **/
    @Autowired
    private ParseService parseService;



    //@PostMapping → 处理 POST 请求
    //返回类型：ResponseEntity<String>
    @PostMapping
    public ResponseEntity<String> parseDocument(@RequestParam("file") MultipartFile file,
                                                @RequestParam("file_md5") String fileMd5,
                                                @RequestAttribute(value = "userId", required = false) String userId) {

        //1.开始性能监控
            //👉 用来记录接口耗时 - 计时器
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PARSE_DOCUMENT");
        try {
            //2.记录日志业务: userId, 文件名,文件大小,md5
            LogUtils.logBusiness("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "开始解析文档: fileMd5=%s, fileName=%s, fileSize=%d", 
                    fileMd5, file.getOriginalFilename(), file.getSize());

            //3. 💥 核心 - RAG 入口
            parseService.parseAndSave(fileMd5, file.getInputStream());

            // 4. 成功日志
                //文件操作（PARSE）
                //用户操作（行为审计）
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "SUCCESS");
            LogUtils.logUserOperation(userId != null ? userId : "system", "PARSE_DOCUMENT", 
                    fileMd5, "SUCCESS");
            //5.结束性能统计
            monitor.end("文档解析成功");

            //6.返回成功
            return ResponseEntity.ok("文档解析成功");
        } catch (Exception e) {
            // 异常处理

            //1.打印错误日志
            LogUtils.logBusinessError("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "文档解析失败: fileMd5=%s, fileName=%s", e, fileMd5, file.getOriginalFilename());
            // 2.记录错误状态
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "FAILED");
            // 3.结束监控
            monitor.end("文档解析失败: " + e.getMessage());

            //4.返回错误
            return ResponseEntity.badRequest().body("文档解析失败：" + e.getMessage());
        }
    }
}

