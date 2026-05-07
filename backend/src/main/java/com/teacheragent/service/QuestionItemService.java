package com.teacheragent.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.QuestionBankHistory;
import com.teacheragent.entity.QuestionItem;
import com.teacheragent.mapper.QuestionBankHistoryMapper;
import com.teacheragent.mapper.QuestionItemMapper;
import com.teacheragent.service.questionbank.Question;
import com.teacheragent.service.questionbank.QuestionBankXlsxFiller;
import com.teacheragent.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionItemService {

    private final QuestionItemMapper questionItemMapper;
    private final QuestionBankHistoryMapper bankMapper;
    private final QuestionBankXlsxFiller filler;
    private final AppProperties props;

    public List<QuestionItem> listByBank(Long bankId) {
        // 数据隔离
        QuestionBankHistory bank = bankMapper.selectById(bankId);
        if (bank == null) throw new BusinessException("题库不存在");
        if (!CurrentUserHolder.isAdmin() && !bank.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权访问该题库");
        }
        return questionItemMapper.selectList(
                new LambdaQueryWrapper<QuestionItem>()
                        .eq(QuestionItem::getBankId, bankId)
                        .orderByAsc(QuestionItem::getSortOrder)
                        .orderByAsc(QuestionItem::getId));
    }

    @Transactional
    public QuestionItem save(QuestionItem item) {
        if (item.getBankId() != null) {
            QuestionBankHistory bank = bankMapper.selectById(item.getBankId());
            if (bank != null && !CurrentUserHolder.isAdmin() && !bank.getOwnerId().equals(CurrentUserHolder.currentId())) {
                throw new BusinessException(403, "无权操作该题库");
            }
        }
        if (item.getOwnerId() == null) item.setOwnerId(CurrentUserHolder.currentId());
        if (item.getId() == null) {
            questionItemMapper.insert(item);
        } else {
            QuestionItem old = questionItemMapper.selectById(item.getId());
            if (old == null) throw new BusinessException("题目不存在");
            if (!CurrentUserHolder.isAdmin() && !old.getOwnerId().equals(CurrentUserHolder.currentId())) {
                throw new BusinessException(403, "无权编辑该题目");
            }
            questionItemMapper.updateById(item);
        }
        return item;
    }

    @Transactional
    public void delete(Long id) {
        QuestionItem old = questionItemMapper.selectById(id);
        if (old == null) return;
        if (!CurrentUserHolder.isAdmin() && !old.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权删除该题目");
        }
        questionItemMapper.deleteById(id);
    }

    /** 重新导出题库（基于当前题目列表生成新的 xlsx，覆盖原文件） */
    @Transactional
    public QuestionBankHistory regenerateXlsx(Long bankId) {
        QuestionBankHistory bank = bankMapper.selectById(bankId);
        if (bank == null) throw new BusinessException("题库不存在");
        if (!CurrentUserHolder.isAdmin() && !bank.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权操作");
        }
        List<QuestionItem> items = questionItemMapper.selectList(
                new LambdaQueryWrapper<QuestionItem>().eq(QuestionItem::getBankId, bankId).orderByAsc(QuestionItem::getSortOrder));
        if (items.isEmpty()) throw new BusinessException("题库中没有题目");

        List<Question> questions = new ArrayList<>();
        for (QuestionItem qi : items) {
            Question q = new Question();
            q.setType(qi.getType());
            q.setStem(qi.getStem());
            q.setKnowledge(qi.getKnowledge());
            q.setDifficulty(qi.getDifficulty());
            q.setAnswer(qi.getAnswer());
            q.setExplanation(qi.getExplanation());
            if (qi.getOptionsJson() != null && !qi.getOptionsJson().isBlank()) {
                try {
                    q.setOptions(JSON.parseArray(qi.getOptionsJson(), String.class));
                } catch (Exception ignored) { }
            }
            questions.add(q);
        }

        try (InputStream is = new ClassPathResource("reference/题目导入模板.xlsx").getInputStream()) {
            byte[] bytes = filler.fill(is, questions);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeChapter = (bank.getChapter() == null ? "题库" : bank.getChapter()).replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = String.format("题库_%s_%d题_v%s.xlsx", safeChapter, questions.size(), stamp);
            Path outPath = Paths.get(props.getOutputDir(), fileName);
            Files.write(outPath, bytes);

            bank.setOutputFilePath(outPath.toAbsolutePath().toString());
            bank.setOutputFileName(fileName);
            bank.setTotalCount(questions.size());
            bankMapper.updateById(bank);
        } catch (Exception e) {
            log.error("重新导出失败", e);
            throw new BusinessException("重新导出失败: " + e.getMessage());
        }
        return bank;
    }
}
