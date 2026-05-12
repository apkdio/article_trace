package com.articleTraceBack.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.articleTraceBack.pojo.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {
    @Select("select * from user limit #{offset},#{pageSize}")
    List<User> findAllAccountsWithPage(int offset, int pageSize);
}
