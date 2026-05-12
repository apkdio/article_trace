package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.Category;

import java.util.List;

public interface CategoryService {
    Category findCategoryByName(String categoryName);

    boolean insert(Category category);

    List<Category> findAllCategory();

    Category findById(Integer id);

    boolean update(Category category, int uid);

    boolean delete(int id);
}
