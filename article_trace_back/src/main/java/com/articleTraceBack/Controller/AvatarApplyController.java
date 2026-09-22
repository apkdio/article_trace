package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 头像审核接口（统一前缀 {@code /avatar}）：用户侧查自己的待审状态，站长侧审批走 {@code /avatar/manage/**} 由拦截器统一拦截；提交入口在 {@code PATCH /user/updateUserLogo}。 */
@RestController
@RequestMapping("/avatar")
public class AvatarApplyController {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** reject_reason 列长度 */
    private static final int MAX_REASON_LENGTH = 200;

    private final AvatarApplyService avatarApplyService;

    public AvatarApplyController(AvatarApplyService avatarApplyService) {
        this.avatarApplyService = avatarApplyService;
    }

    /** 我的最新提交记录（无提交时 data 为 null） */
    @GetMapping("/mine")
    public Result<AvatarApply> mine() {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        return Result.success(avatarApplyService.findMine(userId));
    }

    /** 审核列表（站长）；status 不传查全部 */
    @GetMapping("/manage/list")
    public Result<PageBean<AvatarApply>> list(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(avatarApplyService.list(status, pageNum, pageSize));
    }

    /** 待审数量（站长，用于前端角标） */
    @GetMapping("/manage/pendingCount")
    public Result<Integer> pendingCount() {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(avatarApplyService.pendingCount());
    }

    /** 审批（站长）：pass=true 通过并把待审头像设为用户头像，false 拒绝（需给理由） */
    @PatchMapping("/manage/review/{id}")
    public Result<String> review(@PathVariable int id,
                                 @RequestParam boolean pass,
                                 @RequestParam(required = false) String rejectReason) {
        Integer reviewerId = currentUserId();
        if (reviewerId == null) {
            return Result.error("未登录！");
        }
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        if (!pass && (rejectReason == null || rejectReason.isBlank())) {
            return Result.error("拒绝时必须填写理由！");
        }
        if (rejectReason != null && rejectReason.length() > MAX_REASON_LENGTH) {
            return Result.error("拒绝理由过长（最多 " + MAX_REASON_LENGTH + " 字）！");
        }
        if (!avatarApplyService.review(id, pass, rejectReason, reviewerId)) {
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
