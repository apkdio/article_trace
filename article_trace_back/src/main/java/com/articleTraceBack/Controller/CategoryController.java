package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
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
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int id = (int) userInfo.get("id");
        Map<String, Object> error = new HashMap<>();
        if (categoryService.findCategoryByName(category.getCategoryName()) == null) {
            category.setCreateUser(id);
            try {
                if (categoryService.insert(category)) {
                    return Result.success();
                }
            } catch (DuplicateKeyException e) {
                // 并发下由唯一索引 uk_category_name 兜底
                error.put("categoryName", "文章分类已存在！");
                return Result.error(error);
            }
            error.put("error", "插入失败！请重试！");
            return Result.error(error);
        }
        error.put("categoryName", "文章分类已存在！");
        return Result.error(error);
    }

    /** 分类列表 */
    @GetMapping()
    public Result<List<Category>> findAllCategory() {
        List<Category> allCategory = categoryService.findAllCategory();
        if (allCategory == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "列表为空！");
            return Result.error(error);
        }
        return Result.success(allCategory);
    }

    /** 分类详情 */
    @GetMapping("/detail/{id}")
    public Result<Category> findCategoryById(@PathVariable("id") Integer id) {
        Map<String, Object> error = new HashMap<>();
        Category detail = categoryService.findById(id);
        if (detail != null) {
            return Result.success(detail);
        }
        error.put("error", "未找到该类别！");
        return Result.error(error);
    }

    /** 更新分类（仅创建人） */
    @PatchMapping("/update/{id}")
    public Result<String> updateCategory(@RequestBody @Validated Category category,
                                         @PathVariable("id") int id) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        int uid = (int) userInfo.get("id");
        Category existing = categoryService.findById(id);
        if (existing == null) {
            error.put("error", "类别不存在！");
            return Result.error(error);
        }
        // 与 deleteCategory 保持一致：只能改自己创建的分类
        if (!Objects.equals(existing.getCreateUser(), uid)) {
            error.put("error", "非法用户更新请求！");
            return Result.error(error);
        }
        String newCategoryName = category.getCategoryName();
        Category categoryByName = categoryService.findCategoryByName(newCategoryName);
        if (categoryByName != null && !Objects.equals(categoryByName.getId(), id)) {
            error.put("categoryName", "分类名已存在！");
            return Result.error(error);
        }
        category.setId(id);
        try {
            if (categoryService.update(category, uid)) {
                return Result.success();
            }
        } catch (DuplicateKeyException e) {
            error.put("categoryName", "分类名已存在！");
            return Result.error(error);
        }
        error.put("error", "更新失败！");
        return Result.error(error);
    }

    /** 删除分类（仅创建人），关联文章的分类置空 */
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteCategory(@PathVariable("id") int id) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        int uid = (int) userInfo.get("id");
        Category ca = categoryService.findById(id);
        if (ca == null) {
            error.put("error", "文章类别不存在！");
            return Result.error(error);
        }
        if (ca.getCreateUser() != uid) {
            error.put("error", "非法用户删除请求！");
            return Result.error(error);
        }
        if (categoryService.delete(id)) {
            return Result.success();
        }
        error.put("error", "删除失败！");
        return Result.error(error);
    }
}
