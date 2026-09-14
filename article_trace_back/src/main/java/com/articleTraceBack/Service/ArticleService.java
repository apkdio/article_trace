package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.PageBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


public interface ArticleService {
    boolean articleAddOrUpdate(Article article, int type);

    Article findArticleByName(String articleTitle);
    List<AhoCorasickUtil.Match> containsSensitive(String content);
    PageBean<Article> findAllArticlesWithPage(int uid, int pageNum, int pageSize,
                                              Integer categoryId, Integer state, String search);

    boolean findArticleById(int id);


    int findAllArticlesWithConditions(int uid, Integer categoryId, Integer state, String search);

    Article findArticleByIdWithEntity(int id);

    boolean deleteById(int id);

    Map<String, String> upload(MultipartFile cover, String username);

    boolean removeCover(String key);

    int findAllArticlesCountInMaster(Integer categoryId,
                                     Integer state, Integer userId, String search, Integer searchType, String nickName);

    int findAllArticlesCountInPublic(Integer categoryId,
                                     Integer state, String search, Integer searchType, String nickName);

    PageBean<Article> findAllArticlesInPublic(int pageNum, int pageSize, Integer categoryId,
                                              Integer state, String search,
                                              Integer searchType, String nickName);

    PageBean<Article> findAllArticlesInMaster(int pageNum, int pageSize,
                                              Integer categoryId, Integer state, Integer userId,
                                              String search, Integer searchType, String nickName);

    /** 文章状态：0 草稿 / 1 已发布 / 2 待审核 / 3 已驳回 */
    int STATE_DRAFT = 0;
    int STATE_PUBLISHED = 1;
    int STATE_PENDING = 2;
    int STATE_REJECTED = 3;

    /** 站长角色 type */
    int ROLE_MASTER = 0;

    boolean updateState(int id, int state);

    /**
     * 判断某角色能否把文章状态从 {@code from} 流转到 {@code to}。
     *
     * <p>站长可跳过审核直接发布自己的草稿，因此该判断必须带角色维度。</p>
     */
    boolean canTransfer(int from, int to, int roleType);

    void addViews(String username, int articleId);

    Long getViews(int id);

    List<Article> getViewsTop10();
}
