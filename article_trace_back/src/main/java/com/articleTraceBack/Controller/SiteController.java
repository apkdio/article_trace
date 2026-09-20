package com.articleTraceBack.Controller;

import com.articleTraceBack.config.SiteFeatureProperties;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 站点级公开配置。
 *
 * <p>前端启动时读一次，据此决定「注册 / 评论 / 申请作者」这些入口要不要渲染。
 * 关闭必须做两层——界面隐藏 + 接口拒绝，只做接口的话用户是在界面上点了之后才吃一句报错。</p>
 *
 * <p>本接口<b>必须免登录</b>：用户在登录页就要用到它，所以路径登记在
 * {@code spring.tokenCheck.excludeUrls} 里。</p>
 */
@RestController
@RequestMapping("/site")
public class SiteController {

    private final SiteFeatureProperties siteFeatures;

    public SiteController(SiteFeatureProperties siteFeatures) {
        this.siteFeatures = siteFeatures;
    }

    /** 当前站点的功能开关；字段名与前端一一对应 */
    @GetMapping("/features")
    public Result<Map<String, Object>> features() {
        Map<String, Object> features = new HashMap<>();
        features.put("registerEnabled", siteFeatures.isRegisterEnabled());
        features.put("commentEnabled", siteFeatures.isCommentEnabled());
        features.put("authorApplyEnabled", siteFeatures.isAuthorApplyEnabled());
        features.put("displayName", siteFeatures.getDisplayName());
        features.put("logoEnabled", siteFeatures.isLogoEnabled());
        return Result.success(features);
    }
}
