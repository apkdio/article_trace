package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Service.Support.ArticlePublishPolicy;
import com.articleTraceBack.Service.Support.ArticleSensitivityGuard;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.exception.BusinessException;
import com.articleTraceBack.exception.ForbiddenException;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.RichTextCleaner;
import com.articleTraceBack.Utils.CurrentUser;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.ArticleSnapshot;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer timeOrder) {
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        timeOrder = normalizeTimeOrder(timeOrder);
        int uid = CurrentUser.require().id();
        int totalArticles = articleService.findAllArticlesWithConditions(uid, categoryId, state, search);
        int pageTotal = PageUtil.pageTotal(totalArticles, pageSize);
        if (pageTotal == 0) {
            // 没有数据不是错误：空页让前端渲染空状态，而不是弹「无数据」的红框
            return Result.success(new PageBean<>(0, List.of()));
        }
        if (pageNum > pageTotal) {
            pageNum = pageTotal;
        }
        PageBean<Article> allArticles = articleService.findAllArticlesWithPage(uid, pageNum,
                pageSize, categoryId, state, search, timeOrder);
        allArticles.setTotal(totalArticles);
        return Result.success(allArticles);
    }

    /** 新增文章；封面随本次请求以 multipart 一起提交，避免出现封面已上传、文章未保存的孤儿对象。 */
    @PostMapping("/add")
    public Result<Map<String, Object>> addArticle(@RequestPart("article") @Validated Article article,
                                                  @RequestPart(value = "cover", required = false) MultipartFile cover) {
        // 先认登录态：未登录时不该先跑封面上限这类业务校验，否则 401 会伪装成 200 + 字段错误
        CurrentUser.Session me = CurrentUser.require();
        ensureCoverSize(cover);
        int uid = me.id();
        String username = me.username();
        String articleTitle = article.getTitle();
        String content = article.getTitle() + article.getContent();
        String cleanContent = RichTextCleaner.cleanToPlainText(content);
        // 不拆箱：此前写成 int 接收，请求不带 categoryId 会直接 NPE 500
        Integer categoryId = article.getCategoryId();
        if (categoryId == null) {
            throw new BusinessException("categoryId", "文章类型不能为空！");
        }
        if (articleService.findArticleByUserAndTitle(uid, articleTitle) != null) {
            throw new BusinessException("title", "你已写过同名文章！");
        }
        if (categoryService.findById(categoryId) == null) {
            throw new BusinessException("categoryId", "文章类型不存在！");
        }
        article.setCreateUser(uid);
        // 新增不接受客户端 id（防止借 insertOrUpdate 覆盖他人文章）
        article.setId(null);
        // 目标状态由服务端按「草稿 / 提交」意图 + 角色决定，不信任请求体
        // 作者只能传待审状态，不可直接发布
        Integer target = ArticlePublishPolicy.resolveTargetState(article.getState(), me.type());
        if (target == null) {
            throw new BusinessException("state", "非合理值！");
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
        // 新增没有旧版本可比，快照传 null；版本从 0 开始
        articleService.articleAddOrUpdate(article, 0, cover, username, null);
        return Result.success(savedResult(article, me.type()));
    }

    /** 更新文章。封面随本次请求一起提交；不传 cover 时按 {@code article.coverImg} 决定保留还是清空。 */
    @PatchMapping("/update/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") int id,
                                              @RequestPart("article") @Validated Article article,
                                              @RequestPart(value = "cover", required = false) MultipartFile cover) {
        // 同上：先认登录态，未登录时不暴露「文章是否存在」
        CurrentUser.Session me = CurrentUser.require();
        ensureCoverSize(cover);
        Article art = articleService.findArticleMetaById(id);
        if (art == null) {
            throw new BusinessException("error", "文章不存在！");
        }
        int uid = me.id();
        int roleType = me.type();
        String username = me.username();
        String articleTitle = article.getTitle();
        String content = article.getTitle() + article.getContent();
        String cleanContent = RichTextCleaner.cleanToPlainText(content);
        // 同上：不能直接拆箱，缺失时要给明确提示而不是 500
        Integer categoryId = article.getCategoryId();
        if (categoryId == null) {
            throw new BusinessException("categoryId", "文章类型不能为空！");
        }
        article.setCreateUser(uid);
        article.setId(id);
        if (art.getCreateUser() != uid) {
            throw new BusinessException("error", "非法用户更新请求！");
        }
        Article byName = articleService.findArticleByUserAndTitle(uid, articleTitle);
        // 现在限定查找的范围为同作者的所有文章，意味着查重仅在同作者的全部文章范围内
        if (byName != null && (!Objects.equals(byName.getId(), article.getId()))) {
            throw new BusinessException("title", "你已经写过同名文章了！");
        }
        if (categoryService.findById(categoryId) == null) {
            throw new BusinessException("categoryId", "文章类型不存在！");
        }
        // 内容、归属、重名都校验通过后，最后判定状态流转是否合法
        // （放在末尾是为了不遮蔽原有的内容类报错）
        Integer target = ArticlePublishPolicy.resolveTargetState(article.getState(), roleType);
        if (target == null) {
            throw new BusinessException("state", "非合理值！");
        }
        // 草稿不扫（见 shouldCheckSensitive）；命中则流转目标固定为待审（站长也一样），再交给状态机判断
        List<AhoCorasickUtil.Match> matches = ArticleSensitivityGuard.shouldCheckSensitive(target)
                ? articleService.containsSensitive(cleanContent)
                : List.of();
        target = ArticleSensitivityGuard.applySensitiveVerdict(target, !matches.isEmpty());
        // 目标与当前一致时（例如已在待审的稿子再保存）不是流转，跳过状态机：否则会误报「不允许」
        if (!target.equals(art.getState())
                && !articleService.canTransfer(art.getState(), target, roleType)) {
            throw new BusinessException("state", "当前文章状态不允许该操作！");
        }
        article.setState(target);
        ArticleSensitivityGuard.applyMark(article, matches);
        // 快照取「进入接口时」的读数：保存与审核并发时，版本号 CAS 会让后到的一方落空。
        // 前端传了加载时的版本就以客户端为准，没传则回退到本次入口读数（兼容旧前端）
        Long expectedVersion = article.getVersion() != null ? article.getVersion() : art.getVersion();
        ArticleSnapshot snapshot = new ArticleSnapshot(art.getState(), art.getContent(),
                art.getCoverImg(), expectedVersion);
        articleService.articleAddOrUpdate(article, 1, cover, username, snapshot);
        return Result.success(savedResult(article, roleType));
    }

    /** 封面上限：multipart 的 max-file-size 要装得下正文 JSON（5MB 图片），封面改在应用层单独限 3MB */
    private static final long MAX_COVER_SIZE = 3L * 1024 * 1024;
    private static final String MAX_COVER_SIZE_TEXT = (MAX_COVER_SIZE / 1024 / 1024) + "MB";

    private void ensureCoverSize(MultipartFile cover) {
        if (cover != null && cover.getSize() > MAX_COVER_SIZE) {
            throw new BusinessException("cover", "封面图片不能超过 " + MAX_COVER_SIZE_TEXT + "！");
        }
    }

    /** 发布时间排序：1 升序 / -1 降序，其余值按默认顺序 */
    private Integer normalizeTimeOrder(Integer timeOrder) {
        return (timeOrder != null && (timeOrder == 1 || timeOrder == -1)) ? timeOrder : null;
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
        CurrentUser.Session me = CurrentUser.require();
        int uid = me.id();
        int type = me.type();
        Article art = articleService.findArticleByIdWithEntity(articleId);
        if (art == null) {
            throw new BusinessException("error", "未找到文章!");
        }
        if (art.getCreateUser() != uid) {
            if (type != 0) {
                throw new BusinessException("error", "非法用户删除请求！");
            }
            if (!Objects.equals(masterPass, masterPassword)) {
                throw new BusinessException("error", "站长密码错误！");
            }
        }
        articleService.deleteById(articleId);
        return Result.success();
    }


    /** 我的文章统计（总数 / 待审 / 已发布 / 已驳回） */
    @GetMapping("/count")
    public Result<Map<String, Object>> count() {
        int userId = CurrentUser.require().id();
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

    /** 版本探针：作者本人或站长可查，只回 version / state / updateTime，供编辑抽屉轮询冲突 */
    @GetMapping("/version/{id}")
    public Result<Map<String, Object>> articleVersion(@PathVariable int id) {
        CurrentUser.Session me = CurrentUser.require();
        Article meta = articleService.findArticleMetaById(id);
        if (meta == null) {
            throw new BusinessException("error", "文章不存在！");
        }
        if (me.type() != ArticleService.ROLE_MASTER && !Objects.equals(meta.getCreateUser(), me.id())) {
            throw new ForbiddenException();
        }
        return Result.success(versionOf(meta));
    }

    /** 版本探针（批量，站长）：审核列表轮询用，一次最多 50 篇 */
    @PostMapping("/versions")
    public Result<List<Map<String, Object>>> articleVersions(@RequestBody List<Integer> ids) {
        CurrentUser.Session me = CurrentUser.require();
        if (me.type() != ArticleService.ROLE_MASTER) {
            throw new ForbiddenException();
        }
        if (ids == null || ids.isEmpty()) {
            return Result.success(List.of());
        }
        if (ids.size() > 50) {
            throw new BusinessException("error", "一次最多查询 50 篇文章的版本！");
        }
        return Result.success(articleService.findArticleMetasByIds(ids).stream()
                .map(this::versionOf).toList());
    }

    private Map<String, Object> versionOf(Article article) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", article.getId());
        data.put("version", article.getVersion());
        data.put("state", article.getState());
        data.put("updateTime", article.getUpdateTime());
        return data;
    }

    /** 全站文章分页（站长，支持按标题或作者检索） */
    @GetMapping("/manageArticles")
    public Result<PageBean<Article>> manageArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer searchType,
            @RequestParam(required = false) Integer timeOrder) {
        String username = CurrentUser.require().username();
        User master = userService.findUserByName(username);
        if (master == null || master.getType() != 0) {
            throw new BusinessException("error", "权限不足！");
        }
        Integer userId = master.getId();
        int totalArticles = articleService.findAllArticlesCountInMaster(categoryId, state, userId, search, searchType, null);
        // 没有数据**不是错误**：返回空页让前端照常渲染空表即可。
        // 以前这里返回 code=1「无数据！」，审核中心就会为一个空列表弹红色错误框。
        int pageTotal = PageUtil.pageTotal(totalArticles, pageSize);
        if (pageTotal > 0 && pageNum > pageTotal) {
            pageNum = pageTotal;
        }
        PageBean<Article> allArticles = articleService.findAllArticlesInMaster(pageNum,
                pageSize, categoryId, state, userId, search, searchType, null, timeOrder);
        allArticles.setTotal(totalArticles);
        return Result.success(allArticles);
    }

    /** 审核文章：通过（1）或驳回（3）（站长）；驳回必须给理由，作者会看到 */
    @PatchMapping("/assess")
    public Result<String> assessArticle(@RequestParam int id, @RequestParam int state,
                                        @RequestParam(required = false) String rejectReason,
                                        @RequestParam(required = false) Long version) {
        String username = CurrentUser.require().username();
        User master = userService.findUserByName(username);
        if (master == null || master.getType() != 0) {
            throw new BusinessException("error", "权限不足！");
        }
        // 审核只做「通过 / 驳回」；不先拦住非法目标状态，会被 updateState 挡下后误报成「刚被修改」
        if (state != ArticleService.STATE_PUBLISHED && state != ArticleService.STATE_REJECTED) {
            throw new BusinessException("state", "只能通过（1）或驳回（3）！");
        }
        if (state == ArticleService.STATE_REJECTED
                && (rejectReason == null || rejectReason.isBlank())) {
            throw new BusinessException("error", "驳回理由不能为空！");
        }
        Article meta = articleService.findArticleMetaById(id);
        if (meta == null) {
            throw new BusinessException("error", "文章不存在！");
        }
        Long expectedVersion = version != null ? version : meta.getVersion();
        ArticleSnapshot snapshot = new ArticleSnapshot(meta.getState(), meta.getContent(),
                meta.getCoverImg(), expectedVersion);
        articleService.updateState(id, state, rejectReason, snapshot);
        return Result.success();
    }
}
