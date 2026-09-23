package com.articleTraceBack.Service;

import com.articleTraceBack.mapper.ProfileApplyMapper;
import com.articleTraceBack.pojo.ProfileApply;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ProfileApplyServiceImpl implements ProfileApplyService {

    private final ProfileApplyMapper profileApplyMapper;

    public ProfileApplyServiceImpl(ProfileApplyMapper profileApplyMapper) {
        this.profileApplyMapper = profileApplyMapper;
    }

    @Override
    public boolean submit(int userId, int type, String pendingValue) {
        ProfileApply apply = new ProfileApply();
        apply.setUserId(userId);
        apply.setType(type);
        apply.setPendingValue(pendingValue);
        apply.setStatus(ProfileApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        try {
            return profileApplyMapper.insert(apply) == 1;
        } catch (DuplicateKeyException e) {
            // uk_pending(user_id, type, pending_flag)：同一人同一类已经有一条待审
            log.info("profile apply already pending: userId={}, type={}", userId, type);
            return false;
        }
    }

    @Override
    public void cancelPending(int userId, int type) {
        profileApplyMapper.delete(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)
                .eq("type", type)
                .eq("status", ProfileApply.STATUS_PENDING));
    }
}
