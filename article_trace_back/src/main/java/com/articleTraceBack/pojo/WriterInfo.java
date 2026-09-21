package com.articleTraceBack.pojo;

import lombok.Data;

@Data
public class WriterInfo {
    /** 用户 id：前端举报作者、跳个人页都要用，光有昵称定位不到人 */
    private int id;
    private String username;
    private String nickName;
    private String email;
    private int publishCount;
    private int type;
    private String writerPicSrc;
    private String writerPicThumbSrc;
}
