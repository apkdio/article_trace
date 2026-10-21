package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Service.Support.ArticlePublishPolicy;
import com.articleTraceBack.Service.Support.ArticleSensitivityGuard;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.ArticleConcurrentEditException;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.RichTextCleaner;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.ArticleSnapshot;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
import org.springframework.dao.DuplicateKeyException;
import com.articleTraceBack.pojo.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/article")

public class ArticleController {
    private final CategoryService categoryService;
    private final ArticleService articleService;
    private final UserService userService;
    @Value("${Password.masterPass}")
    private String masterPassword;


    public ArticleController(CategoryService categoryService, ArticleService articleService, UserService userService) {
        this.categoryService = categoryService;
        this.articleService = articleService;
        this.userService = userService;
    }

    /** 我的文章分页（可按分类 / 状态 / 关键词筛选） */
    @GetMapping("/list")
    public Result<PageBean<Article>> articlesList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) String search) {
        Map<String, Object> error = new HashMap<>();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int uid = (int) userInfo.get("id");
        int totalArticles = articleService.findAllArticlesWithConditions(uid, categoryId, state, search);
        int pageTotal = PageUtil.pageTotal(totalArticles, pageSize);
        if (pageTotal == 0) {
            error.put("error", "无数据！");
            return Result.error(error);
        }
        if (pageNum > pageTotal) {
            pageNum = pageTotal;
        }
        PageBean<Article> allArticles = articleService.findAllArticlesWithPage(uid, pageNum,
                pageSize, categoryId, state, search);
        allArticles.setTotal(totalArticles);
        return Result.success(allArticles);
    }

    /** 新增文章；封面随本次请求以 multipart 一起提交，避免出现封面已上传、文章未保存的孤儿对象。 */
    @PostMapping("/add")
    public Result<Map<String, Object>> addArticle(@RequestPart("article") @Validated Article article,
                                                  @RequestPart(value = "cover", required = false) MultipartFile cover) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int uid = (int) userInfo.get("id");
        String username = userInfo.get("name").toString();
        String articleTitle = article.getTitle();
        String content = article.getTitle() + article.getContent();
        String cleanContent = RichTextCleaner.cleanToPlainText(content);
        // 不拆箱：此前写成 int 接收，请求不带 categoryId 会直接 NPE 500
        Integer categoryId = article.getCategoryId();
        if (categoryId == null) {
            error.put("categoryId", "文章类型不能为空！");
            return Result.error(error);
        }
        if (articleService.findArticleByUserAndTitle(uid, articleTitle) != null) {
            error.put("title", "你已写过同名文章！");
            return Result.error(error);
        }
        if (categoryService.findById(categoryId) != null) {
            article.setCreateUser(uid);
            // 新增不接受客户端 id（防止借 insertOrUpdate 覆盖他人文章）
            article.setId(null);
            // 目标状态由服务端按「草稿 / 提交」意图 + 角色决定，不信任请求体
            // 作者只能传待审状态，不可直接发布
            Integer target = ArticlePublishPolicy.resolveTargetState(article.getState(), (int) userInfo.get("type"));
            if (target == null) {
                error.put("state", "非合理值！");
                return Result.error(error);
            }
            // 草稿不扫（见 shouldCheckSensitive）；送审 / 发布命中则一律转待审，站长也不例外——
            // 站长是唯一能改词库的人，只有让命中结果落进他能看见的审核队列，误伤才能反馈回词表。
            // 先看这篇文章是不是草稿，草稿不做审核
            List<AhoCorasickUtil.Match> matches = ArticleSensitivityGuard.shouldCheckSensitive(target)
                    ? articleService.containsSensitive(cleanContent)
                    : List.of();
            // 所有角色均需要通过违规检测
            target = ArticleSensitivityGuard.applySensitiveVerdict(target, !matches.isEmpty());
            article.setState(target);
            ArticleSensitivityGuard.applyMark(article, matches);
            try {
                // 新增没有旧版本可比，快照传 null；版本从 0 开始
                if (articleService.articleAddOrUpdate(article, 0, cover, username, null)) {
                    return Result.success(savedResult(article, (int) userInfo.get("type")));
                }
            } catch (DuplicateKeyException e) {
                // 并发下两个请求可能同时通过查重，由唯一索引 uk_user_title 兜底
                error.put("title", "你已写过同名文章！");
                return Result.error(error);
            }
            error.put("error", "新增失败!");
            return Result.error(error);
        }
        error.put("categoryId", "文章类型不存在！");
        return Result.error(error);
    }

    /** 更新文章。封面随本次请求一起提交；不传 cover 时按 {@code article.coverImg} 决定保留还是清空。 */
    @PatchMapping("/update/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") int id,
                                              @RequestPart("article") @Validated Article article,
                                              @RequestPart(value = "cover", required = false) MultipartFile cover) {
        Map<String, Object> error = new HashMap<>();
        Article art = articleService.findArticleMetaById(id);
        if (art == null) {
            error.put("error", "文章不存在！");
            return Result.error(error);
        }
        {
            Map<String, Object> userInfo = ThreadLocalUtil.get();
            int uid = (int) userInfo.get("id");
            int roleType = (int) userInfo.get("type");
            String username = userInfo.get("name").toString();
            String articleTitle = article.getTitle();
            String content = article.getTitle() + article.getContent();
            String cleanContent = RichTextCleaner.cleanToPlainText(content);
            // 同上：不能直接拆箱，缺失时要给明确提示而不是 500
            Integer categoryId = article.getCategoryId();
            if (categoryId == null) {
                error.put("categoryId", "文章类型不能为空！");
                return Result.error(error);
            }
            article.setCreateUser(uid);
            article.setId(id);
            if (art.getCreateUser() != uid) {
                error.put("error", "非法用户更新请求！");
                return Result.error(error);
            }
            Article byName = articleService.findArticleByUserAndTitle(uid, articleTitle);
            // 现在限定查找的范围为同作者的所有文章，意味着查重仅在同作者的全部文章范围内
            if (byName != null && (!Objects.equals(byName.getId(), article.getId()))) {
                error.put("title", "你已经写过同名文章了！");
                return Result.error(error);
            }
            if (categoryService.findById(categoryId) != null) {
                // 内容、归属、重名都校验通过后，最后判定状态流转是否合法
                // （放在末尾是为了不遮蔽原有的内容类报错）
                Integer target = ArticlePublishPolicy.resolveTargetState(article.getState(), roleType);
                if (target == null) {
                    error.put("state", "非合理值！");
                    return Result.error(error);
                }
                // 草稿不扫（见 shouldCheckSensitive）；命中则流转目标固定为待审（站长也一样），再交给状态机判断
                List<AhoCorasickUtil.Match> matches = ArticleSensitivityGuard.shouldCheckSensitive(target)
                        ? articleService.containsSensitive(cleanContent)
                        : List.of();
                target = ArticleSensitivityGuard.applySensitiveVerdict(target, !matches.isEmpty());
                // 目标与当前一致时（例如已在待审的稿子再保存）不是流转，跳过状态机：否则会误报「不允许」
                if (!target.equals(art.getState())
                        && !articleService.canTransfer(art.getState(), target, roleType)) {
                    error.put("state", "当前文章状态不允许该操作！");
                    return Result.error(error);
                }
                article.setState(target);
                ArticleSensitivityGuard.applyMark(article, matches);
                try {
                    // 快照取「进入接口时」的读数：保存与审核并发时，版本号 CAS 会让后到的一方落空。
                    // 前端传了加载时的版本就以客户端为准，没传则回退到本次入口读数（兼容旧前端）
                    Long expectedVersion = article.getVersion() != null ? article.getVersion() : art.getVersion();
                    ArticleSnapshot snapshot = new ArticleSnapshot(art.getState(), art.getContent(),
                            art.getCoverImg(), expectedVersion);
                    if (articleService.articleAddOrUpdate(article, 1, cover, username, snapshot)) {
                        return Result.success(savedResult(article, roleType));
                    }
                } catch (ArticleConcurrentEditException e) {
                    error.put("error", e.getMessage());
                    return Result.error(error);
                } catch (DuplicateKeyException e) {
                    error.put("title", "你已写过同名文章！");
                    return Result.error(error);
                }
                error.put("error", "更新失败!");
                return Result.error(error);
            }
            error.put("categoryId", "文章类型不存在！");
            return Result.error(error);
        }
    }

    /** 保存成功后的回执：告知前端落到了哪个状态；命中词只回给站长，避免作者拿词表逐条试探。 */
    private Map<String, Object> savedResult(Article article, int roleType) {
        boolean hit = article.getSensitiveHit() != null && article.getSensitiveHit() == 1;
        Map<String, Object> data = new HashMap<>();
        data.put("state", article.getState());
        data.put("sensitiveHit", hit);
        if (hit && roleType == ArticleService.ROLE_MASTER) {
            data.put("sensitiveWords", article.getSensitiveWords());
        }
        return data;
    }

    /** 删除文章：作者本人直删，他人需站长身份 + 站长密码 */
    @DeleteMapping("/delete")
    public Result<String> deleteArticle(@RequestParam int articleId, String masterPass) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int uid = (int) userInfo.get("id");
        int type = (int) userInfo.get("type");
        Map<String, Object> error = new HashMap<>();
        Article art = articleService.findArticleByIdWithEntity(articleId);
        if (art != null) {
            if (art.getCreateUser() != uid) {
                if (type != 0) {
                    error.put("error", "非法用户删除请求！");
                    return Result.error(error);
                }
                if (!Objects.equals(masterPass, masterPassword)) {
                    error.put("error", "站长密码错误！");
                    return Result.error(error);
                }
            }
            if (articleService.deleteById(articleId)) {
                return Result.success();
            }
            error.put("error", "删除失败！");
            return Result.error(error);
        }
        error.put("error", "未找到文章!");
        return Result.error(error);
    }


    /** 我的文章统计（总数 / 待审 / 已发布 / 已驳回） */
    @GetMapping("/count")
    public Result<Map<String, Object>> count() {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int userId = (int) userInfo.get("id");
        int totalArticles = articleService.findAllArticlesCountInMaster(null, null, userId, null, null, null);
        int waitAccessTotal = articleService.findAllArticlesCountInMaster(null, 2, userId, null, null, null);
        int accessTotal = articleService.findAllArticlesCountInMaster(null, 1, userId, null, null, null);
        int rejectTotal = articleService.findAllArticlesCountInMaster(null, 3, userId, null, null, null);
        Map<String, Object> count = new HashMap<>();
        count.put("totalArticles", totalArticles);
        count.put("waitAccessTotal", waitAccessTotal);
        count.put("accessTotal", accessTotal);
        count.put("rejectTotal", rejectTotal);
        return Result.success(count);
    }

    /** 全站文章分页（站长，支持按标题或作者检索） */
    @GetMapping("/manageArticles")
    public Result<PageBean<Article>> manageArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer searchType) {
        Map<String, Object> error = new HashMap<>();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        if (searchType != null && search == null) {
            searchType = null;
        }
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        User master = userService.findUserByName(username);
        if (master != null && master.getType() == 0) {
            Integer userId = master.getId();
            int totalArticles = articleService.findAllArticlesCountInMaster(categoryId, state, userId, search, searchType, null);
            // 没有数据**不是错误**：返回空页让前端照常渲染空表即可。
            // 以前这里返回 code=1「无数据！」，审核中心就会为一个空列表弹红色错误框。
            int pageTotal = PageUtil.pageTotal(totalArticles, pageSize);
            if (pageTotal > 0 && pageNum > pageTotal) {
                pageNum = pageTotal;
            }
            PageBean<Article> allArticles = articleService.findAllArticlesInMaster(pageNum,
                    pageSize, categoryId, state, userId, search, searchType, null);
            allArticles.setTotal(totalArticles);
            return Result.success(allArticles);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }

    /** 审核文章：通过（1）或驳回（3）（站长）；驳回必须给理由，作者会看到 */
    @PatchMapping("/assess")
    public Result<String> assessArticle(@RequestParam int id, @RequestParam int state,
                                        @RequestParam(required = false) String rejectReason,
                                        @RequestParam(required = false) Long version) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        User master = userService.findUserByName(username);
        if (master != null && master.getType() == 0) {
            if (state == ArticleService.STATE_REJECTED
                    && (rejectReason == null || rejectReason.isBlank())) {
                error.put("error", "驳回理由不能为空！");
                return Result.error(error);
            }
            Article meta = articleService.findArticleMetaById(id);
            if (meta == null) {
                error.put("error", "文章不存在！");
                return Result.error(error);
            }
            Long expectedVersion = version != null ? version : meta.getVersion();
            ArticleSnapshot snapshot = new ArticleSnapshot(meta.getState(), meta.getContent(),
                    meta.getCoverImg(), expectedVersion);
            if (articleService.updateState(id, state, rejectReason, snapshot)) {
                return Result.success();
            }
            // 版本落空与前置换状态不合法走同一个出口：对站长而言都该刷新列表再看
            error.put("error", "文章可能刚被修改，请刷新后重试！");
            return Result.error(error);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }
}
