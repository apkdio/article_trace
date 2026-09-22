package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.PageBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


public interface ArticleService {
    /**
     * 新增或更新一篇文章；封面随本次请求一起提交。
     *
     * @param cover 新封面文件；为 null 表示不换封面，此时 {@code article.coverImg}
     *              只接受「空串 = 删除封面」或「与库中原值一致」，其余一律回退为原值
     */
    boolean articleAddOrUpdate(Article article, int type, MultipartFile cover, String username);

    /** 查同一作者是否已有同名文章（标题在作者内唯一，不同作者可同名） */
    Article findArticleByUserAndTitle(int userId, String articleTitle);
    List<AhoCorasickUtil.Match> containsSensitive(String content);
    PageBean<Article> findAllArticlesWithPage(int uid, int pageNum, int pageSize,
                                              Integer categoryId, Integer state, String search);

    boolean findArticleById(int id);


    int findAllArticlesWithConditions(int uid, Integer categoryId, Integer state, String search);

    Article findArticleByIdWithEntity(int id);

    boolean deleteById(int id);

    /** 删除封面对象；只允许删除自己文章正在引用的封面（key 由客户端传入，不可信） */
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

    /** 判断某角色能否把文章状态从 {@code from} 流转到 {@code to}（站长可跳过审核直接发布，判断需带角色维度）。 */
    boolean canTransfer(int from, int to, int roleType);

    void addViews(String username, int articleId);

    Long getViews(int id);

    List<Article> getViewsTop10();
}
