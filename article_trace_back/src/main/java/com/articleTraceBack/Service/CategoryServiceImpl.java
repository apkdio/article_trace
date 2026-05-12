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
        queryWrapper.eq("category_name", categoryName);
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
    public boolean update(Category category, int uid) {
        UpdateWrapper<Category> updateWrapper = new UpdateWrapper<>();
        category.setUpdateTime(LocalDateTime.now());
        updateWrapper.eq("id", category.getId())
                .set("last_update_user", uid);
        return categoryMapper.update(category, updateWrapper) == 1;
    }

    @Override
    public boolean delete(int id) {
        QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        return categoryMapper.delete(queryWrapper) == 1;
    }
}
