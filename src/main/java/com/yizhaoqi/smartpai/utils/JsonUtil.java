package com.yizhaoqi.smartpai.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hibernate.Hibernate;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/14
 */
public class JsonUtil {
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.findAndRegisterModules();
        SimpleModule module = new SimpleModule();
        mapper.registerModule(module);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串
     */
    public static String toStr(Object o) {
        try {
            // 在使用JPA时，Hibernate会创建代理对象来实现延迟加载等功能，这会导致获取到的对象是HibernateProxy代理对象
            // 为了避免这种代理对象的序列化异常，我们做一个代理对象转换成实体对象的动作
            if (o.getClass().getName().contains("HibernateProxy")) {
                o = Hibernate.unproxy(o);
            }
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> toList(String s, Class<T> clazz) {
        try {
            return mapper.readValue(s, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T toObj(String s, Class<T> clazz) {
        try {
            return mapper.readValue(s, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T toObj(String s, Type type) {
        try {
            return mapper.readValue(s, new TypeReference<>() {
                @Override
                public Type getType() {
                    return type;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T toObj(String s, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(s, typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
