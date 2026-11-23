package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Utils.BusinessException;
import com.articleTraceBack.Utils.CurrentUser;
import com.articleTraceBack.pojo.Category;
import com.articleTraceBack.pojo.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/category")
public class CategoryController {
    final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 新增分类（作者，名称唯一） */
    @PostMapping("/add")
    public Result<String> addCategory(@RequestBody @Validated Category category) {
        int id = CurrentUser.require().id();
        if (categoryService.findCategoryByName(category.getCategoryName()) != null) {
            throw new BusinessException("categoryName", "文章分类已存在！");
        }
        category.setCreateUser(id);
        try {
            if (!categoryService.insert(category)) {
                throw new BusinessException("error", "插入失败！请重试！");
            }
        } catch (DuplicateKeyException e) {
            // 并发下由唯一索引 uk_category_name 兜底
            throw new BusinessException("categoryName", "文章分类已存在！");
        }
        return Result.success();
    }

    /** 分类列表 */
    @GetMapping()
    public Result<List<Category>> findAllCategory() {
        return Result.success(categoryService.findAllCategory());
    }

    /** 更新分类（仅创建人） */
    @PatchMapping("/update/{id}")
    public Result<String> updateCategory(@RequestBody @Validated Category category,
                                         @PathVariable("id") int id) {
        int uid = CurrentUser.require().id();
        Category existing = categoryService.findById(id);
        if (existing == null) {
            throw new BusinessException("error", "类别不存在！");
        }
        // 与 deleteCategory 保持一致：只能改自己创建的分类
        if (!Objects.equals(existing.getCreateUser(), uid)) {
            throw new BusinessException("error", "非法用户更新请求！");
        }
        String newCategoryName = category.getCategoryName();
        Category categoryByName = categoryService.findCategoryByName(newCategoryName);
        if (categoryByName != null && !Objects.equals(categoryByName.getId(), id)) {
            throw new BusinessException("categoryName", "分类名已存在！");
        }
        category.setId(id);
        try {
            if (!categoryService.update(category, uid, existing.getUpdateTime())) {
                // 条件更新落空：进入接口后别人改过这个分类
                throw new BusinessException("error", "分类刚被修改，请刷新后重试！");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("categoryName", "分类名已存在！");
        }
        return Result.success();
    }

    /** 删除分类（仅创建人），关联文章的分类置空 */
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteCategory(@PathVariable("id") int id) {
        int uid = CurrentUser.require().id();
        Category ca = categoryService.findById(id);
        if (ca == null) {
            throw new BusinessException("error", "文章类别不存在！");
        }
        if (ca.getCreateUser() != uid) {
            throw new BusinessException("error", "非法用户删除请求！");
        }
        if (!categoryService.delete(id)) {
            throw new BusinessException("error", "删除失败！");
        }
        return Result.success();
    }
}
