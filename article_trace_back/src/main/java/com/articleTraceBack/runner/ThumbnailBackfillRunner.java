package com.articleTraceBack.runner;

import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量缩略图补齐：启动时为「已上传但缺少缩略图」的封面/头像生成缩略图。
 *
 * <p>默认关闭（thumbnail.backfill.enabled=false），仅在需要一次性补齐旧数据时开启。
 * 无封面（cover_img 为空）或无头像（user_pic 为空）的记录会被跳过。</p>
 */
@Slf4j
@Component
@Order(2)
public class ThumbnailBackfillRunner implements CommandLineRunner {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final RustFsUtil rustFsUtil;

    @Value("${thumbnail.backfill.enabled:false}")
    private boolean enabled;

    public ThumbnailBackfillRunner(ArticleMapper articleMapper, UserMapper userMapper, RustFsUtil rustFsUtil) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.rustFsUtil = rustFsUtil;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        int count = 0;
        log.info("Thumbnail completion start!");
        // 补齐文章封面（无封面的跳过）
        List<Article> articles = articleMapper.selectList(null);
        for (Article a : articles) {
            if (a.getCoverImg() == null || a.getCoverImg().isEmpty()) {
                continue;
            }
            if (rustFsUtil.exists(RustFsUtil.getThumbKey(a.getCoverImg()), "image")) {
                continue;
            }
            if (rustFsUtil.generateThumbFor(a.getCoverImg())) {
                count++;
            }
        }

        // 补齐用户头像（无头像的跳过）
        List<User> users = userMapper.selectList(null);
        for (User u : users) {
            if (u.getUserPic() == null || u.getUserPic().isEmpty()) {
                continue;
            }
            if (rustFsUtil.exists(RustFsUtil.getThumbKey(u.getUserPic()), "image")) {
                continue;
            }
            if (rustFsUtil.generateThumbFor(u.getUserPic())) {
                count++;
            }
        }

        log.info("Thumbnail completion finished, a total of {} thumbnails generated", count);
    }
}
