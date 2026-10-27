package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface ArticleMapper extends BaseMapper<Article> {

    @Select("<script>select a.*,IFNULL(c.category_name,'未分类') as categoryName,u.nickname as createUserName " +
            "from article a left join user u on a.create_user = u.id " +
            "left join category c on a.category_id = c.id " +
            "where u.id = #{uid} " +
            "and ( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and (#{state} is null or a.state = #{state}) " +
            "and (#{search} is null or a.title like concat('%',#{search},'%')) " +
            "order by " +
            // 显式按发布时间排序时覆盖默认的「最后改动」序；只认 1 / -1，其余走默认
            "<choose>" +
            "<when test='timeOrder != null and timeOrder == 1'>a.create_time asc</when>" +
            "<when test='timeOrder != null and timeOrder == -1'>a.create_time desc</when>" +
            "<otherwise>case when a.update_time is not null then a.update_time else a.create_time end desc</otherwise>" +
            "</choose> " +
            "limit #{offset},#{pageSize}" +
            "</script>")
    List<Article> allArticles(@Param("uid") int uid, @Param("pageNum") int pageNum,
                              @Param("pageSize") int pageSize, @Param("categoryId") Integer categoryId,
                              @Param("state") Integer state, @Param("offset") int offset,
                              @Param("search") String search, @Param("timeOrder") Integer timeOrder);

    @Select("select count(*)" +
            "from article a left join user u on a.create_user = u.id where u.id = #{uid} " +
            "and ( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and (#{state} is null or a.state = #{state}) " +
            "and (#{search} is null or a.title like concat('%',#{search},'%'))")
    int findAllArticlesWithConditions(@Param("uid") int uid, @Param("categoryId") Integer categoryId,
                                      @Param("state") Integer state, @Param("search") String search);

    @Select("select a.*,c.category_name as categoryName,u.nickname as createUserName " +
            "from article a " +
            "left join user u on u.id = a.create_user " +
            "left join category c on a.category_id = c.id " +
            "where a.id = #{id}")
    Article findArticleById(int id);

    @Select("SELECT id, title, views FROM article WHERE state = 1 ORDER BY views DESC LIMIT 10")
    List<Article> getHotArticlesTop10();

    @Select("select count(*)" +
            "from article a left join user u on a.create_user = u.id where " +
            "( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and ( " +
            " (#{state} is null and (a.state in (1,2,3) or (a.state = 0 and a.create_user = #{userId})))" +
            "or " +
            "(#{state} is not null and a.state = #{state} and (#{state} != 0 or a.create_user = #{userId}))" +
            "  )" +
            "and (#{nickName} is null or u.nickname = #{nickName}) " +
            "and (#{searchType} is null " +
            "or (#{searchType} = 0 and a.title like concat('%',#{search},'%')) " +
            "or (#{searchType} =1 and u.nickname like concat('%',#{search},'%')))")
    int findAllArticlesCountInMaster(Integer categoryId, Integer state, Integer userId, String search, Integer searchType, String nickName);

    @Select("select count(*)" +
            "from article a left join user u on a.create_user = u.id where " +
            "( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and (a.state = #{state}) " +
            "and (#{nickName} is null or u.nickname = #{nickName}) " +
            "and (#{searchType} is null " +
            "or (#{searchType} = 0 and a.title like concat('%',#{search},'%')) " +
            "or (#{searchType} =1 and u.nickname like concat('%',#{search},'%')))")
    int findAllArticlesCountInPublic(Integer categoryId, Integer state, String search, Integer searchType, String nickName);

    @Select("<script>" +
            "select a.*,IFNULL(c.category_name,'未分类') as categoryName,u.nickname as createUserName " +
            "from article a left join user u on a.create_user = u.id " +
            "left join category c on a.category_id = c.id " +
            "where " +
            "( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and ( " +
            " (#{state} is null and (a.state in (1,2,3) or (a.state = 0 and a.create_user = #{userId})))" +
            "or " +
            "(#{state} is not null and a.state = #{state} and (#{state} != 0 or a.create_user = #{userId}))" +
            "  )" +
            "and (#{nickName} is null or u.nickname = #{nickName}) " +
            "and (#{searchType} is null " +
            "or (#{searchType} = 0 and a.title like concat('%',#{search},'%')) " +
            "or (#{searchType} = 1 and u.nickname like concat('%',#{search},'%')))" +
            "order by " +
            // 显式按发布时间排序优先；未指定时保留原口径：待审视图命中违禁词的排前面，
            // 其余视图按「最后改动」倒序
            "<choose>" +
            "<when test='timeOrder != null and timeOrder == 1'>a.create_time asc</when>" +
            "<when test='timeOrder != null and timeOrder == -1'>a.create_time desc</when>" +
            "<otherwise>" +
            "<if test='state != null and state == 2'>a.sensitive_hit desc,</if>" +
            "case when a.update_time is not null then a.update_time else a.create_time end desc" +
            "</otherwise>" +
            "</choose> " +
            "limit #{offset},#{pageSize}" +
            "</script>")
    List<Article> findAllArticlesInMaster(int pageNum, int pageSize,
                                          Integer categoryId, Integer state, Integer userId,
                                          Integer offset, String search, Integer searchType, String nickName,
                                          Integer timeOrder);

    @Select("select a.*,IFNULL(c.category_name,'未分类') as categoryName,u.nickname as createUserName " +
            "from article a left join user u on a.create_user = u.id " +
            "left join category c on a.category_id = c.id " +
            "where " +
            "( " +
            "   #{categoryId} is null " +
            "   or (#{categoryId} = -1 and a.category_id is null) " +
            "   or a.category_id = #{categoryId} " +
            ") " +
            "and (#{state} is null or a.state = #{state}) " +
            "and (#{nickName} is null or u.nickname = #{nickName}) " +
            "and (#{searchType} is null " +
            "or (#{searchType} = 0 and a.title like concat('%',#{search},'%')) " +
            "or (#{searchType} = 1 and u.nickname like concat('%',#{search},'%')))" +
            // 首页列表按发布时间倒序：站长编辑老文章不该把它顶到最前面（那是「最近改动」而不是「最近发布」）
            "order by a.create_time desc " +
            "limit #{offset},#{pageSize}")
    List<Article> findAllArticlesInPublic(int pageNum, int pageSize, Integer categoryId, Integer state, int offset, String search, Integer searchType, String nickName);

    @Update({
            "<script>",
            "UPDATE article SET views = views + CASE id ",
            "<foreach collection='updates.entrySet()' item='delta' index='id'>",
            "WHEN #{id} THEN #{delta} ",
            "</foreach>",
            "END WHERE id IN ",
            "<foreach collection='updates.keySet()' item='id' open='(' close=')' separator=','>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    void batchAddViews(@Param("updates") Map<Long, Long> updates);
}
