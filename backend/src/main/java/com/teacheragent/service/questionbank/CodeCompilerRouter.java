package com.teacheragent.service.questionbank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 编程题代码校验路由：根据 language 参数分发到不同语言的校验实现。
 *
 * <p>当前实现策略：
 * <ul>
 *     <li>{@code java}：走完整的 {@link JavaCompilerService}（编译 + 运行）</li>
 *     <li>其他语言：走"轻量代码检测"（长度合理 / 不为空 / 不含明显的 LLM 拒答模式）</li>
 * </ul>
 *
 * <p>未来扩展：可按需补 PythonRunner（用 ProcessBuilder("python3", file)）、
 * NodeRunner、GccRunner 等，对其他主流语言也支持完整运行校验。
 *
 * <p>设计取舍：教师助手系统部署在教学机房，强制要求所有语言运行环境齐全不现实；
 * 同时多数教师只关心"代码格式合理 + 答案逻辑可读"，未必要严格运行通过。
 * 当前实现保证 Java 完整可用、其他语言基础格式过滤，平衡可用性与精度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeCompilerRouter {

    private final JavaCompilerService javaCompilerService;

    /**
     * 编译校验：返回是否通过。
     * Java 走完整编译；其他语言走轻量格式检查。
     */
    public boolean tryCompile(String code, String language) {
        if (code == null || code.isBlank()) return false;
        if (isJava(language)) {
            return javaCompilerService.tryCompile(code);
        }
        return lightCheck(code, language);
    }

    /**
     * 运行校验：仅 Java 实际运行；其他语言走代码格式检查（不实际运行）。
     */
    public boolean tryRunWithStdin(String code, String stdin, String expectedStdout, long timeoutMs, String language) {
        if (code == null || code.isBlank()) return false;
        if (isJava(language)) {
            return javaCompilerService.tryRunWithStdin(code, stdin, expectedStdout, timeoutMs);
        }
        // 非 Java 语言暂不真实运行，仅做轻量检查
        return lightCheck(code, language);
    }

    private boolean isJava(String language) {
        return language == null || "java".equalsIgnoreCase(language);
    }

    /**
     * 轻量代码检查：
     * - 长度 ≥ 30 字符（过滤空答案 / "对不起，我无法..." 等 LLM 拒答）
     * - 包含基本代码符号（{ } ; : def function 等任一）
     * - 不以中文标点开头（避免 LLM 把题干当代码返回）
     */
    private boolean lightCheck(String code, String language) {
        String c = code.trim();
        if (c.length() < 30) {
            log.debug("[{}] 代码过短，校验失败 length={}", language, c.length());
            return false;
        }
        // 拒答模式
        String lower = c.toLowerCase();
        if (lower.contains("对不起") || lower.contains("无法生成") || lower.contains("i can't") || lower.contains("i cannot")) {
            log.debug("[{}] 检测到 LLM 拒答模式", language);
            return false;
        }
        // 必须含基本代码符号之一（覆盖几乎所有主流语言）
        boolean hasCodeSymbol = c.contains("{") || c.contains(";") || c.contains("def ") || c.contains("function ")
                || c.contains("func ") || c.contains("fn ") || c.contains("class ")
                || c.contains("import ") || c.contains("#include") || c.contains("using ")
                || c.contains("=>") || c.contains("=") || c.contains("SELECT ") || c.contains("select ");
        if (!hasCodeSymbol) {
            log.debug("[{}] 未检测到任何代码符号，疑似纯文字", language);
            return false;
        }
        return true;
    }
}
