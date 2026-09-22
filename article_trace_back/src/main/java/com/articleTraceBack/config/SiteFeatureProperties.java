package com.articleTraceBack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 站点功能开关（对应 application.yml 的 {@code site.*}）：默认全开，线上由 .env 注入 false 收敛为单用户态（适配个人备案口径）。
 * 关闭只作用于新增入口，历史数据照常展示。
 */
@Data
@Component
@ConfigurationProperties(prefix = "site")
public class SiteFeatureProperties {

    /** 是否开放注册；同时约束发码接口的 register 场景，否则验证码照发等于没关 */
    private boolean registerEnabled = true;

    /** 是否开放新增评论；历史评论不受影响 */
    private boolean commentEnabled = true;

    /** 是否开放「申请成为作者」 */
    private boolean authorApplyEnabled = true;

    /** 站点显示名（浏览器标题与页脚用它）；线上设成备案的网站名称 */
    private String displayName = "文迹";

    /** 站内是否展示 logo（首页与文章详情页页头）；个人备案站通常置 false，避免站名与备案信息不一致。 */
    private boolean logoEnabled = true;
}
