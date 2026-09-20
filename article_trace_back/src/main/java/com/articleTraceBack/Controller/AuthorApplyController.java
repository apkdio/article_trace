package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.config.SiteFeatureProperties;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.PageBean;
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

/**
 * 作者申请接口（统一前缀 {@code /applyAuthor}）。
 *
 * <p>按使用者分两段路径：用户侧直接挂在 {@code /applyAuthor} 下，站长侧统一走
 * {@code /applyAuthor/manage/**}。这样分开是为了让「站长专属」在 URL 层面就能识别——
 * 拦截器可以把 {@code /applyAuthor/manage} 加进 {@code notAllowUrl} 统一挡掉，
 * 而不必依赖每个方法各自记得写权限判断。</p>
 *
 * <p>{@link #isMaster()} 仍然保留：URL 规则只覆盖到前缀，纵深防御下多一道总是好的。</p>
 */
@RestController
@RequestMapping("/applyAuthor")
public class AuthorApplyController {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    private final AuthorApplyService applyService;
    private final SiteFeatureProperties siteFeatures;

    public AuthorApplyController(AuthorApplyService applyService, SiteFeatureProperties siteFeatures) {
        this.applyService = applyService;
        this.siteFeatures = siteFeatures;
    }

    /** 提交作者申请（body 可选：{@code {"reason": "..."}}） */
    @PostMapping()
    public Result<String> submit(@RequestBody(required = false) Map<String, String> body) {
        // 单用户态下关闭申请入口：注册关了不再有新读者，但**存量账号仍能走到这里**，
        // 不关就是留了个后门
        if (!siteFeatures.isAuthorApplyEnabled()) {
            return Result.error("暂不开放作者申请！");
        }
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        String reason = (body == null) ? null : body.get("reason");
        if (!applyService.submit(userId, reason)) {
            return Result.error("已有待审核的申请，请勿重复提交！");
        }
        return Result.success();
    }

    /** 我的最新申请状态（无申请时 data 为 null） */
    @GetMapping("/mine")
    public Result<AuthorApply> mine() {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        return Result.success(applyService.findMine(userId));
    }

    /** 申请列表（站长）；status 不传查全部 */
    @GetMapping("/manage/list")
    public Result<PageBean<AuthorApply>> list(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(applyService.list(status, pageNum, pageSize));
    }

    /** 待审数量（站长，用于前端角标） */
    @GetMapping("/manage/pendingCount")
    public Result<Integer> pendingCount() {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        return Result.success(applyService.pendingCount());
    }

    /** 审批（站长）：pass=true 通过并提升为作者，false 拒绝 */
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
        if (!applyService.review(id, pass, rejectReason, reviewerId)) {
            return Result.error("申请不存在或已处理！");
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
