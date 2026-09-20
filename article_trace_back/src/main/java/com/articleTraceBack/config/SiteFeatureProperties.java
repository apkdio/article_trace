package com.articleTraceBack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 站点功能开关（对应 application.yml 的 site.*）。
 *
 * <p>用于把站点收敛成「单用户态」，以满足个人 ICP 备案的口径——个人备案要求网站内容
 * 不涉及企业、团体、论坛，而开放注册 + 多作者供稿 + 评论正好踩在这三条上。三处一并关掉后，
 * 站点从外面看就是个人博客。</p>
 *
 * <p><b>默认全开</b>：本地开发即多用户态，线上由 .env 注入 false 切换，代码不需要分叉。
 * 切换形态只改配置 + 重建容器，不必切分支、不必重新构建。</p>
 *
 * <p>关闭只作用于<b>新增</b>入口，历史数据照常展示（例如评论列表仍然可见）。</p>
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
}
