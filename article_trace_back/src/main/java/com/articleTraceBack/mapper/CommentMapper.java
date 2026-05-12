package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Comment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {
    @Select("select count(*) from comments where article_id = #{articleId}")
    int findAllCommentsCount(Integer articleId);

    @Select("select u.nickname as nickname,u.user_pic as userpic,u.email as email ,c.* from comments c " +
            "left join user u on c.user_id = u.id " +
            "where article_id = #{articleId} " +
            "order by c.create_time desc " +
            "limit #{offset}, #{pageSize} ")
    List<Comment> findAllComments(int offset, int pageSize, Integer articleId);
}
