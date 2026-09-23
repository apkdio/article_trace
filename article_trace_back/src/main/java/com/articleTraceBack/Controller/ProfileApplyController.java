package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ProfileApplyService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.ProfileApply;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 昵称 / 个签审核接口（前缀 {@code /profile}）：提交入口在 {@code PATCH /user/update}，站长审批走 {@code /profile/manage/**}。 */
@RestController
@RequestMapping("/profile")
public class ProfileApplyController {

    /** reject_reason 列长度 */
    private static final int MAX_REASON_LENGTH = 200;

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    private final ProfileApplyService profileApplyService;

    public ProfileApplyController(ProfileApplyService profileApplyService) {
        this.profileApplyService = profileApplyService;
    }

    /** 审核列表（站长）；status 不传查全部 */
    @GetMapping("/manage/list")
    public Result<PageBean<ProfileApply>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(profileApplyService.list(status, pageNum, pageSize));
    }

    /** 待审数量（站长，用于审核中心角标与合计） */
    @GetMapping("/manage/pendingCount")
    public Result<Integer> pendingCount() {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(profileApplyService.pendingCount());
    }

    /** 审批（站长）：pass=true 通过、把待审值写回 user 并起 7 天锁定期；false 拒绝（需给理由） */
    @PatchMapping("/manage/review/{id}")
    public Result<String> review(@PathVariable int id,
                                 @RequestParam boolean pass,
                                 @RequestParam(required = false) String rejectReason) {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        Integer reviewerId = currentUserId();
        if (reviewerId == null) {
            return Result.error("未登录！");
        }
        if (!pass && (rejectReason == null || rejectReason.isBlank())) {
            return Result.error("拒绝时必须填写理由！");
        }
        if (rejectReason != null && rejectReason.length() > MAX_REASON_LENGTH) {
            return Result.error("拒绝理由过长（最多 " + MAX_REASON_LENGTH + " 字）！");
        }
        if (!profileApplyService.review(id, pass, rejectReason, reviewerId)) {
            return Result.error("记录不存在或已处理！");
        }
        return Result.success();
    }

    /** 从 ThreadLocal 取当前登录用户 id（TokenCheck 已校验登录） */
    private Integer currentUserId() {
        Object id = attribute("id");
        return id instanceof Integer value ? value : null;
    }

    private boolean isMaster() {
        Object type = attribute("type");
        return type instanceof Integer value && value == ROLE_MASTER;
    }

    private Object attribute(String key) {
        Map<String, Object> info = ThreadLocalUtil.get();
        return info == null ? null : info.get(key);
    }
}
