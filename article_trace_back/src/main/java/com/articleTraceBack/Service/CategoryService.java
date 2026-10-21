package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.Category;

import java.time.LocalDateTime;
import java.util.List;

public interface CategoryService {
    Category findCategoryByName(String categoryName);

    boolean insert(Category category);

    List<Category> findAllCategory();

    Category findById(Integer id);

    /**
     * 更新分类；{@code expectedUpdateTime} 为进入接口时读到的更新时间，用它做条件更新，
     * 两个窗口并发改名时后到的一方落空（历史行该列为 NULL 时按 IS NULL 匹配）。
     */
    boolean update(Category category, int uid, LocalDateTime expectedUpdateTime);

    boolean delete(int id);
}
