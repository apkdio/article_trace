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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
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
            if (!StringUtils.isBlank(comment.getUserPic())) {
                comment.setUserPicSrc(rustFsUtil.getPciUrl(comment.getUserPic()));
                comment.setUserPicThumbSrc(rustFsUtil.getThumbUrl(comment.getUserPic()));
            } else {
                comment.setUserPicSrc("");
                comment.setUserPicThumbSrc("");
            }
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
        // nickname 无唯一索引，重复时取首条，避免 selectOne 抛 TooManyResultsException
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("nickname", nickName).last("limit 1"));
        if (user != null) {
            if (!Objects.equals(user.getUserPic(), "")) {
                user.setUserPicSrc(rustFsUtil.getPciUrl(user.getUserPic()));
                user.setUserPicThumbSrc(rustFsUtil.getThumbUrl(user.getUserPic()));
            } else {
                user.setUserPicSrc("");
                user.setUserPicThumbSrc("");
            }
            return user;
        } else return null;
    }

    @Override
    public int findPublishCounts(Integer id) {
        return articleMapper.findAllArticlesWithConditions(id, null, 1, null);
    }

    @Override
    public String getIPMixUA(HttpServletRequest request) {
        return IPUtil.mixOf(request);
    }
}
