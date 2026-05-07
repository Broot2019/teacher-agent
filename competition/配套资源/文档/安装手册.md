# 安装手册

## 系统要求

| 项目 | 最低要求 |
|------|---------|
| 操作系统 | Windows 10+ / macOS / Linux |
| JDK | 17 或更高 |
| Node.js | 18 或更高 |
| MySQL | 8.0 或更高 |
| 内存 | 4GB+ |
| 磁盘 | 1GB+ 可用空间 |

## 安装步骤

### 1. 安装基础环境

#### JDK 17
- 下载地址：https://adoptium.net/
- 安装后确认：`java -version` 输出 17.x

#### Node.js 18+
- 下载地址：https://nodejs.org/
- 安装后确认：`node -v` 输出 v18+

#### MySQL 8.0
- 下载地址：https://dev.mysql.com/downloads/mysql/
- 安装后创建数据库：
  ```sql
  CREATE DATABASE IF NOT EXISTS teacher_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

### 2. 获取源代码

```bash
git clone <仓库地址> teacher-agent
cd teacher-agent
```

### 3. 初始化数据库

按顺序执行 SQL 脚本：

```bash
mysql -uroot -p < init.sql
mysql -uroot -p < init_v2.sql
mysql -uroot -p < backend/src/main/resources/db/migration/V2__course_config.sql
mysql -uroot -p < backend/src/main/resources/db/migration/V3__knowledge_base.sql
```

默认管理员账号：`admin / 123456`

### 4. 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/teacher_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 你的数据库密码

jwt:
  secret: 请更换为随机高强度密钥
```

### 5. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

或使用 `start.bat`（Windows 一键启动）。

后端默认地址：`http://localhost:8089`

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://localhost:5173`

### 7. 验证安装

1. 浏览器访问 `http://localhost:5173`
2. 使用 `admin / 123456` 登录
3. 进入「模型配置」页面，添加大模型 API Key 并激活
4. 进入「课程配置」页面，配置课程信息
5. 尝试生成一份教案或题库

## 常见问题

### Q: 启动报 "Port 8089 already in use"
修改 `application.yml` 中 `server.port` 为其他端口。

### Q: MySQL 连接失败
检查 MySQL 是否启动、用户名密码是否正确、数据库是否已创建。

### Q: 前端 npm install 报错
删除 `node_modules` 目录后重试，或使用镜像源：`npm config set registry https://registry.npmmirror.com`

### Q: 生成任务一直 pending
检查是否已配置并激活至少一个大模型。
