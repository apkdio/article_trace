package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Service.ReportService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审核中心的聚合计数（T19 第二期第一刀）。
 *
 * <p>只做汇总：四种待办的数量从这里一次取走，给菜单角标和左侧类型列表用。
 * **列表与处置仍走各自原有的接口**（`/article/manageArticles`、`/avatar/manage/list`、
 * `/applyAuthor/manage/list`、`/report/manage/list`）——各类型的数据模型天然不同，
 * 不新建统一审核表，这里也不做任何状态变更。</p>
 *
 * <p>⚠️ 待办：`/review` 还没加进 `spring.tokenCheck.notAllowUrl`（四份配置），
 * 目前靠下面的 {@link #isMaster()} 保护。前端页面落地时一并补上。</p>
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

    public ReviewController(ArticleService articleService,
                            AvatarApplyService avatarApplyService,
                            AuthorApplyService authorApplyService,
                            ReportService reportService) {
        this.articleService = articleService;
        this.avatarApplyService = avatarApplyService;
        this.authorApplyService = authorApplyService;
        this.reportService = reportService;
    }

    /**
     * 各类型待办数（站长）。
     *
     * <p>返回固定四个键：{@code article} / {@code avatar} / {@code authorApply} / {@code report}，
     * 前端直接按类型取用，不必判断某个键存不存在。</p>
     */
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
