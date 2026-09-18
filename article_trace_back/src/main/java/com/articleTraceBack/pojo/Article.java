package com.articleTraceBack.pojo;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Article {
    @TableId(type = IdType.AUTO)
    private Integer id;//主键ID

    @NotBlank(message = "标题不能为空！")
    @Size(max = 30, message = "标题长度不能超过 30 个字符！")
    @Pattern(regexp = "^\\S(.*\\S)?$", message = "标题首尾不能是空格！")
    private String title;//文章标题

    @NotNull(message = "不能为空！")
    @NotEmpty
    private String content;//文章内容
    private String coverImg;//封面图像

    @NotNull(message = "不能为空！")
    private Integer state;//发布状态 驳回 3 | 待审核 2 | 已发布1 | 草稿 0

    @NotNull(message = "不能为空！")
    private Integer categoryId;//文章分类id

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer createUser;//创建人ID

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;//创建时间

    private Long views;

    /** 是否命中违禁词（1 命中）—— 只回给审核方，见 {@link #hideSensitiveDetail} */
    private Integer sensitiveHit;

    /** 命中的违禁词，顿号分隔 —— 只回给审核方 */
    private String sensitiveWords;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;//更新时间

    @TableField(exist = false)
    private String createUserName;// 链接创建用户的名称

    @TableField(exist = false)
    private String categoryName;//链接文章种类名

    @TableField(exist = false)
    private String coverImgSrc;

    @TableField(exist = false)
    private String coverThumbSrc;

    /**
     * 清掉只该给审核方看的字段。
     *
     * <p>作者与公众的响应里不能留下“命中违禁词”的痕迹——尤其是 {@code sensitiveWords}，
     * 那等于把词库逐条告诉作者，他便可以绕着写。面向非站长的返回必须先过一遍这里。</p>
     */
    public static void hideSensitiveDetail(List<Article> articles) {
        if (articles == null) {
            return;
        }
        for (Article article : articles) {
            if (article != null) {
                article.setSensitiveHit(null);
                article.setSensitiveWords(null);
            }
        }
    }
}
