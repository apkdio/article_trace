package com.articleTraceBack;

import com.articleTraceBack.Service.ReportService;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.mapper.CommentMapper;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.ReportMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Comment;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Report;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 举报链路：提交 → 站长处置 → 双方收到通知。
 *
 * <p>三条关键约定：</p>
 * <ul>
 *   <li>举报自己、重复举报、对象不存在一律拒绝，且**不留下记录**；</li>
 *   <li>处置走条件更新，同一条举报只可能被处理一次（第二个请求返回 false，通知也不会重复发）；</li>
 *   <li>本服务**不碰被举报的内容**——删评论、下架文章是另外的接口，用例里断言对象原样还在。</li>
 * </ul>
 *
 * <pre>mvn test -Dtest=ReportServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    private Integer reporterId;
    private Integer authorId;
    private String reporterName;
    private String authorName;
    private Integer articleId;
    private Integer commentId;

    @BeforeEach
    public void setUp() {
        long seed = System.currentTimeMillis() % 100000000L;
        reporterName = "rp" + seed;
        authorName = "au" + seed;
        reporterId = insertUser(reporterName, "举报人");
        authorId = insertUser(authorName, "被举报人");

        Article article = new Article();
        article.setTitle("zy-report-" + seed);
        article.setContent("zz-test-content.json");
        article.setState(1);
        article.setCreateUser(authorId);
        article.setCreateTime(LocalDateTime.now());
        articleMapper.insert(article);
        articleId = article.getId();

        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(authorId);
        comment.setContent("zz-test-comment");
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        commentId = comment.getId();
    }

    @AfterEach
    public void tearDown() {
        if (reporterId == null) {
            return;
        }
        QueryWrapper<Report> rw = new QueryWrapper<>();
        rw.eq("reporter_id", reporterId);
        reportMapper.delete(rw);

        // 发给站长的那条（内容里带举报人用户名，便于精确清理）
        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.like("content", reporterName);
        notificationMapper.delete(masterNw);

        for (Integer uid : new Integer[]{reporterId, authorId}) {
            QueryWrapper<Notification> nw = new QueryWrapper<>();
            nw.eq("receiver_id", uid);
            notificationMapper.delete(nw);
        }

        if (commentId != null) {
            commentMapper.deleteById(commentId);
        }
        if (articleId != null) {
            articleMapper.deleteById(articleId);
        }
        userMapper.deleteById(reporterId);
        userMapper.deleteById(authorId);
    }

    private Integer insertUser(String username, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword("x");
        user.setEmail(username + "@example.invalid");
        user.setType(2);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    public void testSubmitThenDispose() {
        assertTrue(reportService.submit(reporterId, Report.TARGET_ARTICLE, articleId, "内容涉嫌违规"),
                "首次举报应成功");

        QueryWrapper<Report> rw = new QueryWrapper<>();
        rw.eq("reporter_id", reporterId).eq("target_type", Report.TARGET_ARTICLE);
        Report saved = reportMapper.selectOne(rw);
        assertNotNull(saved, "举报应落库");
        assertEquals(articleId, saved.getTargetId());
        assertEquals(Report.STATUS_PENDING, saved.getStatus());
        assertNotNull(saved.getCreateTime());

        // 提交后站长收到待审通知，且独立归入 report 类型（前端可按类型筛）
        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.eq("type", Notification.TYPE_REPORT).like("content", reporterName);
        assertTrue(notificationMapper.selectCount(masterNw) >= 1, "提交后站长应收到举报通知");

        assertTrue(reportService.pendingCount() >= 1);

        // 重复举报被唯一索引挡下，且不该多出一条记录
        assertFalse(reportService.submit(reporterId, Report.TARGET_ARTICLE, articleId, "再报一次"),
                "同一人对同一对象不应重复举报");
        assertEquals(1L, reportMapper.selectCount(rw).longValue(), "重复举报不应新增记录");

        // 举报自己：唯一索引拦不住，得由服务挡
        assertFalse(reportService.submit(authorId, Report.TARGET_ARTICLE, articleId, "报自己"),
                "不应允许举报自己的内容");

        // 对象不存在 / 类型非法 / 理由不合法，一律拒绝
        assertFalse(reportService.submit(reporterId, Report.TARGET_ARTICLE, 99999999, "对象不存在"));
        assertFalse(reportService.submit(reporterId, "video", articleId, "类型非法"));
        assertFalse(reportService.submit(reporterId, Report.TARGET_ARTICLE, articleId, "   "), "空理由应被拒");
        assertFalse(reportService.submit(reporterId, Report.TARGET_ARTICLE, articleId, "x".repeat(201)),
                "超长理由应被拒");

        // 列表带上举报人信息与对象摘要，站长不开新页也能认出报的是哪一条
        PageBean<Report> page = reportService.list(Report.STATUS_PENDING, 1, 10);
        Report listed = page.getItems().stream()
                .filter(item -> reporterId.equals(item.getReporterId()))
                .findFirst().orElse(null);
        assertNotNull(listed, "待处理列表应包含刚提交的举报");
        assertEquals(reporterName, listed.getReporterUsername());
        assertEquals("举报人", listed.getReporterNickname());
        assertTrue(listed.getTargetSummary() != null && listed.getTargetSummary().startsWith("zy-report-"),
                "文章类举报的摘要应是标题，实际：" + listed.getTargetSummary());

        // 驳回举报：状态置为已驳回，举报人收到「未成立」，被举报人不受打扰
        assertTrue(reportService.handle(saved.getId(), false, 1));
        Report rejected = reportMapper.selectById(saved.getId());
        assertEquals(Report.STATUS_REJECTED, rejected.getStatus());
        assertNotNull(rejected.getHandleTime());
        assertNotNull(rejected.getHandleUser());
        assertTrue(titleCount(reporterId, "举报未成立") >= 1, "驳回后举报人应收到通知");
        assertEquals(0L, titleCount(authorId, "你的内容被处理").longValue(),
                "只是驳回举报时不应通知被举报人");

        // 已处理的记录不能重复处置
        assertFalse(reportService.handle(saved.getId(), true, 1), "已处理的举报不应能再次处置");
    }

    @Test
    public void testHandleCommentReportKeepsContentAndNotifiesOwner() {
        assertTrue(reportService.submit(reporterId, Report.TARGET_COMMENT, commentId, "辱骂或人身攻击"));

        QueryWrapper<Report> rw = new QueryWrapper<>();
        rw.eq("reporter_id", reporterId).eq("target_type", Report.TARGET_COMMENT);
        Report saved = reportMapper.selectOne(rw);
        assertNotNull(saved);

        // 评论没有独立页面：列表要带上它所属的文章 id，站长才能跳过去或走原有的删评论接口
        PageBean<Report> page = reportService.list(Report.STATUS_PENDING, 1, 10);
        Report listed = page.getItems().stream()
                .filter(item -> reporterId.equals(item.getReporterId()))
                .findFirst().orElse(null);
        assertNotNull(listed);
        assertEquals(articleId, listed.getTargetParentId(), "评论类举报应带出所属文章 id");
        assertEquals("zz-test-comment", listed.getTargetSummary());

        assertTrue(reportService.handle(saved.getId(), true, 1));

        Report handled = reportMapper.selectById(saved.getId());
        assertEquals(Report.STATUS_HANDLED, handled.getStatus());
        assertTrue(titleCount(reporterId, "举报已处理") >= 1, "处置后举报人应收到通知");
        assertTrue(titleCount(authorId, "你的内容被处理") >= 1, "处置后被举报人应收到通知");

        // 处置只改举报记录：删评论仍由原来的接口负责，这里断言内容原样还在
        assertNotNull(commentMapper.selectById(commentId), "举报处置不应直接删除被举报的评论");
        assertNotNull(articleMapper.selectById(articleId), "文章也不应被动过");
    }

    /** 某人收到的、标题为 title 的站内信条数 */
    private Long titleCount(Integer receiverId, String title) {
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", receiverId).eq("title", title);
        return notificationMapper.selectCount(nw);
    }
}
