package com.articleTraceBack;

import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.mapper.CategoryMapper;
import com.articleTraceBack.pojo.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分类更新的条件更新：进入接口后别人改过，更新应落空；create_time / create_user 不回写。
 *
 * <pre>mvn test -Dtest=CategoryUpdateCasTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CategoryUpdateCasTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private TestFixtures fixtures;

    private Category newCategory(int userId) {
        Category c = new Category();
        // 分类名限 20 字符：取纳秒尾部 10 位足够唯一
        String tag = String.valueOf(System.nanoTime());
        c.setCategoryName("cat" + tag.substring(tag.length() - 10));
        c.setCategoryAlias("测试");
        c.setCreateUser(userId);
        c.setCreateTime(LocalDateTime.now());
        categoryMapper.insert(c);
        return categoryMapper.selectById(c.getId());
    }

    private Category editOf(Category current, String alias) {
        Category edit = new Category();
        edit.setId(current.getId());
        edit.setCategoryName(current.getCategoryName());
        edit.setCategoryAlias(alias);
        return edit;
    }

    @Test
    public void staleUpdateTimeLoses() {
        int userId = fixtures.ensureUser(1);
        Category created = newCategory(userId);
        try {
            // 首次更新：update_time 原本为 NULL，走 IS NULL 条件
            assertTrue(categoryService.update(editOf(created, "第一次修改"), userId, created.getUpdateTime()));
            Category current = categoryMapper.selectById(created.getId());

            // 第二个窗口拿的还是进入接口时的旧 update_time（NULL）→ 必须落空
            assertFalse(categoryService.update(editOf(created, "第二次修改"), userId, created.getUpdateTime()),
                    "用旧的 update_time 更新应落空");
            assertEquals("第一次修改", categoryMapper.selectById(created.getId()).getCategoryAlias());

            // 用最新读到的 update_time 才能成功
            assertTrue(categoryService.update(editOf(created, "第二次修改"), userId, current.getUpdateTime()));
            assertEquals("第二次修改", categoryMapper.selectById(created.getId()).getCategoryAlias());
        } finally {
            categoryMapper.deleteById(created.getId());
        }
    }

    @Test
    public void updateDoesNotRewriteCreateFields() {
        int userId = fixtures.ensureUser(1);
        Category created = newCategory(userId);
        try {
            Category edit = editOf(created, "只改别名");
            edit.setCreateTime(created.getCreateTime().minusDays(5));
            edit.setCreateUser(userId + 1000);

            assertTrue(categoryService.update(edit, userId, created.getUpdateTime()));
            Category after = categoryMapper.selectById(created.getId());
            assertEquals(created.getCreateTime(), after.getCreateTime(), "创建时间不得被回写");
            assertEquals(userId, after.getCreateUser(), "创建人不得被回写");
        } finally {
            categoryMapper.deleteById(created.getId());
        }
    }
}
