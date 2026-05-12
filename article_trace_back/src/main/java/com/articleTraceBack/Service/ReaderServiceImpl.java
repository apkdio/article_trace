package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.IPUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.mapper.CommentMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Comment;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ReaderServiceImpl implements ReaderService {
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final RustFsUtil rustFsUtil;

    public ReaderServiceImpl(CommentMapper commentMapper,
                             UserMapper userMapper, ArticleMapper articleMapper,
                             RustFsUtil rustFsUtil) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.articleMapper = articleMapper;
        this.rustFsUtil = rustFsUtil;
    }

    @Override
    public boolean addComment(Comment comment) {
        comment.setCreateTime(LocalDateTime.now());
        return commentMapper.insert(comment) == 1;
    }

    @Override
    public boolean deleteComment(int id) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        return commentMapper.delete(queryWrapper) == 1;
    }

    @Override
    public int findAllCommentsCount(Integer articleId) {
        return commentMapper.findAllCommentsCount(articleId);
    }

    @Override
    public List<Comment> findAllComments(int pageNum, int pageSize, Integer articleId) {
        int offset = (pageNum - 1) * pageSize;
        List<Comment> comments = commentMapper.findAllComments(offset, pageSize, articleId);
        for (Comment comment : comments) {
            if (!StringUtils.isBlank(comment.getUserPic()))
                comment.setUserPicSrc(rustFsUtil.getPciUrl(comment.getUserPic()));
            else comment.setUserPicSrc("");
        }
        return comments;
    }

    @Override
    public Comment findCommentById(int commentId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", commentId);
        return commentMapper.selectOne(queryWrapper);
    }

    @Override
    public User findUserByNickName(String nickName) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("nickname", nickName));
        if (user != null) {
            if (!Objects.equals(user.getUserPic(), "")) user.setUserPicSrc(rustFsUtil.getPciUrl(user.getUserPic()));
            else user.setUserPicSrc("");
            return user;
        } else return null;
    }

    @Override
    public int findPublishCounts(Integer id) {
        return articleMapper.findAllArticlesWithConditions(id, null, 1, null);
    }

    @Override
    public String getIPMixUA(HttpServletRequest request) {
        String ip = IPUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown";
        String raw = ip + "/" + userAgent;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
