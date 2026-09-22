package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Report;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ReportMapper extends BaseMapper<Report> {

    /** 举报分页（联查举报人与处置人），status 为 null 时查全部；被举报对象不在 SQL 里联查，由 service 逐条取。 */
    @Select("<script>"
            + "select r.*, u.username as reporterUsername, u.nickname as reporterNickname, "
            + "h.username as handleUsername "
            + "from report r "
            + "left join user u on r.reporter_id = u.id "
            + "left join user h on r.handle_user = h.id "
            + "<where><if test='status != null'>and r.status = #{status}</if></where> "
            + "order by r.create_time desc limit #{offset}, #{pageSize}"
            + "</script>")
    List<Report> selectPageWithUser(@Param("status") Integer status,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    /** 记录总数；status 为 null 时查全部 */
    @Select("<script>"
            + "select count(*) from report "
            + "<where><if test='status != null'>and status = #{status}</if></where>"
            + "</script>")
    int countByStatus(@Param("status") Integer status);
}
