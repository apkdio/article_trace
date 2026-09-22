package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.mapper.CommentMapper;
import com.articleTraceBack.mapper.ReportMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Comment;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Report;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 举报实现：处置走乐观 CAS（影响行数不为 1 视为已被处理，不再发通知）；本类不碰被举报内容。 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    /** 与 application.yml 的 notification.scenes 对齐 */
    private static final String SCENE_SUBMITTED = "report-submitted";
    private static final String SCENE_HANDLED = "report-handled";
    private static final String SCENE_REJECTED = "report-rejected";
    private static final String SCENE_NOTICE = "report-notice";

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** 与 report.reason 列长度一致 */
    private static final int MAX_REASON_LENGTH = 200;

    /** 列表里评论摘要的截断长度 */
    private static final int SUMMARY_LENGTH = 40;

    /** 对象已被删除时的摘要占位（文章下架删库、评论被删都走这条） */
    private static final String SUMMARY_MISSING = "（原内容已不存在）";

    private final ReportMapper reportMapper;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final UserService userService;
    private final NotificationService notificationService;

    public ReportServiceImpl(ReportMapper reportMapper,
                             ArticleMapper articleMapper,
                             CommentMapper commentMapper,
                             UserService userService,
                             NotificationService notificationService) {
        this.reportMapper = reportMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Override
    public boolean submit(int reporterId, String targetType, int targetId, String reason) {
        if (!isValidTargetType(targetType)) {
            return false;
        }
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            return false;
        }
        Integer ownerId = ownerOf(targetType, targetId);
        if (ownerId == null) {
            return false;
        }
        // 举报自己：唯一索引只拦「同一人对同一对象重复举报」，这条得单独挡
        if (ownerId == reporterId) {
            return false;
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setStatus(Report.STATUS_PENDING);
        report.setCreateTime(LocalDateTime.now());
        try {
            if (reportMapper.insert(report) != 1) {
                return false;
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过上面的校验，由唯一索引 uk_reporter_target 兜底
            log.info("duplicate report rejected: reporterId={}, target={}:{}", reporterId, targetType, targetId);
            return false;
        }
        log.info("report submitted: reportId={}, reporterId={}, target={}:{}",
                report.getId(), reporterId, targetType, targetId);

        notificationService.notifyRole(ROLE_MASTER, SCENE_SUBMITTED, "有新的举报待处理",
                reporterLabel(reporterId) + " 举报了一条" + targetLabel(targetType) + "，请及时处理。");
        return true;
    }

    @Override
    public PageBean<Report> list(Integer status, int pageNum, int pageSize) {
        int safePageNum = PageUtil.normalizePageNum(pageNum);
        int safePageSize = PageUtil.normalizePageSize(pageSize);
        int total = reportMapper.countByStatus(status);
        int offset = (safePageNum - 1) * safePageSize;
        List<Report> items = reportMapper.selectPageWithUser(status, offset, safePageSize);
        items.forEach(this::fillTargetSummary);
        return new PageBean<>(total, items);
    }

    @Override
    public int pendingCount() {
        QueryWrapper<Report> wrapper = new QueryWrapper<>();
        wrapper.eq("status", Report.STATUS_PENDING);
        return Math.toIntExact(reportMapper.selectCount(wrapper));
    }

    @Override
    public boolean handle(int reportId, boolean handled, int handlerId) {
        // 只为拿举报人与被举报对象；状态判断不依赖这次读取
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            return false;
        }

        // 条件更新：只有仍是「待处理」才改得动。并发下（两个站长同时点）只有一个请求影响行数为 1。
        UpdateWrapper<Report> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", reportId)
                .eq("status", Report.STATUS_PENDING)
                .set("status", handled ? Report.STATUS_HANDLED : Report.STATUS_REJECTED)
                .set("handle_user", handlerId)
                .set("handle_time", LocalDateTime.now());
        if (reportMapper.update(null, wrapper) != 1) {
            log.info("report already handled, skip: reportId={}, handlerId={}", reportId, handlerId);
            return false;
        }

        int reporterId = report.getReporterId();
        if (handled) {
            notificationService.notify(reporterId, SCENE_HANDLED, "举报已处理",
                    "你举报的内容经核实存在违规，已处理。感谢你的反馈。");
            // 被处置方也告知一声：站长是照着举报列表点掉的，当事人至少该知道原因
            Integer ownerId = ownerOf(report.getTargetType(), report.getTargetId());
            if (ownerId != null && ownerId != reporterId) {
                notificationService.notify(ownerId, SCENE_NOTICE, "你的内容被处理",
                        "你发布的" + targetLabel(report.getTargetType()) + "被举报并经核实违规，已被处理。");
            }
        } else {
            notificationService.notify(reporterId, SCENE_REJECTED, "举报未成立",
                    "你举报的内容经核实未构成违规。感谢你的反馈。");
        }
        log.info("report handled: reportId={}, handled={}, handlerId={}", reportId, handled, handlerId);
        return true;
    }

    /** 取被举报对象的归属人（文章作者 / 评论作者 / 用户本人）；对象不存在返回 null */
    private Integer ownerOf(String targetType, Integer targetId) {
        if (targetId == null) {
            return null;
        }
        if (Report.TARGET_ARTICLE.equals(targetType)) {
            Article article = articleMapper.selectById(targetId);
            return article == null ? null : article.getCreateUser();
        }
        if (Report.TARGET_COMMENT.equals(targetType)) {
            Comment comment = commentMapper.selectById(targetId);
            return comment == null ? null : comment.getUserId();
        }
        if (Report.TARGET_USER.equals(targetType)) {
            return userService.findUserById(targetId) == null ? null : targetId;
        }
        return null;
    }

    /** 填充被举报对象的摘要，让站长不开新页也能认出报的是哪一条 */
    private void fillTargetSummary(Report report) {
        String type = report.getTargetType();
        Integer targetId = report.getTargetId();
        if (targetId == null) {
            report.setTargetSummary(SUMMARY_MISSING);
            return;
        }
        if (Report.TARGET_ARTICLE.equals(type)) {
            Article article = articleMapper.selectById(targetId);
            report.setTargetSummary(article == null ? SUMMARY_MISSING : article.getTitle());
        } else if (Report.TARGET_COMMENT.equals(type)) {
            Comment comment = commentMapper.selectById(targetId);
            report.setTargetSummary(comment == null ? SUMMARY_MISSING : truncate(comment.getContent()));
            // 评论没有独立页面：站长处置时要么去文章下看，要么复用「删评论」接口，而它要 articleId
            report.setTargetParentId(comment == null ? null : comment.getArticleId());
        } else if (Report.TARGET_USER.equals(type)) {
            User user = userService.findUserById(targetId);
            report.setTargetSummary(user == null ? SUMMARY_MISSING : user.getNickname());
        } else {
            report.setTargetSummary(SUMMARY_MISSING);
        }
    }

    /** 取昵称用于通知文案；用户不存在时退回「用户#id」 */
    private String reporterLabel(int userId) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return "用户#" + userId;
        }
        return user.getNickname() + "（" + user.getUsername() + "）";
    }

    private String targetLabel(String targetType) {
        if (Report.TARGET_ARTICLE.equals(targetType)) {
            return "文章";
        }
        if (Report.TARGET_COMMENT.equals(targetType)) {
            return "评论";
        }
        if (Report.TARGET_USER.equals(targetType)) {
            return "用户";
        }
        return "内容";
    }

    private String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= SUMMARY_LENGTH ? text : text.substring(0, SUMMARY_LENGTH) + "…";
    }

    private boolean isValidTargetType(String targetType) {
        return Report.TARGET_ARTICLE.equals(targetType)
                || Report.TARGET_COMMENT.equals(targetType)
                || Report.TARGET_USER.equals(targetType);
    }
}
