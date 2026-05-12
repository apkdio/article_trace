package com.articleTraceBack.pojo;

import lombok.Data;

@Data
public class WriterInfo {
    private String username;
    private String nickName;
    private String email;
    private int publishCount;
    private int type;
    private String writerPicSrc;
}
