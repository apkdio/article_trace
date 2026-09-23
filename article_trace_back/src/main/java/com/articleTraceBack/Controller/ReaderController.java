package com.articleTraceBack.Controller;

import com.articleTraceBack.config.SiteFeatureProperties;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.ReaderService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/reader")
public class ReaderController {
    private final ArticleService articleService;
    private final ReaderService readerService;
    private final UserService userService;
    private final SiteFeatureProperties siteFeatures;
    @Value("${spring.application.admin.defaultUser}")
    private String masterUserName;

    public ReaderController(ArticleService articleService, ReaderService readerService, UserService userService,
                            SiteFeatureProperties siteFeatures) {
        this.articleService = articleService;
        this.readerService = readerService;
        this.userService = userService;
        this.siteFeatures = siteFeatures;
    }

    /** 公开文章分页（分类 / 关键词 / 作者筛选，仅已发布） */
    @GetMapping("/getArticles")
    public Result<PageBean<Article>> getArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer searchType,
            @RequestParam(required = false) String nickName) {
        Map<String, Object> error = new HashMap<>();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        if (searchType != null && search == null) {
            searchType = null;
        }
        int totalArticles = articleService.findAllArticlesCountInPublic(categoryId, 1, search, searchType, nickName);
        int pageTotal = (int) Math.ceil(totalArticles * 1.0 / pageSize);
        if (pageTotal == 0) {
            error.put("error", "无数据！");
            return Result.error(error);
        }
        if (pageNum > pageTotal) {
            pageNum = pageTotal;
        }
        PageBean<Article> allArticles = articleService.findAllArticlesInPublic(pageNum,
                pageSize, categoryId, 1, search, searchType, nickName);
        allArticles.setTotal(totalArticles);
        return Result.success(allArticles);
    }

    /** 公开文章详情（未发布不可见） */
    @GetMapping("/article/{id}")
    public Result<Article> getArticleById(@PathVariable int id) {
        Map<String, Object> error = new HashMap<>();
        if (articleService.findArticleById(id)) {
            Article article = articleService.findArticleByIdWithEntity(id);
            if (article.getState() != 1) {
                error.put("error", "文章未发布！");
                return Result.error(error);
            }
            article.setViews(article.getViews() != null ? article.getViews() : 0L);
            return Result.success(article);
        }
        error.put("error", "文章不存在!");
        return Result.error(error);
    }

    /** 作者信息（头像、发文数等） */
    @GetMapping("/writerInfo")
    public Result<WriterInfo> getWriterInfo(@RequestParam String nickName) {
        Map<String, Object> error = new HashMap<>();
        WriterInfo writer = new WriterInfo();
        User user = readerService.findUserByNickName(nickName);
        if (user == null) {
            error.put("error", "用户不存在！");
            return Result.error(error);
        }
        int publishCount = readerService.findPublishCounts(user.getId());
        writer.setId(user.getId());
        writer.setType(user.getType());
        writer.setWriterPicSrc(user.getUserPicSrc());
        writer.setWriterPicThumbSrc(user.getUserPicThumbSrc());
        writer.setPublishCount(publishCount);
        writer.setNickName(user.getNickname());
        writer.setSignature(user.getSignature());
        writer.setEmail(user.getEmail());
        writer.setUsername(user.getUsername());
        return Result.success(writer);
    }

    /** 站长信息 */
    @GetMapping("/masterInfo")
    public Result<WriterInfo> getMasterInfo() {
        WriterInfo writer = new WriterInfo();
        User user = userService.findUserByName(masterUserName);
        int publishCount = readerService.findPublishCounts(user.getId());
        writer.setId(user.getId());
        writer.setType(user.getType());
        writer.setWriterPicSrc(user.getUserPicSrc());
        writer.setWriterPicThumbSrc(user.getUserPicThumbSrc());
        writer.setPublishCount(publishCount);
        writer.setNickName(user.getNickname());
        writer.setSignature(user.getSignature());
        writer.setEmail(user.getEmail());
        writer.setUsername(user.getUsername());
        return Result.success(writer);
    }

    /** 发表评论（需登录，含敏感词与长度校验） */
    @PostMapping("/addComment")
    public Result<String> addComment(@RequestBody @Validated Comment comment) {
        Map<String, Object> error = new HashMap<>();
        // 单用户态下关闭「新增」评论；列表接口 getComments 不动，历史评论照常展示
        if (!siteFeatures.isCommentEnabled()) {
            error.put("error", "评论功能暂未开放！");
            return Result.error(error);
        }
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        if (userInfo == null) {
            error.put("error", "请登录！");
            return Result.error(error);
        }
        int id = (int) userInfo.get("id");
        User addComment = userService.findUserById(id);
        if (addComment.getNickname() == null || addComment.getEmail() == null) {
            error.put("error", "请完善用户信息！");
            return Result.error(error);
        }
        String commentWord = comment.getContent();
        List<AhoCorasickUtil.Match> matches = articleService.containsSensitive(commentWord);
        if (!matches.isEmpty()) {
            error.put("error", "评论内容含有敏感词！");
            return Result.error(error);
        }
        if (comment.getContent().length() > 200) {
            error.put("error", "评论内容过长！");
            return Result.error(error);
        }
        if (Objects.equals(comment.getUserId(), id)) {
            boolean result = readerService.addComment(comment);
            if (result) return Result.success();
            else {
                error.put("error", "添加失败！");
                return Result.error(error);
            }
        }
        error.put("error", "非本人添加！");
        return Result.error(error);
    }

    /** 删除评论（站长 / 文章作者 / 评论本人） */
    @DeleteMapping("/deleteComment")
    public Result<String> deleteComment(@RequestParam int commentId,
                                        @RequestParam int articleId) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int userId = (int) userInfo.get("id");
        int type = (int) userInfo.get("type");
        if (type == 0) {
            boolean result = readerService.deleteComment(commentId);
            if (result) return Result.success();
            else {
                error.put("error", "删除失败！");
                return Result.error(error);
            }
        } else if (type == 1) {
            // 文章可能已被并发删除，取不到就当「作者这一路」不成立，继续往下走评论人判定
            Article article = articleService.findArticleByIdWithEntity(articleId);
            if (article != null && userId == article.getCreateUser()) {
                boolean result = readerService.deleteComment(commentId);
                if (result) return Result.success();
                else {
                    error.put("error", "删除失败！");
                    return Result.error(error);
                }
            }
        }
        // 评论已被删除（重复点删除）时没有归属可判，直接当作删除失败
        Comment comment = readerService.findCommentById(commentId);
        if (comment == null) {
            error.put("error", "评论不存在或已被删除！");
            return Result.error(error);
        }
        if (userId == comment.getUserId()) {
            boolean result = readerService.deleteComment(commentId);
            if (result) return Result.success();
            else {
                error.put("error", "删除失败！");
                return Result.error(error);
            }
        }
        error.put("error", "非本人删除！");
        return Result.error(error);
    }

    /** 文章评论分页 */
    @GetMapping("/comments")
    public Result<PageBean<Comment>> getComments(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam Integer articleId) {
        Map<String, Object> error = new HashMap<>();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        int totalComments = readerService.findAllCommentsCount(articleId);
        PageBean<Comment> allComments = new PageBean<>();
        if (totalComments == 0) {
            error.put("error", "无评论！");
            return Result.error(error);
        }
        int pageTotal = (int) Math.ceil(totalComments * 1.0 / pageSize);
        if (pageNum > pageTotal) pageNum = pageTotal;
        List<Comment> Comments = readerService.findAllComments(pageNum, pageSize, articleId);
        allComments.setTotal(totalComments);
        allComments.setItems(Comments);
        return Result.success(allComments);
    }

    /** 上报浏览量（登录按用户名去重，匿名按 IP+UA） */
    @PatchMapping("/article/addViews/{articleId}")
    public void addViews(@PathVariable int articleId, HttpServletRequest request) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username;
        if (userInfo != null) {
            username = userInfo.get("name").toString();
        } else {
            username = readerService.getIPMixUA(request);
        }
        articleService.addViews(username, articleId);
    }

    /** 热门文章 Top10 */
    @GetMapping("/article/hotArticles")
    public Result<List<Article>> hotArticles() {
        List<Article> articles = articleService.getViewsTop10();
        return Result.success(articles);
    }
}

