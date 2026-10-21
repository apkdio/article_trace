package com.articleTraceBack.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.articleTraceBack.mapper.CategoryMapper;
import com.articleTraceBack.pojo.Category;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public Category findCategoryByName(String categoryName) {
        QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
        // 分类名由 uk_category_name 保证唯一，limit 1 仅作防御
        queryWrapper.eq("category_name", categoryName).last("limit 1");
        return categoryMapper.selectOne(queryWrapper);
    }

    @Override
    public boolean insert(Category category) {
        category.setCreateTime(LocalDateTime.now());
        return categoryMapper.insert(category) == 1;
    }

    @Override
    public List<Category> findAllCategory() {
        return categoryMapper.categoryList();
    }

    @Override
    public Category findById(Integer id) {
        return categoryMapper.findCategoryDetail(id);
    }

    @Override
    public boolean update(Category category, int uid, LocalDateTime expectedUpdateTime) {
        category.setUpdateTime(LocalDateTime.now());
        UpdateWrapper<Category> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", category.getId())
                .set("last_update_user", uid);
        // 更新时间是秒级精度：只作为「读出后是否被人改过」的判据，不做严格版本控制
        if (expectedUpdateTime == null) {
            updateWrapper.isNull("update_time");
        } else {
            updateWrapper.eq("update_time", expectedUpdateTime);
        }
        return categoryMapper.update(category, updateWrapper) == 1;
    }

    @Override
    public boolean delete(int id) {
        QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        return categoryMapper.delete(queryWrapper) == 1;
    }
}
