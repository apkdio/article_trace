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

    boolean updateState(int id, int state);

    void addViews(String username, int articleId);

    Long getViews(int id);

    List<Article> getViewsTop10();
}
