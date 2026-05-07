package com.teacheragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.config.AppProperties;
import com.teacheragent.entity.ChapterMaterial;
import com.teacheragent.mapper.ChapterMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterMaterialService {

    private final ChapterMaterialMapper materialMapper;
    private final AppProperties props;

    public List<ChapterMaterial> list(String chapter, String course) {
        LambdaQueryWrapper<ChapterMaterial> q = new LambdaQueryWrapper<>();
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null) {
            q.and(qq -> qq.eq(ChapterMaterial::getOwnerId, CurrentUserHolder.currentId())
                    .or().eq(ChapterMaterial::getIsPublic, 1));
        }
        if (chapter != null && !chapter.isBlank()) q.like(ChapterMaterial::getChapter, chapter);
        if (course != null && !course.isBlank()) q.eq(ChapterMaterial::getCourse, course);
        q.orderByDesc(ChapterMaterial::getCreateTime);
        return materialMapper.selectList(q);
    }

    @Transactional
    public ChapterMaterial upload(MultipartFile file, String chapter, String course, String description, Integer isPublic) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择文件");
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");

        try {
            String dir = props.getDataDir() + "/materials";
            File d = new File(dir);
            if (!d.exists()) d.mkdirs();
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = stamp + "_" + safeName;
            Path target = Paths.get(dir, fileName);
            Files.write(target, file.getBytes());

            ChapterMaterial m = new ChapterMaterial();
            m.setChapter(chapter);
            m.setFileName(file.getOriginalFilename());
            m.setFilePath(target.toAbsolutePath().toString());
            m.setFileSize(file.getSize());
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String type = name.endsWith(".pptx") ? "pptx" : name.endsWith(".ppt") ? "ppt"
                    : name.endsWith(".pdf") ? "pdf" : name.endsWith(".docx") ? "docx" : "other";
            m.setFileType(type);
            m.setCourse(course == null ? "Java" : course);
            m.setDescription(description);
            m.setOwnerId(ownerId);
            m.setIsPublic(isPublic == null ? 1 : isPublic);
            m.setUseCount(0);
            materialMapper.insert(m);
            return m;
        } catch (IOException e) {
            throw new BusinessException("保存文件失败: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long id) {
        ChapterMaterial m = materialMapper.selectById(id);
        if (m == null) return;
        if (!CurrentUserHolder.isAdmin() && !m.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权删除");
        }
        // 物理文件保留，仅逻辑删除
        materialMapper.deleteById(id);
    }

    public ChapterMaterial getForUse(Long id) {
        ChapterMaterial m = materialMapper.selectById(id);
        if (m == null) throw new BusinessException("资料不存在");
        if (m.getIsPublic() != null && m.getIsPublic() == 1) return m;
        if (CurrentUserHolder.isAdmin() || m.getOwnerId().equals(CurrentUserHolder.currentId())) return m;
        throw new BusinessException(403, "无权使用该资料");
    }

    public void incrementUseCount(Long id) {
        ChapterMaterial m = materialMapper.selectById(id);
        if (m == null) return;
        m.setUseCount((m.getUseCount() == null ? 0 : m.getUseCount()) + 1);
        materialMapper.updateById(m);
    }
}
