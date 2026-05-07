# 比赛材料 Mermaid 图表代码

本文件包含所有比赛材料所需的Mermaid图表代码。
可以复制到以下平台使用：
- https://mermaid.live/ (在线编辑器，可导出PNG/SVG)
- https://app.diagrams.net/ (draw.io，支持Mermaid)
- https://www.processon.com/ (ProcessOn，支持部分Mermaid)
- Typora / VS Code + Mermaid插件

---

## 1. 系统架构图

### Mermaid代码

```mermaid
graph TB
    subgraph 用户层["用户层"]
        TEACHER["教师用户"]
        ADMIN["管理员"]
        BROWSER["浏览器"]
    end

    subgraph 前端层["前端层 (Vue 3)"]
        LP["教案生成"]
        QB["题库生成"]
        KB["知识库"]
        QA["智能答疑"]
        DASH["仪表盘"]
    end

    subgraph 后端层["后端层 (Spring Boot)"]
        CTRL["Controller"]
        SVC["Service"]
        LLM["LLM Client"]
        RAG["RAG Service"]
        SEC["Security"]
    end

    subgraph 数据层["数据层"]
        MYSQL["MySQL"]
        FILES["知识库文件"]
        HIST["教案/题库历史"]
        USER["用户/权限"]
    end

    subgraph 外部服务["外部服务"]
        DS["DeepSeek"]
        ZP["智谱清言"]
        QW["通义千问"]
        KIMI["Kimi"]
        MM["MiniMax"]
    end

    TEACHER --> BROWSER
    ADMIN --> BROWSER

    BROWSER --> LP & QB & KB & QA & DASH
    LP & QB & KB & QA & DASH --> CTRL
    CTRL --> SVC
    SVC --> LLM & RAG
    LLM & RAG --> DS & ZP & QW & KIMI & MM
    SVC --> MYSQL & FILES & HIST & USER
    SEC --> CTRL

    style 用户层 fill:#e1f5ff,stroke:#01579b,stroke-width:2px
    style 前端层 fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    style 后端层 fill:#fff3e0,stroke:#e65100,stroke-width:2px
    style 数据层 fill:#e8f5e9,stroke:#1b5e20,stroke-width:2px
    style 外部服务 fill:#fce4ec,stroke:#880e4f,stroke-width:2px

    classDef userStyle fill:#4fc3f7,stroke:#0277bd,stroke-width:2px,color:#fff
    classDef frontendStyle fill:#7e57c2,stroke:#512da8,stroke-width:2px,color:#fff
    classDef backendStyle fill:#ffb74d,stroke:#ef6c00,stroke-width:2px,color:#fff
    classDef dataStyle fill:#81c784,stroke:#388e3c,stroke-width:2px,color:#fff
    classDef llmStyle fill:#ff8a65,stroke:#d84315,stroke-width:2px,color:#fff

    class TEACHER,ADMIN,BROWSER userStyle
    class LP,QB,KB,QA,DASH frontendStyle
    class CTRL,SVC,LLM,RAG,SEC backendStyle
    class MYSQL,FILES,HIST,USER dataStyle
    class DS,ZP,QW,KIMI,MM llmStyle
```

### ProcessOn / draw.io 布局建议

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户层                                   │
│  ┌──────┐    ┌──────┐    ┌──────┐                             │
│  │教师用户│    │管理员│    │浏览器│                             │
│  └──────┘    └──────┘    └──────┘                             │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     前端层 (Vue 3)                               │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐             │
│  │教案生成│  │题库生成│  │知识库│  │智能答疑│  │仪表盘│             │
│  └──────┘  └──────┘  └──────┘  └──────┘  └──────┘             │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   后端层 (Spring Boot)                           │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐             │
│  │Controller│ │Service│ │LLM Client│ │RAG Service│ │Security│     │
│  └──────┘  └──────┘  └──────┘  └──────┘  └──────┘             │
└─────────────────────────────────────────────────────────────────┘
                    │                           │
                    ▼                           ▼
┌─────────────────────────────┐   ┌───────────────────────────────┐
│          数据层              │   │      外部服务                 │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐│   │┌────┐┌────┐┌────┐┌────┐┌────┐│
│  │MySQL││文件││历史││用户││   │ ││DeepSeek││智谱││千问││Kimi││MiniMax││
│  └────┘ └────┘ └────┘ └────┘│   │└────┘└────┘└────┘└────┘└────┘│
└─────────────────────────────┘   └───────────────────────────────┘
```

---

## 2. 功能模块图

### Mermaid代码

```mermaid
mindmap
    root((教师助手))
        核心功能
            教案智能生成
                PPT素材解析
                四档内容等级
                基础版(4段)
                标准版(6段)
                详尽版(8段)
                特详版(10段)
                思政融入
            题库智能生成
                单选题
                多选题
                判断题
                编程题(编译校验)
                难度分级
                Excel导出
            RAG知识库
                文档上传
                智能分块
                关键词索引
                检索增强
            智能答疑
                上下文记忆
                引用标注
                流式输出
        辅助功能
            AI审阅
            多模型支持
            课程配置
            任务中心
            积分系统
            仪表盘
            用户管理
            审计日志
```

### graph版本（更适合导出）

```mermaid
graph LR
    CENTER(("🎯<br/>教师助手"))

    CORE["⭐ 核心功能"]
    AUX["🔧 辅助功能"]

    CENTER --> CORE
    CENTER --> AUX

    LP["📝<br/>教案生成"]
    QB["❓<br/>题库生成"]
    KB["📚<br/>知识库"]
    QA["💬<br/>智能答疑"]

    CORE --> LP & QB & KB & QA

    LP --> LP1["素材解析"]
    LP --> LP2["四档等级"]
    LP --> LP3["思政融入"]

    QB --> QB1["四类题型"]
    QB --> QB2["编译校验"]
    QB --> QB3["Excel导出"]

    KB --> KB1["智能分块"]
    KB --> KB2["关键词索引"]

    QA --> QA1["上下文记忆"]
    QA --> QA2["引用标注"]

    REVIEW["🔍 AI审阅"]
    MODEL["🤖 多模型"]
    COURSE["📖 课程配置"]
    TASK["⏰ 任务中心"]
    POINT["💰 积分系统"]
    DASH["📊 仪表盘"]

    AUX --> REVIEW & MODEL & COURSE & TASK & POINT & DASH

    style CENTER fill:#4361ee,stroke:#1a1a2e,stroke-width:3px,color:#fff
    style CORE fill:#7209b7,stroke:#1a1a2e,stroke-width:2px,color:#fff
    style AUX fill:#00b4d8,stroke:#1a1a2e,stroke-width:2px,color:#fff

    classDef coreBox fill:#2e7d32,stroke:#1b5e20,color:#fff
    classDef auxBox fill:#f77f00,stroke:#e65100,color:#fff

    class LP,QB,KB,QA coreBox
    class REVIEW,MODEL,COURSE,TASK,POINT,DASH auxBox
```

---

## 3. 质量保障流程图

### Mermaid代码

```mermaid
flowchart TD
    START(["开始"])

    PHASE1(("📋 阶段1<br/>初次生成"))
    P11["用户上传素材"]
    P12["构建提示词模板"]
    P13["调用国产大模型"]
    P14["生成初稿"]

    CHECK1{🔍 合规检查}
    PASS1["✅ 格式规范"]
    PASS2["✅ 思政要素"]
    PASS3["✅ 知识点完整"]

    PHASE2(("🔧 阶段2<br/>合规自检"))
    SELF1["AI检查格式规范"]
    SELF2["AI检查思政要素"]
    SELF3["AI检查知识点"]

    CHECK2{⚖️ 二次判断}
    PHASE3(("🎯 阶段3<br/>AI批判与修正")]
    CRITIC1["AI角色扮演"]
    CRITIC2["专业批判"]
    CRITIC3["修正重生成"]

    COMPILE["🔨 编译校验<br/>(题库专用)"]

    CHECK3{✨ 最终质检}
    OUTPUT(["📤 输出高质量资源"])

    FAIL["❌ 不通过"]

    START --> PHASE1
    PHASE1 --> P11 & P12 & P13 & P14
    P11 & P12 & P13 & P14 --> CHECK1

    CHECK1 -->|通过| PASS1 & PASS2 & PASS3 --> PHASE2
    CHECK1 -->|不通过| FAIL

    PHASE2 --> SELF1 & SELF2 & SELF3
    SELF1 & SELF2 & SELF3 --> CHECK2

    CHECK2 -->|通过| PHASE3
    CHECK2 -->|不通过| FAIL

    PHASE3 --> CRITIC1 & CRITIC2 & CRITIC3
    CRITIC1 & CRITIC2 & CRITIC3 --> CHECK3

    CHECK3 -->|通过| COMPILE
    CHECK3 -->|不通过| PHASE3

    COMPILE --> CHECK3
    FAIL --> PHASE1
    CHECK3 -->|最终通过| OUTPUT

    style START fill:#4361ee,stroke:#1a1a2e,stroke-width:2px,color:#fff
    style OUTPUT fill:#2e7d32,stroke:#1a1a2e,stroke-width:2px,color:#fff
    style FAIL fill:#e94560,stroke:#1a1a2e,stroke-width:2px,color:#fff

    style PHASE1 fill:#4361ee,stroke:#1a1a2e,stroke-width:2px,color:#fff
    style PHASE2 fill:#f77f00,stroke:#1a1a2e,stroke-width:2px,color:#fff
    style PHASE3 fill:#7209b7,stroke:#1a1a2e,stroke-width:2px,color:#fff

    classDef passStyle fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    classDef failStyle fill:#ffcdd2,stroke:#c62828,stroke-width:2px

    class CHECK1,CHECK2,CHECK3 failStyle
    class PASS1,PASS2,PASS3 passStyle
```

### 简化版本

```mermaid
flowchart LR
    A(["📥 用户素材"]) --> B["🤖 阶段1: 初次生成"]

    B --> C{✅ 合规检查}
    C -->|通过| D["🔍 阶段2: 合规自检"]
    C -->|不通过| B

    D --> E{⚖️ 质量判断}
    E -->|通过| F["🎯 阶段3: AI批判修正"]
    E -->|不通过| B

    F --> G{✨ 最终质检}
    G -->|通过| H(["📤 高质量输出"])
    G -->|不通过| F

    H --> I["📈 合格率 60% → 90%+"]

    style A fill:#e3f2fd,stroke:#1976d2
    style B fill:#4361ee,stroke:#1a1a2e,color:#fff
    style D fill:#f77f00,stroke:#1a1a2e,color:#fff
    style F fill:#7209b7,stroke:#1a1a2e,color:#fff
    style H fill:#2e7d32,stroke:#1a1a2e,color:#fff
    style I fill:#00b4d8,stroke:#1a1a2e,color:#fff
```

---

## 4. 技术栈图

### Mermaid代码

```mermaid
graph TB
    subgraph 前端技术栈["🎨 前端技术栈"]
        VUE["Vue 3"]
        ELE["Element Plus"]
        ECH["ECharts"]
        PIN["Pinia"]
        AXI["Axios"]
        VIT["Vite"]
    end

    subgraph 后端技术栈["⚙️ 后端技术栈"]
        SB["Spring Boot 3"]
        MB["MyBatis-Plus"]
        MYQ["MySQL 8"]
        JW["JWT"]
        SCH["Scheduled"]
        RES["RESTful API"]
    end

    subgraph 文档处理["📄 文档处理"]
        POI["Apache POI"]
        POT["poi-tl"]
        PDF["PDFBox"]
        EAS["EasyExcel"]
        JSO["Jsoup"]
        DOC["docx4j"]
    end

    subgraph AI能力["🤖 AI 能力"]
        DS["DeepSeek"]
        ZP["智谱清言"]
        QW["通义千问"]
        KI["Kimi"]
        MM["MiniMax"]
    end

    subgraph 开发工具["🛠️ 开发工具"]
        CLAUDE["Claude Code"]
        GIT["Git"]
        MAV["Maven"]
        JDK["JDK 17"]
        NODE["Node.js 18"]
        PYT["Python 3.10"]
    end

    subgraph 核心特性["💡 核心特性"]
        RAG["轻量级RAG"]
        MULTI["多模型自适应"]
        TWO["两阶段生成"]
        LOOP["闭环质量保障"]
        COMP["编译校验"]
        DECOUP["课程配置解耦"]
    end

    style 前端技术栈 fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    style 后端技术栈 fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style 文档处理 fill:#fff3e0,stroke:#e65100,stroke-width:2px
    style AI能力 fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style 开发工具 fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    style 核心特性 fill:#e0f7fa,stroke:#0097a7,stroke-width:2px

    classDef techBox fill:#fff,stroke:#333,stroke-width:1px
    class VUE,ELE,ECH,PIN,AXI,VIT,SB,MB,MYQ,JW,SCH,RES,POI,POT,PDF,EAS,JSO,DOC,DS,ZP,QW,KI,MM,CLAUDE,GIT,MAV,JDK,NODE,PYT,RAG,MULTI,TWO,LOOP,COMP,DECOUP techBox
```

### 分层架构版本

```mermaid
graph TB
    subgraph 展示层["📱 展示层"]
        VUE["Vue 3 组合式API"]
        ELE["Element Plus 组件库"]
        ECH["ECharts 数据可视化"]
    end

    subgraph 接口层["🔌 接口层"]
        RES["RESTful API"]
        JWT["JWT 认证"]
    end

    subgraph 业务层["💼 业务层"]
        SVC["Service 业务逻辑"]
        LLM["LLM Client"]
        RAG["RAG Service"]
        SEC["Security"]
    end

    subgraph 数据层["💾 数据层"]
        MBP["MyBatis-Plus"]
        MYQ["MySQL 8.0"]
        FILE["文件存储"]
    end

    subgraph 外部服务["☁️ 外部服务"]
        API["OpenAI 兼容协议"]
        LLM1["DeepSeek"]
        LLM2["智谱清言"]
        LLM3["通义千问"]
    end

    展示层 --> 接口层
    接口层 --> 业务层
    业务层 --> 数据层
    业务层 --> 外部服务

    API --> LLM1 & LLM2 & LLM3

    style 展示层 fill:#4361ee,stroke:#1a1a2e,color:#fff
    style 接口层 fill:#00b4d8,stroke:#1a1a2e,color:#fff
    style 业务层 fill:#7209b7,stroke:#1a1a2e,color:#fff
    style 数据层 fill:#2e7d32,stroke:#1a1a2e,color:#fff
    style 外部服务 fill:#f77f00,stroke:#1a1a2e,color:#fff
```

---

## 5. RAG流程图

### Mermaid代码

```mermaid
flowchart TD
    subgraph KB_BUILD["📚 知识库构建"]
        UPLOAD["上传文档<br/>PDF/PPT/Word"]
        PARSE["文档解析"]
        CHUNK["智能分块<br/>200-500字"]
        INDEX["TF关键词提取"]
        STORE["关键词索引存储"]
    end

    subgraph RAG_FLOW["🔄 检索增强生成"]
        INPUT["👤 用户输入<br/>生成请求"]
        KW["提取关键词"]
        SEARCH["🔍 检索知识库"]
        RETRIEVE["获取相关内容"]
        PROMPT["构建增强提示词"]
        LLM_CALL["调用大模型"]
        OUTPUT["📤 生成输出"]
    end

    UPLOAD --> PARSE --> CHUNK --> INDEX --> STORE

    INPUT --> KW --> SEARCH
    SEARCH --> STORE
    STORE --> RETRIEVE
    RETRIEVE --> PROMPT --> LLM_CALL --> OUTPUT

    style KB_BUILD fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    style RAG_FLOW fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px

    style UPLOAD fill:#4fc3f7,stroke:#0277bd
    style STORE fill:#81c784,stroke:#388e3c
    style INPUT fill:#ffb74d,stroke:#ef6c00
    style OUTPUT fill:#2e7d32,stroke:#1b5e20,color:#fff
```

### 详细版本

```mermaid
flowchart LR
    subgraph 左侧["📚 知识库构建"]
        A1["📄 上传文档"]
        A2["🔤 文档解析<br/>PDFBox/poi-tl"]
        A3["✂️ 智能分块<br/>200-500字/块"]
        A4["🏷️ TF关键词<br/>提取"]
        A5["💾 索引存储"]
    end

    subgraph 右侧["🔄 RAG检索生成"]
        B1["👤 用户请求"]
        B2["🔤 提取关键词"]
        B3["🔍 检索知识库"]
        B4["📖 获取相关片段"]
        B5["📝 构建提示词<br/>+ 知识库内容"]
        B6["🤖 调用LLM"]
        B7["📤 生成输出"]
    end

    A1 --> A2 --> A3 --> A4 --> A5

    B1 --> B2
    B2 --> B3
    B3 -.->|检索| A5
    A5 -.->|返回| B4
    B4 --> B5
    B5 --> B6
    B6 --> B7

    subgraph 优势["💡 优势"]
        C1["轻量级<br/>无需向量库"]
        C2["部署简单<br/>适合高职"]
        C3["响应快速<br/>秒级检索"]
        C4["可解释性强<br/>明确引用"]
    end

    style 左侧 fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style 右侧 fill:#fff3e0,stroke:#e65100,stroke-width:2px
    style 优势 fill:#e3f2fd,stroke:#1976d2,stroke-width:2px

    style A5 fill:#81c784,stroke:#388e3c
    style B7 fill:#4361ee,stroke:#1a1a2e,color:#fff
```

---

## 使用说明

### 方法1：在线Mermaid编辑器（推荐）

1. 访问 https://mermaid.live/
2. 复制上面的代码块
3. 粘贴到左侧编辑器
4. 右侧实时预览
5. 点击 "Download PNG" 或 "Download SVG" 下载

### 方法2：draw.io

1. 访问 https://app.diagrams.net/
2. 点击 "Arrange" -> "Insert" -> "Advanced" -> "Mermaid"
3. 粘贴Mermaid代码
4. 调整样式和布局
5. 导出为PNG/SVG

### 方法3：ProcessOn

1. 访问 https://www.processon.com/
2. 新建流程图
3. 在左侧工具栏找到 "Mermaid" 或使用图形库手动绘制
4. 参考上面的布局建议手动绘制

### 方法4：VS Code

1. 安装 "Markdown Preview Mermaid Support" 插件
2. 新建 .md 文件
3. 粘贴代码块
4. 预览并截图

### 方法5：Typora

1. 打开Typora
2. 新建文档
3. 粘贴代码块
4. 导出为PNG（需要安装主题支持）

---

## 样式调整建议

### 颜色方案

```css
/* 推荐配色 */
主色: #4361ee  /* 蓝色 */
辅色: #7209b7  /* 紫色 */
成功: #2e7d32  /* 绿色 */
警告: #f77f00  /* 橙色 */
危险: #e94560  /* 红色 */
信息: #00b4d8  /* 青色 */
深色: #1a1a2e  /* 深色背景 */
```

### 节点样式

```mermaid
%% 圆角矩形
style id fill:#颜色,stroke:#边框色,stroke-width:2px,color:#文字色

%% 圆形节点
id(("文字"))
style id fill:#颜色,stroke:#边框色,stroke-width:2px,color:#文字色

%% 菱形（判断）
id{条件}
style id fill:#颜色,stroke:#边框色,stroke-width:2px,color:#文字色
```

---

## 导出建议

1. **PNG格式**：适合Word文档，推荐分辨率 300 DPI
2. **SVG格式**：可缩放矢量，适合Web展示
3. **尺寸建议**：
   - 横向图：宽度 1920px 或 1280px
   - 纵向图：高度 1080px
4. **背景**：建议使用白色或透明背景
