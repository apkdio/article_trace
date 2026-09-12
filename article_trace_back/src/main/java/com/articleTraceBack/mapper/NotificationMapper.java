package com.articleTraceBack.mapper;

import com.articleTraceBack.pojo.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    /** 某人的站内信总数 */
    @Select("select count(*) from notification where receiver_id = #{receiverId}")
    int countByReceiver(Integer receiverId);

    /** 某人的站内信分页（按发送时间倒序） */
    @Select("select * from notification where receiver_id = #{receiverId} "
            + "order by create_time desc limit #{offset}, #{pageSize}")
    List<Notification> selectPageByReceiver(Integer receiverId, int offset, int pageSize);
}
