package com.articleTraceBack.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.articleTraceBack.pojo.Category;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CategoryMapper extends BaseMapper<Category> {
    @Select("select c.*,u1.nickname as createUserName,u2.nickname as lastUpdateUserName from category c " +
            "left join user u1  on c.create_user = u1.id "+
            "left join user u2 on c.last_update_user = u2.id")
    List<Category> categoryList();

    @Select("select c.*,u1.nickname as createUserName,u2.nickname as lastUpdateUserName from category c " +
            "left join user u1  on c.create_user = u1.id " +
            "left join user u2 on c.last_update_user = u2.id " +
            "where c.id = #{id}")
    Category findCategoryDetail(int id);
}
