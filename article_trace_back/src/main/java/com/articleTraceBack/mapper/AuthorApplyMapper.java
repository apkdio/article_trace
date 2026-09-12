package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.AuthorApply;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AuthorApplyMapper extends BaseMapper<AuthorApply> {

    /** 申请分页（联查申请人信息）；status 为 null 时查全部 */
    @Select("<script>"
            + "select a.*, u.username as username, u.nickname as nickname "
            + "from author_apply a left join user u on a.user_id = u.id "
            + "<where><if test='status != null'>and a.status = #{status}</if></where> "
            + "order by a.create_time desc limit #{offset}, #{pageSize}"
            + "</script>")
    List<AuthorApply> selectPageWithUser(@Param("status") Integer status,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    /** 申请总数；status 为 null 时查全部 */
    @Select("<script>"
            + "select count(*) from author_apply "
            + "<where><if test='status != null'>and status = #{status}</if></where>"
            + "</script>")
    int countByStatus(@Param("status") Integer status);
}
