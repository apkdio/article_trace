package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.Comment;
import com.articleTraceBack.pojo.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;

import java.util.List;


public interface ReaderService {
    boolean addComment(Comment comment);

    boolean deleteComment(int id);

    int findAllCommentsCount(Integer articleId);

    List<Comment> findAllComments(int pageNum, int pageSize, Integer articleId);

    Comment findCommentById(int commentId);

    User findUserByNickName(String nickName);

    int findPublishCounts(@NotNull Integer id);

    String getIPMixUA(HttpServletRequest request);

    /** 发信链路测试：向指定邮箱发一封测试邮件（to 为空时用配置的默认收件人） */
    boolean sendTestMail(String to);
}
