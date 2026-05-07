package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.InvitationCode;
import com.teacheragent.mapper.InvitationCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationCodeService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final InvitationCodeMapper invitationCodeMapper;
    private final SystemConfigService systemConfigService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InvitationCode create(Integer countDays, Integer initialPoints, Integer initialQuota, String note) {
        InvitationCode c = new InvitationCode();
        c.setCode(generateCode());
        c.setInitialPoints(initialPoints == null ? systemConfigService.getInt("register_initial_points", 1000) : initialPoints);
        c.setInitialQuota(initialQuota == null ? 100 : initialQuota);
        c.setCreatedBy(CurrentUserHolder.currentId());
        c.setExpireTime(LocalDateTime.now().plusDays(countDays == null ? 30 : countDays));
        c.setNote(note);
        c.setStatus("unused");
        invitationCodeMapper.insert(c);
        return c;
    }

    public List<InvitationCode> list(int limit) {
        return invitationCodeMapper.selectList(new LambdaQueryWrapper<InvitationCode>()
                .orderByDesc(InvitationCode::getCreateTime).last("LIMIT " + Math.min(limit, 500)));
    }

    @Transactional
    public InvitationCode use(String code, Long userId) {
        if (code == null || code.isBlank()) throw new BusinessException("请输入邀请码");
        InvitationCode c = invitationCodeMapper.selectOne(new LambdaQueryWrapper<InvitationCode>().eq(InvitationCode::getCode, code.trim()).last("LIMIT 1"));
        if (c == null) throw new BusinessException("邀请码不存在");
        if (!"unused".equals(c.getStatus())) throw new BusinessException("邀请码已使用或不可用");
        if (c.getExpireTime() != null && c.getExpireTime().isBefore(LocalDateTime.now())) {
            c.setStatus("expired");
            invitationCodeMapper.updateById(c);
            throw new BusinessException("邀请码已过期");
        }
        c.setStatus("used");
        c.setUsedBy(userId);
        c.setUsedTime(LocalDateTime.now());
        invitationCodeMapper.updateById(c);
        return c;
    }

    @Transactional
    public void disable(Long id) {
        InvitationCode c = invitationCodeMapper.selectById(id);
        if (c == null) return;
        if ("used".equals(c.getStatus())) throw new BusinessException("已使用的邀请码不能禁用");
        c.setStatus("disabled");
        invitationCodeMapper.updateById(c);
    }

    public void updateUsedBy(InvitationCode c) {
        invitationCodeMapper.updateById(c);
    }

    private String generateCode() {
        for (int tries = 0; tries < 20; tries++) {
            StringBuilder sb = new StringBuilder("TA-");
            for (int i = 0; i < 8; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            String code = sb.toString();
            Long exists = invitationCodeMapper.selectCount(new LambdaQueryWrapper<InvitationCode>().eq(InvitationCode::getCode, code));
            if (exists == 0) return code;
        }
        throw new BusinessException("生成邀请码失败，请重试");
    }
}
