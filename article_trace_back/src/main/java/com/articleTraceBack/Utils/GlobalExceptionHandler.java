package com.articleTraceBack.Utils;

import com.articleTraceBack.pojo.Result;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
// 全局异常处理器
public class GlobalExceptionHandler {
    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException
            (MethodArgumentNotValidException ex) {
        // 提取所有字段错误信息
        BindingResult bindingResult = ex.getBindingResult();
        Map<String, String> errors = new HashMap<>();
        bindingResult.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return Result.error(errors);
    }
    // @Validated 参数校验异常简化捕获
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Map<String, Object>> handleHandlerMethodValidation(
            HandlerMethodValidationException ex) {
        Map<String, Object> errors = new HashMap<>();
        // 提取信息
        ex.getParameterValidationResults().forEach(result -> {
            // 提取参数名
            String paramName = result.getMethodParameter().getParameterName();
            // 提取错误信息
            if (!result.getResolvableErrors().isEmpty()) {
                String message = result.getResolvableErrors().getFirst().getDefaultMessage();
                errors.put(paramName, message);
            }
        });
        return Result.error(errors);
    }

    // RequestParams 参数缺失异常
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Map<String, Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put(ex.getParameterName(), "参数缺失！");
        return Result.error(errors);
    }

    // 请求体格式异常
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Map<String, Object>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", "不支持的媒体类型");
        errors.put("contentType", ex.getContentType() != null ?
                ex.getContentType().toString() : "未知类型");
        errors.put("supportedTypes", ex.getSupportedMediaTypes()
                .stream()
                .map(MediaType::toString)
                .collect(Collectors.toList()));
        return Result.error(errors);
    }

    // 请求体内部内容校验（Json格式、数据类型、必须参数等）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Map<String, Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        Map<String, Object> errors = new HashMap<>();
        // 最简化错误信息
        errors.put("error", ex.getMessage().substring(0, ex.getMessage().indexOf(":")).trim());
        return Result.error(errors);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put(ex.getName(), "类型不匹配！需要"+ex.getRequiredType());
        return  Result.error(errors);
    }
}
