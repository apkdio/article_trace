package com.articleTraceBack;

import com.articleTraceBack.mapper.CategoryMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Category;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试数据工厂。
 *
 * <p>用例应该自带它依赖的数据，而不是从库里「捞一条现成的」——后者在干净的测试库上必然失败，
 * 且会随开发库的数据变化时好时坏。这里提供幂等的 {@code ensureXxx}：存在就复用、不存在就插入。
 *
 * <p>命名统一带 {@link #PREFIX} 前缀，方便批量识别与清理测试产生的数据。
 */
@Component
public class TestFixtures {

    /** 测试数据统一前缀，便于与真实数据区分 */
    public static final String PREFIX = "zz-test-";

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * 短期唯一标签，8 位数字。
     *
     * <p>长度受限：{@code user.username} 为 varchar(20)、{@code nickname} 为 varchar(20)，
     * 命名过长会直接报 {@code Data too long}。PREFIX(8) + 8 位 = 16，两列都容得下。</p>
     */
    private static String tag() {
        return String.format("%06d%02d", System.nanoTime() % 1000000, SEQ.incrementAndGet() % 100);
    }

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;

    public TestFixtures(UserMapper userMapper, CategoryMapper categoryMapper) {
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
    }

    /** 生成本次调用唯一的后缀 */
    public static String unique() {
        return tag();
    }

    /**
     * 保证存在一个用户，返回其 id。
     *
     * @param type 0 站长 / 1 作者 / 2 读者
     */
    public int ensureUser(int type) {
        String tag = tag();
        User u = new User();
        u.setUsername(PREFIX + tag);                  // 16 字符，限 20
        u.setPassword("test-placeholder");
        u.setNickname("zt-" + tag);                   // 11 字符，限 15（PREFIX 太长，换短前缀）
        u.setEmail(PREFIX + tag + "@test.invalid");   // 29 字符，限 128
        u.setType(type);
        u.setUserPicSrc("");
        u.setUserPicThumbSrc("");
        u.setCreateTime(LocalDateTime.now());
        userMapper.insert(u);
        return u.getId();
    }

    /**
     * 保证存在一个分类，返回其 id。
     *
     * <p>分类名同样受列宽限制，用短前缀。</p>
     */
    public int ensureCategory(int createUser) {
        Category c = new Category();
        c.setCategoryName("zt-" + tag());             // 11 字符，限 15
        c.setCreateUser(createUser);
        c.setCreateTime(LocalDateTime.now());
        categoryMapper.insert(c);
        return c.getId();
    }

    /** 删除本次测试建的用户（按前缀匹配） */
    public int cleanUsers() {
        return userMapper.delete(new QueryWrapper<User>().likeRight("username", PREFIX));
    }

    /** 删除本次测试建的分类（按前缀匹配） */
    public int cleanCategories() {
        return categoryMapper.delete(new QueryWrapper<Category>().likeRight("category_name", PREFIX));
    }
}
