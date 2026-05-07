package com.teacheragent.aspect;

import com.alibaba.fastjson2.JSON;
import com.teacheragent.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 关键操作审计日志切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Pointcut("execution(* com.teacheragent.controller.AuthController.login(..))")
    public void loginPoint() {}

    @Pointcut("execution(* com.teacheragent.controller.UserController.*(..))")
    public void userMgmtPoint() {}

    @Pointcut("execution(* com.teacheragent.controller.LessonPlanController.generate(..))")
    public void lessonGenPoint() {}

    @Pointcut("execution(* com.teacheragent.controller.QuestionBankController.generate(..))")
    public void questionGenPoint() {}

    @AfterReturning(pointcut = "loginPoint()", returning = "result")
    public void afterLogin(JoinPoint jp, Object result) {
        try {
            Object[] args = jp.getArgs();
            String username = "";
            if (args != null && args.length > 0 && args[0] != null) {
                Object o = args[0];
                username = (String) o.getClass().getMethod("getUsername").invoke(o);
            }
            auditLogService.logSuccess("LOGIN", "user", username, "用户登录: " + username);
        } catch (Exception ignored) { }
    }

    @AfterThrowing(pointcut = "loginPoint()", throwing = "ex")
    public void afterLoginFail(JoinPoint jp, Throwable ex) {
        try {
            Object[] args = jp.getArgs();
            String username = "";
            if (args != null && args.length > 0 && args[0] != null) {
                Object o = args[0];
                username = (String) o.getClass().getMethod("getUsername").invoke(o);
            }
            auditLogService.logFailure("LOGIN", "user", username, ex.getMessage());
        } catch (Exception ignored) { }
    }

    @AfterReturning(pointcut = "userMgmtPoint()", returning = "result")
    public void afterUserMgmt(JoinPoint jp, Object result) {
        try {
            String method = jp.getSignature().getName();
            auditLogService.logSuccess("USER_" + method.toUpperCase(), "user", null, "用户管理: " + method);
        } catch (Exception ignored) { }
    }

    @AfterReturning(pointcut = "lessonGenPoint() || questionGenPoint()", returning = "result")
    public void afterGenerate(JoinPoint jp, Object result) {
        try {
            String type = jp.getSignature().getDeclaringTypeName().contains("Lesson") ? "LESSON_GEN" : "QUESTION_GEN";
            auditLogService.logSuccess(type, "task", null, "生成任务提交");
        } catch (Exception ignored) { }
    }
}
