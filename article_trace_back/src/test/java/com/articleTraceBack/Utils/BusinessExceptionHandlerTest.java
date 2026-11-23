package com.articleTraceBack.Utils;

import com.articleTraceBack.pojo.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 业务异常到返回体的唯一出口：字段名决定 key，缺省落 error。 */
public class BusinessExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    public void fieldIsUsedAsKey() {
        Result<Map<String, Object>> result =
                handler.handleBusiness(new BusinessException("title", "你已写过同名文章！"));
        assertEquals(1, result.getCode());
        assertEquals(Map.of("title", "你已写过同名文章！"), result.getMessage());
        assertNull(result.getData());
    }

    @Test
    public void missingFieldFallsBackToError() {
        Result<Map<String, Object>> result = handler.handleBusiness(new BusinessException("删除失败"));
        assertEquals(Map.of("error", "删除失败"), result.getMessage());
    }

    @Test
    public void bodyImageLimitKeepsContentField() {
        Result<Map<String, Object>> result =
                handler.handleBusiness(new BodyImageLimitException("正文最多 15 张图片"));
        assertEquals(Map.of("content", "正文最多 15 张图片"), result.getMessage());
    }
}
