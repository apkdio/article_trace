package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Service.ProfileApplyService;
import com.articleTraceBack.Service.ReportService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审核中心的聚合计数：四种待办数量一次取走，供菜单角标与类型列表使用。
 * 只做汇总，不作状态变更；列表与处置仍走各自原有接口。
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** 文章状态：待审核 */
    private static final int ARTICLE_STATE_PENDING = 2;

    private final ArticleService articleService;
    private final AvatarApplyService avatarApplyService;
    private final AuthorApplyService authorApplyService;
    private final ReportService reportService;
    private final ProfileApplyService profileApplyService;

    public ReviewController(ArticleService articleService,
                            AvatarApplyService avatarApplyService,
                            AuthorApplyService authorApplyService,
                            ReportService reportService,
                            ProfileApplyService profileApplyService) {
        this.articleService = articleService;
        this.avatarApplyService = avatarApplyService;
        this.authorApplyService = authorApplyService;
        this.reportService = reportService;
        this.profileApplyService = profileApplyService;
    }

    /** 各类型待办数（站长）。固定返回 {@code article} / {@code avatar} / {@code authorApply} / {@code report} / {@code profile} 五个键。 */
    @GetMapping("/summary")
    public Result<Map<String, Integer>> summary() {
        if (!isMaster()) {
            return Result.error("权限不足！");
        }
        Map<String, Integer> data = new LinkedHashMap<>();
        // 站长的待办是「全站还有多少没处理」，所以文章这一项不按当前用户过滤（userId 传 null）
        data.put("article", articleService.findAllArticlesCountInMaster(
                null, ARTICLE_STATE_PENDING, null, null, null, null));
        data.put("avatar", avatarApplyService.pendingCount());
        data.put("authorApply", authorApplyService.pendingCount());
        data.put("report", reportService.pendingCount());
        data.put("profile", profileApplyService.pendingCount());
        return Result.success(data);
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
