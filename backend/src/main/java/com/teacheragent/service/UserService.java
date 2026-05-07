package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUser;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.dto.*;
import com.teacheragent.entity.InvitationCode;
import com.teacheragent.entity.User;
import com.teacheragent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final InvitationCodeService invitationCodeService;
    private final PointService pointService;
    private final SystemConfigService systemConfigService;
    private final CaptchaService captchaService;

    public LoginResponse login(LoginRequest req) {
        // 1. 验证码先行校验：避免暴力破解
        if (req.getCaptchaKey() == null || req.getCaptchaKey().isBlank()
                || req.getCaptchaCode() == null || req.getCaptchaCode().isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }
        if (!captchaService.verifyOnce(req.getCaptchaKey(), req.getCaptchaCode())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        User u = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (u == null) throw new BusinessException(401, "用户名或密码错误");
        if (!"enabled".equals(u.getStatus())) throw new BusinessException(403, "账号已被禁用");
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        u.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(u);
        String token = jwtService.generate(u.getId(), u.getUsername(), u.getRole());
        return new LoginResponse(token, u.getId(), u.getUsername(), u.getRole(), u.getRealName());
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exist != null) throw new BusinessException("用户名已存在");

        int registerOpen = systemConfigService.getInt("register_open", 1);
        if (registerOpen == 0) {
            throw new BusinessException("当前系统注册已关闭，请联系管理员");
        }

        int initialPoints = systemConfigService.getInt("register_initial_points", 1000);
        int initialQuota = systemConfigService.getInt("default_monthly_quota", 100);

        if (registerOpen == 1) {
            if (req.getInvitationCode() == null || req.getInvitationCode().isBlank()) {
                throw new BusinessException("请输入邀请码");
            }
            InvitationCode code = invitationCodeService.use(req.getInvitationCode(), null);
            if (code.getInitialPoints() != null) initialPoints = code.getInitialPoints();
            if (code.getInitialQuota() != null) initialQuota = code.getInitialQuota();

            User u = createUser(req, initialPoints, initialQuota);
            code.setUsedBy(u.getId());
            invitationCodeService.updateUsedBy(code);
            return buildLoginResponse(u);
        }

        // registerOpen == 2: 开放注册，无需邀请码
        User u = createUser(req, initialPoints, initialQuota);
        return buildLoginResponse(u);
    }

    private User createUser(RegisterRequest req, int initialPoints, int initialQuota) {
        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRole("teacher");
        u.setEmail(req.getEmail());
        u.setRealName(req.getRealName());
        u.setStatus("enabled");
        u.setPoints(initialPoints);
        u.setMonthlyQuota(initialQuota);
        userMapper.insert(u);
        return u;
    }

    private LoginResponse buildLoginResponse(User u) {
        String token = jwtService.generate(u.getId(), u.getUsername(), u.getRole());
        return new LoginResponse(token, u.getId(), u.getUsername(), u.getRole(), u.getRealName());
    }

    public User getCurrent() {
        CurrentUser cu = CurrentUserHolder.get();
        if (cu == null) return null;
        User u = userMapper.selectById(cu.getId());
        if (u != null) u.setPasswordHash(null);
        return u;
    }

    @Transactional
    public void changePassword(String oldPwd, String newPwd) {
        Long uid = CurrentUserHolder.currentId();
        if (uid == null) throw new BusinessException(401, "未登录");
        User u = userMapper.selectById(uid);
        if (u == null) throw new BusinessException("用户不存在");
        if (!passwordEncoder.matches(oldPwd, u.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        u.setPasswordHash(passwordEncoder.encode(newPwd));
        userMapper.updateById(u);
    }

    // ===== 管理员接口 =====

    public List<User> list() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        users.forEach(u -> u.setPasswordHash(null));
        return users;
    }

    @Transactional
    public User saveByAdmin(UserSaveRequest req) {
        User u;
        if (req.getId() == null) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
            if (exist != null) throw new BusinessException("用户名已存在");
            u = new User();
            u.setUsername(req.getUsername().trim());
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                throw new BusinessException("新增用户必须设置密码");
            }
            u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            u.setRole(req.getRole() == null ? "teacher" : req.getRole());
            u.setStatus(req.getStatus() == null ? "enabled" : req.getStatus());
            u.setEmail(req.getEmail());
            u.setRealName(req.getRealName());
            u.setMonthlyQuota(req.getMonthlyQuota() == null ? 100 : req.getMonthlyQuota());
            u.setPoints(req.getPoints() == null ? systemConfigService.getInt("register_initial_points", 1000) : req.getPoints());
            userMapper.insert(u);
        } else {
            u = userMapper.selectById(req.getId());
            if (u == null) throw new BusinessException("用户不存在");
            if (req.getRole() != null) u.setRole(req.getRole());
            if (req.getEmail() != null) u.setEmail(req.getEmail());
            if (req.getRealName() != null) u.setRealName(req.getRealName());
            if (req.getStatus() != null) u.setStatus(req.getStatus());
            if (req.getMonthlyQuota() != null) u.setMonthlyQuota(req.getMonthlyQuota());
            if (req.getPoints() != null) u.setPoints(req.getPoints());
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            }
            userMapper.updateById(u);
        }
        u.setPasswordHash(null);
        return u;
    }

    @Transactional
    public void toggleStatus(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        if (Long.valueOf(1L).equals(id)) throw new BusinessException("不能禁用主管理员");
        u.setStatus("enabled".equals(u.getStatus()) ? "disabled" : "enabled");
        userMapper.updateById(u);
    }

    @Transactional
    public String resetPassword(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        String newPwd = "Pass" + (10000 + (int)(Math.random() * 89999));
        u.setPasswordHash(passwordEncoder.encode(newPwd));
        userMapper.updateById(u);
        return newPwd;
    }

    @Transactional
    public void delete(Long id) {
        if (Long.valueOf(1L).equals(id)) throw new BusinessException("不能删除主管理员");
        userMapper.deleteById(id);
    }
}
