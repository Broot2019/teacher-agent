package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.entity.QuestionBankHistory;
import com.teacheragent.entity.QuestionItem;
import com.teacheragent.service.QuestionItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/question-item")
@RequiredArgsConstructor
public class QuestionItemController {

    private final QuestionItemService questionItemService;

    @GetMapping("/list")
    public R<List<QuestionItem>> list(@RequestParam Long bankId) {
        return R.ok(questionItemService.listByBank(bankId));
    }

    @PostMapping("/save")
    public R<QuestionItem> save(@RequestBody QuestionItem item) {
        return R.ok(questionItemService.save(item), "保存成功");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        questionItemService.delete(id);
        return R.ok(null, "已删除");
    }

    @PostMapping("/regenerate/{bankId}")
    public R<QuestionBankHistory> regenerate(@PathVariable Long bankId) {
        return R.ok(questionItemService.regenerateXlsx(bankId), "已重新导出 Excel");
    }
}
