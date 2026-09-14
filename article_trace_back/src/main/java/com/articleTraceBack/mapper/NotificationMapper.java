package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    /** 某人的站内信总数；type 为 null 时查全部 */
    @Select("<script>"
            + "select count(*) from notification where receiver_id = #{receiverId} "
            + "<if test='type != null'>and `type` = #{type}</if>"
            + "</script>")
    int countByReceiver(@Param("receiverId") Integer receiverId, @Param("type") String type);

    /** 某人的站内信分页（按发送时间倒序）；type 为 null 时查全部 */
    @Select("<script>"
            + "select * from notification where receiver_id = #{receiverId} "
            + "<if test='type != null'>and `type` = #{type}</if> "
            + "order by create_time desc limit #{offset}, #{pageSize}"
            + "</script>")
    List<Notification> selectPageByReceiver(@Param("receiverId") Integer receiverId,
                                            @Param("type") String type,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);
}
