package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.dto.CourseConfigSaveRequest;
import com.teacheragent.entity.CourseConfig;
import com.teacheragent.mapper.CourseConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseConfigService {

    private final CourseConfigMapper mapper;

    public List<CourseConfig> listMine() {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        return mapper.selectList(new LambdaQueryWrapper<CourseConfig>()
                .eq(CourseConfig::getOwnerId, userId)
                .orderByDesc(CourseConfig::getIsActive)
                .orderByDesc(CourseConfig::getCreateTime));
    }

    public CourseConfig getActive() {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) return null;
        return mapper.selectOne(new LambdaQueryWrapper<CourseConfig>()
                .eq(CourseConfig::getOwnerId, userId)
                .eq(CourseConfig::getIsActive, 1)
                .last("LIMIT 1"));
    }

    public CourseConfig getById(Long id) {
        CourseConfig c = mapper.selectById(id);
        if (c == null) throw new BusinessException("课程配置不存在");
        Long userId = CurrentUserHolder.currentId();
        if (!CurrentUserHolder.isAdmin() && userId != null && !userId.equals(c.getOwnerId())) {
            throw new BusinessException(403, "无权操作");
        }
        return c;
    }

    @Transactional
    public CourseConfig save(CourseConfigSaveRequest req) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");

        CourseConfig entity;
        if (req.getId() != null) {
            entity = mapper.selectById(req.getId());
            if (entity == null) throw new BusinessException("课程配置不存在");
            if (!userId.equals(entity.getOwnerId()) && !CurrentUserHolder.isAdmin()) {
                throw new BusinessException(403, "无权操作");
            }
        } else {
            entity = new CourseConfig();
            entity.setOwnerId(userId);
        }

        if (req.getCourseName() != null) entity.setCourseName(req.getCourseName());
        if (req.getMajor() != null) entity.setMajor(req.getMajor());
        if (req.getEducationLevel() != null) entity.setEducationLevel(req.getEducationLevel());
        if (req.getStudentDescription() != null) entity.setStudentDescription(req.getStudentDescription());
        if (req.getTeachingMode() != null) entity.setTeachingMode(req.getTeachingMode());
        if (req.getClassName() != null) entity.setClassName(req.getClassName());
        if (req.getProgrammingLanguage() != null) entity.setProgrammingLanguage(req.getProgrammingLanguage());
        if (entity.getIsActive() == null) entity.setIsActive(0);

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    @Transactional
    public void activate(Long id) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        CourseConfig c = mapper.selectById(id);
        if (c == null) throw new BusinessException("课程配置不存在");
        if (!userId.equals(c.getOwnerId()) && !CurrentUserHolder.isAdmin()) {
            throw new BusinessException(403, "无权操作");
        }
        mapper.update(null, new LambdaUpdateWrapper<CourseConfig>()
                .eq(CourseConfig::getOwnerId, userId)
                .set(CourseConfig::getIsActive, 0));
        mapper.update(null, new LambdaUpdateWrapper<CourseConfig>()
                .eq(CourseConfig::getId, id)
                .set(CourseConfig::getIsActive, 1));
    }

    @Transactional
    public void deleteById(Long id) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        CourseConfig c = mapper.selectById(id);
        if (c == null) throw new BusinessException("课程配置不存在");
        if (!userId.equals(c.getOwnerId()) && !CurrentUserHolder.isAdmin()) {
            throw new BusinessException(403, "无权操作");
        }
        mapper.deleteById(id);
    }
}
