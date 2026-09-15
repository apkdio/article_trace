package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.RichTextCleaner;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Article;
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
        int pageTotal = (int) Math.ceil(totalArticles * 1.0 / pageSize);
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

    /**
     * 新增文章。封面随本次请求一起提交，不再单独上传。
     *
     * <p>用 multipart 而非 JSON：这样「正文 + 封面」是一次请求，不会出现封面传上去了、
     * 文章却没提交成功所留下的孤儿对象。</p>
     */
    @PostMapping("/add")
    public Result<String> addArticle(@RequestPart("article") @Validated Article article,
                                     @RequestPart(value = "cover", required = false) MultipartFile cover) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int uid = (int) userInfo.get("id");
        String username = userInfo.get("name").toString();
        String articleTitle = article.getTitle();
        String content = article.getTitle() + article.getContent();
        String cleanContent = RichTextCleaner.cleanToPlainText(content);
        List<AhoCorasickUtil.Match> matches = articleService.containsSensitive(cleanContent);
        if (!matches.isEmpty()) {
            error.put("content", "文章标题/内容包含违规词！");
            return Result.error(error);
        }
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
            Integer target = resolveTargetState(article.getState(), (int) userInfo.get("type"));
            if (target == null) {
                error.put("state", "非合理值！");
                return Result.error(error);
            }
            article.setState(target);
            try {
                if (articleService.articleAddOrUpdate(article, 0, cover, username)) {
                    return Result.success();
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

    @GetMapping("/detail/{id}")
    public Result<Article> detail(@PathVariable("id") int id) {
        Map<String, Object> error = new HashMap<>();
        if (articleService.findArticleById(id)) {
            Article article = articleService.findArticleByIdWithEntity(id);
            return Result.success(article);
        }
        error.put("error", "文章不存在!");
        return Result.error(error);
    }

    /** 更新文章。封面随本次请求一起提交；不传 cover 时按 {@code article.coverImg} 决定保留还是清空。 */
    @PatchMapping("/update/{id}")
    public Result<String> update(@PathVariable("id") int id,
                                 @RequestPart("article") @Validated Article article,
                                 @RequestPart(value = "cover", required = false) MultipartFile cover) {
        Map<String, Object> error = new HashMap<>();
        Article art = articleService.findArticleByIdWithEntity(id);
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
            List<AhoCorasickUtil.Match> matches = articleService.containsSensitive(cleanContent);
            if (!matches.isEmpty()) {
                error.put("content", "文章标题/内容包含违规词！");
                return Result.error(error);
            }
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
                Integer target = resolveTargetState(article.getState(), roleType);
                if (target == null) {
                    error.put("state", "非合理值！");
                    return Result.error(error);
                }
                if (!articleService.canTransfer(art.getState(), target, roleType)) {
                    error.put("state", "当前文章状态不允许该操作！");
                    return Result.error(error);
                }
                article.setState(target);
                try {
                    if (articleService.articleAddOrUpdate(article, 1, cover, username)) {
                        return Result.success();
                    }
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

    /**
     * 由「草稿 / 提交」意图 + 角色决定目标状态，不信任请求体里的具体数值。
     *
     * <p>前端仍沿用 state=0 表示「存为草稿」，1/2/3 表示「提交」：
     * 站长直接发布（1），其他人送审（2）。</p>
     *
     * @return 目标状态；取值不在 {0,1,2,3} 内时返回 {@code null}（由调用方拒绝）
     */
    private Integer resolveTargetState(Integer requested, int roleType) {
        if (requested == null) {
            return null;
        }
        if (requested == ArticleService.STATE_DRAFT) {
            return ArticleService.STATE_DRAFT;
        }
        // 只有合法的「非草稿」状态值才算提交意图，其余取值一律视为非法入参
        if (requested != ArticleService.STATE_PUBLISHED
                && requested != ArticleService.STATE_PENDING
                && requested != ArticleService.STATE_REJECTED) {
            return null;
        }
        return (roleType == ArticleService.ROLE_MASTER)
                ? ArticleService.STATE_PUBLISHED
                : ArticleService.STATE_PENDING;
    }

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
            int pageTotal = (int) Math.ceil(totalArticles * 1.0 / pageSize);
            if (pageTotal == 0) {
                error.put("error", "无数据！");
                return Result.error(error);
            }
            if (pageNum > pageTotal) {
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

    @PatchMapping("/assess")
    public Result<String> assessArticle(@RequestParam int id, @RequestParam int state) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        User master = userService.findUserByName(username);
        if (master != null && master.getType() == 0) {
            if (articleService.updateState(id, state)) {
                return Result.success();
            }
            error.put("error", "更新失败！");
            return Result.error(error);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }
}
