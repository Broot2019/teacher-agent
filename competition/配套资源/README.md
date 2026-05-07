# 教师助手系统 (Teacher Agent)

AI赋能高职教学资源智能生成与知识管理系统。基于国产大模型，实现教案自动生成、题库智能出题、RAG知识库检索和智能答疑，帮助教师大幅提升教学资源准备效率。

## 功能特性

### 核心功能
- **教案智能生成**：上传 PPT/教材素材，AI 自动生成符合教学规范的 Word 教案（支持基础版/标准版/详尽版/特详版四个等级）
- **题库智能生成**：基于章节素材生成单选、多选、判断、编程四类题目，支持 Excel 导出
- **RAG 知识库**：上传教材/教案建立检索索引，生成时自动检索补充内容
- **智能答疑助手**：基于知识库的 AI 问答，支持教师备课辅助和学生答疑
- **AI 辅助审阅**：对已生成的教案/题库进行 AI 专业评审
- **课程思政系统融入**：教案自动包含思政目标与融合点（科学精神/工匠精神/信息素养等）

### 质量保障
- **两阶段生成策略**：先做知识点规划（避免多份教案内容重复），再逐份生成
- **闭环质量保障**：生成 → 合规校验 → 二次自检 → 重生机制
- **编程题编译校验**：生成的 Java 代码通过实际编译验证

### 管理功能
- 多国产大模型支持（DeepSeek / 智谱清言 / 通义千问 / Kimi / MiniMax）
- 课程配置管理（不同课程独立配置，解除 Java 硬编码绑定）
- 用户管理、积分系统、邀请码
- 仪表盘数据可视化（ECharts）、审计日志

## 技术架构

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + ECharts + Vite |
| 后端 | Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 |
| AI | 国产大模型 API（OpenAI 兼容协议） |
| 文档处理 | Apache POI + poi-tl + PDFBox |

## 快速开始

### 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0+
- 至少一个大模型 API Key（推荐 DeepSeek 或智谱清言）

### 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |

首次登录后请立即修改密码。

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS teacher_agent DEFAULT CHARACTER SET utf8mb4;
```

```bash
mysql -uroot -p123456 < init.sql
mysql -uroot -p123456 < init_v2.sql
mysql -uroot -p123456 < backend/src/main/resources/db/migration/V2__course_config.sql
mysql -uroot -p123456 < backend/src/main/resources/db/migration/V3__knowledge_base.sql
```

### 2. 后端启动

```bash
cd backend
# 修改 application.yml 中的数据库密码和端口
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8089`

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

### 4. 一键启动（Windows）

双击 `start.bat`，浏览器访问 `http://localhost:8089`

### 5. 首次使用

1. 登录管理员账号
2. 进入「模型配置」页面，添加至少一个大模型配置并激活
3. 进入「课程配置」页面，配置您教授的课程信息
4. 开始使用教案生成或题库生成功能

## 使用指南

### 教案生成
1. 进入「教案生成」页面
2. 上传 PPT/PDF 素材文件
3. 选择生成模式（单章节 / 按周次范围 / 按文件配置）
4. 选择内容等级（基础版/标准版/详尽版/特详版）和模板
5. 提交任务，在「任务中心」查看进度
6. 生成完成后下载 Word 文档

### 题库生成
1. 进入「题库生成」页面
2. 上传章节素材文件
3. 选择题型和数量
4. 设置难度倾向
5. 生成后下载 Excel 题库

### 知识库使用
1. 在「知识库」页面上传教材、教案等参考资料
2. 系统自动建立检索索引
3. 在「智能答疑」页面可以基于知识库进行问答

## 项目结构

```
teacher-agent/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/teacheragent/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # 控制器
│   │   ├── dto/               # 数据传输对象
│   │   ├── entity/            # 数据实体
│   │   ├── mapper/            # MyBatis Mapper
│   │   └── service/           # 业务逻辑
│   │       ├── lessonplan/    # 教案生成
│   │       ├── questionbank/  # 题库生成
│   │       └── llm/           # 大模型客户端
│   └── src/main/resources/
│       ├── db/migration/      # 数据库迁移脚本
│       └── reference/         # 教案模板、题库模板
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── api/               # API 接口
│       ├── views/             # 页面组件
│       └── stores/            # Pinia 状态
└── docs/                       # 文档
```

## API 接口列表

### 认证
- `POST /api/auth/login` - 登录
- `POST /api/auth/register` - 注册
- `GET /api/auth/me` - 当前用户
- `POST /api/auth/change-password` - 修改密码

### 教案/题库
- `POST /api/lesson-plan/generate` - 提交教案任务
- `POST /api/question-bank/generate` - 提交题库任务
- `GET /api/lesson-plan/history` - 教案历史
- `GET /api/question-bank/history` - 题库历史

### 课程配置
- `GET /api/course-config/list` - 课程列表
- `POST /api/course-config/save` - 保存课程
- `POST /api/course-config/activate/{id}` - 激活课程

### 知识库
- `GET /api/knowledge-base/list` - 知识库列表
- `POST /api/knowledge-base/upload` - 上传资料
- `DELETE /api/knowledge-base/{id}` - 删除

### 智能答疑
- `POST /api/qa/ask` - 提问

### AI审阅
- `POST /api/review/lesson-plan` - 教案评审
- `POST /api/review/question-bank` - 题库评审

## 创新亮点

1. **两阶段知识点规划**：先生成知识点分布规划（避免重复），再逐份生成教案
2. **闭环质量保障**：合规校验 → 二次自检 → 自动重生机制
3. **编程题编译校验**：AI 生成的代码经过实际编译验证
4. **课程思政系统化**：教案自动融入思政元素
5. **RAG 知识库增强**：基于检索增强生成，提升内容准确性
6. **多模型自适应**：支持多个国产大模型切换
7. **课程配置解耦**：支持任意课程，不绑定特定学科

## 安全说明

- JWT 密钥在 `application.yml` 的 `jwt.secret`，生产环境请修改
- 默认 admin 密码 123456，请立即修改
- 教师只能看到/下载自己生成的文件
- 接口走 `Authorization: Bearer <token>` 头校验

## 许可证

Apache License 2.0

## 致谢

本项目开发过程中大量使用 AI 辅助编程，体现了"AI 赋能开发"的理念。
