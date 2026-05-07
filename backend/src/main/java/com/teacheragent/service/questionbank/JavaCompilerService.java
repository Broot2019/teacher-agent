package com.teacheragent.service.questionbank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 编程题代码编译校验（仅 compile，不执行）。
 * 用于过滤 LLM 生成的语法错误代码，提高编程题质量。
 */
@Slf4j
@Service
public class JavaCompilerService {

    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern ANY_CLASS = Pattern.compile("class\\s+(\\w+)");

    /**
     * 从题干中提取样例输入输出。
     *
     * <p>识别多种常见格式（教师素材里 LLM 生成的题干风格不一）：
     * <ul>
     *     <li>"样例输入：xxx 样例输出：yyy"</li>
     *     <li>"输入示例：xxx 输出示例：yyy"</li>
     *     <li>"输入：xxx 输出：yyy"</li>
     *     <li>"Sample Input: xxx Sample Output: yyy"</li>
     *     <li>"Input: xxx Output: yyy"</li>
     * </ul>
     *
     * <p>提取到 input/output 后再做剥壳处理：去掉前后空白、代码块标记 ``` 等。
     * 提取失败返回 null。
     */
    public static SampleIo extractSampleIo(String stem) {
        if (stem == null || stem.isBlank()) return null;
        // 把转义换行还原
        String s = stem.replace("\\n", "\n");

        // 一组 (input 标记, output 标记) 候选；先匹配越靠前的越优先
        String[][] markers = {
                {"样例输入", "样例输出"},
                {"输入示例", "输出示例"},
                {"示例输入", "示例输出"},
                {"Sample Input", "Sample Output"},
                {"Sample input", "Sample output"},
                {"Input", "Output"},
                {"输入", "输出"},
        };

        for (String[] pair : markers) {
            String input = extractBetween(s, pair[0], pair[1]);
            if (input == null) continue;
            // 找 output 标记之后的内容
            int outputStart = s.indexOf(pair[1]);
            if (outputStart < 0) continue;
            String afterOutput = s.substring(outputStart + pair[1].length());
            String output = trimSampleBlock(afterOutput, pair);
            if (output == null || output.isBlank()) continue;
            return new SampleIo(input, output);
        }
        return null;
    }

    /** 在 stem 中找 startMarker 与 endMarker 之间的文本（去掉冒号与空白） */
    private static String extractBetween(String s, String startMarker, String endMarker) {
        int start = s.indexOf(startMarker);
        if (start < 0) return null;
        int end = s.indexOf(endMarker, start + startMarker.length());
        if (end < 0) return null;
        String segment = s.substring(start + startMarker.length(), end);
        return trimSampleBlock(segment, null);
    }

    /** 去掉冒号、code block 标记、首尾空白 */
    private static String trimSampleBlock(String s, String[] stopMarkers) {
        if (s == null) return null;
        // 截到下一个 stop marker（避免吃过界）
        if (stopMarkers != null) {
            for (String m : stopMarkers) {
                int p = s.indexOf(m);
                if (p > 0) s = s.substring(0, p);
            }
        }
        // 去常见的引导字符
        s = s.replaceAll("^[:：]+\\s*", "");
        // 去 code block
        s = s.replaceAll("```\\w*", "").replaceAll("```", "");
        return s.trim();
    }

    /** 样例输入输出对 */
    public record SampleIo(String input, String output) {}

    /**
     * 尝试编译给定的 Java 源代码。
     * @return 是否编译通过；编译失败时仅记录 warn 日志
     */
    public boolean tryCompile(String javaSource) {
        if (javaSource == null || javaSource.isBlank()) return false;
        // LLM 可能输出 \n 字符串而非真换行；统一为真换行
        String src = javaSource.replace("\\n", "\n").replace("\\\"", "\"");

        // 提取类名（优先 public class）
        String className = extractClassName(src);
        if (className == null) {
            log.warn("代码未识别到 class 声明，跳过编译: {}", preview(src));
            return false;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.warn("当前 JDK 不含 JavaCompiler（可能是 JRE），跳过编译校验");
            return true;  // 无法校验则放行（不应误杀）
        }

        ByteArrayOutputStream errOut = new ByteArrayOutputStream();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavaFileObject source = new InMemoryJavaFile(className, src);
            List<JavaFileObject> compilationUnits = Collections.singletonList(source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    new OutputStreamWriter(errOut, StandardCharsets.UTF_8),
                    fm, diagnostics,
                    List.of("-d", System.getProperty("java.io.tmpdir") + "/teacher-agent-compile",
                            "-nowarn"),
                    null,
                    compilationUnits);
            Boolean ok = task.call();
            if (ok == null || !ok) {
                StringBuilder errs = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        errs.append(d.getMessage(null)).append("; ");
                    }
                }
                log.warn("编程题代码编译失败 className={} errs={}", className, errs);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("编译过程异常，跳过此题: {}", e.getMessage());
            return false;
        }
    }

    private String extractClassName(String src) {
        Matcher m = PUBLIC_CLASS.matcher(src);
        if (m.find()) return m.group(1);
        m = ANY_CLASS.matcher(src);
        if (m.find()) return m.group(1);
        return null;
    }

    private String preview(String s) {
        s = s.replace("\n", " ");
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    /**
     * 编译 + 运行 + 输出比对（用于编程题运行校验）。
     *
     * <p>把源代码 写到临时文件 → javac 编译 → java 子进程跑 → 把 stdin 喂给子进程 →
     * 拿子进程的 stdout，与 expectedStdout 做"宽松比对"（trim、去空白行、忽略行尾空格）。
     *
     * <p>容错：编译失败 / 运行超时 / 异常退出 / 输出不一致 → 返回 false。
     * 不会抛异常（避免影响题目生成主流程）。
     *
     * @param javaSource     完整 Java 源代码（含 class 与 main）
     * @param stdin          喂给子进程的标准输入（可空）
     * @param expectedStdout 期望的标准输出（trim 后比对，可空表示只要求运行不出错）
     * @param timeoutMs      运行超时毫秒数（建议 3000-5000）
     * @return 是否运行成功且输出匹配
     */
    public boolean tryRunWithStdin(String javaSource, String stdin, String expectedStdout, long timeoutMs) {
        if (javaSource == null || javaSource.isBlank()) return false;
        String src = javaSource.replace("\\n", "\n").replace("\\\"", "\"");
        String className = extractClassName(src);
        if (className == null) {
            log.warn("代码未识别到 class 声明，跳过运行: {}", preview(src));
            return false;
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("teacher-agent-run-");
            Path srcFile = workDir.resolve(className + ".java");
            Files.writeString(srcFile, src, StandardCharsets.UTF_8);

            // 1. 编译到 workDir
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                log.warn("当前 JDK 不含 JavaCompiler，跳过运行校验");
                return true; // 无法校验则放行
            }
            int rc = compiler.run(null, null, new ByteArrayOutputStream(),
                    "-d", workDir.toString(), srcFile.toString());
            if (rc != 0) {
                log.warn("[{}] 运行校验编译失败", className);
                return false;
            }

            // 2. 运行子进程
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-cp", workDir.toString(), className);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            // 喂 stdin
            if (stdin != null && !stdin.isEmpty()) {
                try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                        proc.getOutputStream(), StandardCharsets.UTF_8))) {
                    pw.print(stdin);
                    pw.flush();
                }
            } else {
                proc.getOutputStream().close();
            }

            // 异步读 stdout（避免缓冲区满导致死锁）
            StringBuilder stdout = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (stdout) {
                            stdout.append(line).append('\n');
                        }
                    }
                } catch (IOException ignored) {}
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                log.warn("[{}] 运行超时（{}ms）", className, timeoutMs);
                return false;
            }
            reader.join(500);
            if (proc.exitValue() != 0) {
                log.warn("[{}] 运行异常退出 code={} 输出={}",
                        className, proc.exitValue(), preview(stdout.toString()));
                return false;
            }

            // 3. 输出比对（如果有期望值）
            if (expectedStdout != null && !expectedStdout.isBlank()) {
                String actual = normalizeStdout(stdout.toString());
                String expected = normalizeStdout(expectedStdout);
                if (!actual.equals(expected)) {
                    log.warn("[{}] 输出不匹配 expected=[{}] actual=[{}]",
                            className, preview(expected), preview(actual));
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("运行校验异常: {}", e.getMessage());
            return false;
        } finally {
            if (workDir != null) cleanupDir(workDir.toFile());
        }
    }

    /** stdout 规范化：trim 每行 + 去空白行 + 全 trim */
    private static String normalizeStdout(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\\r?\\n")) {
            String t = line.strip();
            if (!t.isEmpty()) sb.append(t).append('\n');
        }
        return sb.toString().strip();
    }

    private static void cleanupDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) cleanupDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    /** 内存 JavaFileObject — 把字符串源码作为编译单元 */
    private static class InMemoryJavaFile extends SimpleJavaFileObject {
        private final String code;
        InMemoryJavaFile(String className, String code) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
