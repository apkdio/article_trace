package com.articleTraceBack.Controller;

import com.articleTraceBack.config.SiteFeatureProperties;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 站点级公开配置：前端启动时读取，决定注册 / 评论 / 申请作者等入口是否渲染。
 * 关闭需两层（界面隐藏 + 接口拒绝）；本接口必须免登录。
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
