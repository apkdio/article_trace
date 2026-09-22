package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ReportService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Report;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 举报接口（统一前缀 {@code /report}）：提交只需登录，列表与处置走 {@code /manage} 由拦截器统一拦截；{@link #isMaster()} 保留作纵深防御。 */
@RestController
@RequestMapping("/report")
public class ReportController {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** report.reason 列长度 */
    private static final int MAX_REASON_LENGTH = 200;

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 提交举报（登录即可）；举报人取自登录态，不接受前端传入 */
    @PostMapping
    public Result<String> submit(@RequestBody Report report) {
        Integer reporterId = currentUserId();
        if (reporterId == null) {
            return Result.error("未登录！");
        }
        if (report.getTargetType() == null || report.getTargetId() == null) {
            return Result.error("举报对象不能为空！");
        }
        String reason = report.getReason() == null ? "" : report.getReason().trim();
        if (reason.isEmpty()) {
            return Result.error("请填写举报理由！");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return Result.error("举报理由过长（最多 " + MAX_REASON_LENGTH + " 字）！");
        }
        if (!reportService.submit(reporterId, report.getTargetType(), report.getTargetId(), reason)) {
            return Result.error("举报失败：对象不存在、不能举报自己，或你已经举报过了。");
        }
        return Result.success();
    }

    /** 举报列表（站长）；status 不传查全部 */
    @GetMapping("/manage/list")
    public Result<PageBean<Report>> list(@RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(reportService.list(status, pageNum, pageSize));
    }

    /** 待处理数量（站长，用于前端角标） */
    @GetMapping("/manage/pendingCount")
    public Result<Integer> pendingCount() {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(reportService.pendingCount());
    }

    /** 处置（站长）：handled=true 认定违规，false 驳回举报。只改举报记录，不动被举报的内容 */
    @PatchMapping("/manage/handle/{id}")
    public Result<String> handle(@PathVariable int id, @RequestParam boolean handled) {
        Integer handlerId = currentUserId();
        if (handlerId == null) {
            return Result.error("未登录！");
        }
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        if (!reportService.handle(id, handled, handlerId)) {
            return Result.error("记录不存在或已处理！");
        }
        return Result.success();
    }

    /** 从 ThreadLocal 取当前登录用户 id（TokenCheck 已校验登录） */
    private Integer currentUserId() {
        Object info = ThreadLocalUtil.get();
        if (info instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id instanceof Integer value) {
                return value;
            }
            if (id != null) {
                try {
                    return Integer.valueOf(id.toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 当前登录用户是否为站长 */
    private boolean isMaster() {
        Object info = ThreadLocalUtil.get();
        if (info instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type == null) {
                return false;
            }
            try {
                return Integer.parseInt(type.toString()) == ROLE_MASTER;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }
}
