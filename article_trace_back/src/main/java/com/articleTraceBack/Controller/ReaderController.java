package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.ReaderService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
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
    @Value("${spring.application.admin.defaultUser}")
    private String masterUserName;

    public ReaderController(ArticleService articleService, ReaderService readerService, UserService userService) {
        this.articleService = articleService;
        this.readerService = readerService;
        this.userService = userService;
    }

    @GetMapping("/getArticles")
    public Result<PageBean<Article>> getArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer searchType,
            @RequestParam(required = false) String nickName) {
        Map<String, Object> error = new HashMap<>();
        if (pageNum <= 0) {
            pageNum = 1;
        }
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

    @GetMapping("/article/{id}")
    public Result<Article> getArticleById(@PathVariable int id) {
        Map<String, Object> error = new HashMap<>();
        if (articleService.findArticleById(id)) {
            Article article = articleService.findArticleByIdWithEntity(id);
            if (article.getState() != 1) {
                error.put("error", "文章未发布！");
                return Result.error(error);
            }
            article.setViews(articleService.getViews(id));
            return Result.success(article);
        }
        error.put("error", "文章不存在!");
        return Result.error(error);
    }

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
        writer.setType(user.getType());
        writer.setWriterPicSrc(user.getUserPicSrc());
        writer.setPublishCount(publishCount);
        writer.setNickName(user.getNickname());
        writer.setEmail(user.getEmail());
        writer.setUsername(user.getUsername());
        return Result.success(writer);
    }

    @GetMapping("/masterInfo")
    public Result<WriterInfo> getMasterInfo() {
        WriterInfo writer = new WriterInfo();
        User user = userService.findUserByName(masterUserName);
        int publishCount = readerService.findPublishCounts(user.getId());
        writer.setType(user.getType());
        writer.setWriterPicSrc(user.getUserPicSrc());
        writer.setPublishCount(publishCount);
        writer.setNickName(user.getNickname());
        writer.setEmail(user.getEmail());
        writer.setUsername(user.getUsername());
        return Result.success(writer);
    }

    @PostMapping("/addComment")
    public Result<String> addComment(@RequestBody @Validated Comment comment) {
        Map<String, Object> error = new HashMap<>();
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
            if (userId == articleService.findArticleByIdWithEntity(articleId).getCreateUser()) {
                boolean result = readerService.deleteComment(commentId);
                if (result) return Result.success();
                else {
                    error.put("error", "删除失败！");
                    return Result.error(error);
                }
            }
        }
        if (userId == readerService.findCommentById(commentId).getUserId()) {
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

    @GetMapping("/comments")
    public Result<PageBean<Comment>> getComments(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam Integer articleId) {
        Map<String, Object> error = new HashMap<>();
        if (pageNum <= 0) {
            pageNum = 1;
        }
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

    @GetMapping("/article/hotArticles")
    public Result<List<Article>> hotArticles() {
        List<Article> articles = articleService.getViewsTop10();
        return Result.success(articles);
    }
}

