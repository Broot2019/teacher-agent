package com.teacheragent.service.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用题目模型
 */
@Data
public class Question {

    /** 题型: single/multi/judge/program 内部用，输出时按映射规则转换 */
    private String type;

    /** 题干 */
    private String stem = "";

    /** 知识点 */
    private String knowledge = "";

    /** 难易度: 简单/一般/困难 */
    private String difficulty = "一般";

    /** 答案（单选: "A"; 多选: "A,B"; 判断: "正确"/"错误"; 编程: 完整答案文本） */
    private String answer = "";

    /** 解析 */
    private String explanation = "";

    /** 选项（单选/多选用，每个选项不带前缀字母） */
    private List<String> options = new ArrayList<>();
}
