package com.articleTraceBack.Utils;

import com.articleTraceBack.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
@Slf4j
// 全局异常处理器
public class GlobalExceptionHandler {
    /** 参数校验失败（@Validated）：取第一条字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException
            (MethodArgumentNotValidException ex) {
        // 提取所有字段错误信息
        BindingResult bindingResult = ex.getBindingResult();
        Map<String, String> errors = new HashMap<>();
        bindingResult.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return Result.error(errors);
    }
    /** 方法级参数校验失败（@Validated 简化捕获） */
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

    /** 缺少必填请求参数（RequestParam 缺失） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Map<String, Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put(ex.getParameterName(), "参数缺失！");
        return Result.error(errors);
    }

    /** 请求 Content-Type 不支持或请求体格式异常 */
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

    /** 请求体无法解析（JSON 格式 / 数据类型 / 必填项校验失败） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Map<String, Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        Map<String, Object> errors = new HashMap<>();
        // 原始 message 形如「JSON parse error: Cannot deserialize value of type `int` from String "abc"」，
        // 真正有用的信息在冒号「之后」。此前只截取冒号前那段，等于把所有格式错误都说成同一句
        // 「JSON parse error」——既没告诉调用方哪里错了，还在 message 不含冒号时 substring(0,-1) 抛越界。
        Throwable root = ex.getMostSpecificCause();
        String detail = root != null ? root.getMessage() : ex.getMessage();
        errors.put("error", detail == null || detail.isBlank() ? "请求体格式错误" : detail);
        return Result.error(errors);
    }

    /** 唯一键冲突（并发写入撞唯一索引时由数据库兜底） */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Map<String, Object>> handleDuplicateKeyException(DuplicateKeyException ex) {
        Map<String, Object> errors = new HashMap<>();
        // 不谎称是某一具体字段冲突：异常里只有约束名，无法确定调用方该改哪个字段。
        // 各写入点若需要更精确的提示，应自行 catch 转换。
        log.warn("duplicate key: {}", ex.getMostSpecificCause().getMessage());
        errors.put("error", "提交的数据与已有记录冲突，请检查是否有重复项");
        return Result.error(errors);
    }

    /** 其他数据完整性异常（外键不存在、字段超长、非空约束等） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> errors = new HashMap<>();
        log.warn("data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        errors.put("error", "提交的数据不符合约束（可能引用了不存在的记录或字段过长）");
        return Result.error(errors);
    }

    /** 兜底：未预期异常统一返回，避免把堆栈与内部细节抛给调用方 */
    @ExceptionHandler(Exception.class)
    public Result<Map<String, Object>> handleUnexpectedException(Exception ex) {
        Map<String, Object> errors = new HashMap<>();
        log.error("unhandled exception", ex);
        errors.put("error", "服务器处理请求时出错，请稍后重试");
        return Result.error(errors);
    }
    /** 参数类型不匹配（如路径变量非数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put(ex.getName(), "类型不匹配！需要"+ex.getRequiredType());
        return  Result.error(errors);
    }
}
