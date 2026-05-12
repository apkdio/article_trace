package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface ArticleMapper extends BaseMapper<Article> {

    @Select("select a.*,IFNULL(c.category_name,'未分类') as categoryName,u.nickname as createUserName " +
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
            "case " +
            "when a.update_time is not null then a.update_time " +
            "else a.create_time " +
            "end desc " +
            "limit #{offset},#{pageSize}")
    List<Article> allArticles(@Param("uid") int uid, @Param("pageNum") int pageNum,
                              @Param("pageSize") int pageSize, @Param("categoryId") Integer categoryId,
                              @Param("state") Integer state, @Param("offset") int offset,
                              @Param("search") String search);

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

    @Select("select a.*,IFNULL(c.category_name,'未分类') as categoryName,u.nickname as createUserName " +
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
            "case " +
            "when a.update_time is not null then a.update_time " +
            "else a.create_time " +
            "end desc " +
            "limit #{offset},#{pageSize}")
    List<Article> findAllArticlesInMaster(int pageNum, int pageSize,
                                          Integer categoryId, Integer state, Integer userId,
                                          Integer offset, String search, Integer searchType, String nickName);

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
            "order by " +
            "case " +
            "when a.update_time is not null then a.update_time " +
            "else a.create_time " +
            "end desc " +
            "limit #{offset},#{pageSize}")
    List<Article> findAllArticlesInPublic(int pageNum, int pageSize, Integer categoryId, Integer state, int offset, String search, Integer searchType, String nickName);

    @Update({
            "<script>",
            "UPDATE article SET views = CASE id ",
            "<foreach collection='updates.entrySet()' item='views' index='id'>",
            "WHEN #{id} THEN #{views} ",
            "</foreach>",
            "END WHERE id IN ",
            "<foreach collection='updates.keySet()' item='id' open='(' close=')' separator=','>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    void batchIncrementViews(@Param("updates") Map<Long, Long> updates);
}
