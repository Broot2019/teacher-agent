package com.teacheragent.service.lessonplan;

import lombok.Data;

@Data
public class TimeSlot {
    /** 时间分配，如 "0-15min" 或 "0-15分钟" */
    private String time;
    /** 教师活动 */
    private String teacherAction;
    /** 学生活动 */
    private String studentAction;

    public TimeSlot() {}

    public TimeSlot(String time, String teacherAction, String studentAction) {
        this.time = time;
        this.teacherAction = teacherAction;
        this.studentAction = studentAction;
    }
}
