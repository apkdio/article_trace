package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Category;
import com.articleTraceBack.pojo.Result;
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

    @PostMapping("/add")
    public Result<String> addCategory(@RequestBody @Validated Category category) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int id = (int) userInfo.get("id");
        Map<String, Object> error = new HashMap<>();
        if (categoryService.findCategoryByName(category.getCategoryName()) == null) {
            category.setCreateUser(id);
            if (categoryService.insert(category)) {
                return Result.success();
            }
            error.put("error", "插入失败！请重试！");
            return Result.error(error);
        }
        error.put("categoryName", "文章分类已存在！");
        return Result.error(error);
    }

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

    @PatchMapping("/update/{id}")
    public Result<String> updateCategory(@RequestBody @Validated Category category,
                                         @PathVariable("id") int id) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        int uid = (int) userInfo.get("id");
        if (categoryService.findById(id) == null) {
            error.put("error", "类别不存在！");
            return Result.error(error);
        }
        String newCategoryName = category.getCategoryName();
        Category categoryByName = categoryService.findCategoryByName(newCategoryName);
        if (categoryByName != null && !Objects.equals(categoryByName.getId(), id)) {
            error.put("categoryName", "分类名已存在！");
            return Result.error(error);
        }
        category.setId(id);
        if (categoryService.update(category, uid)) {
            return Result.success();
        }
        error.put("error", "更新失败！");
        return Result.error(error);
    }

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
