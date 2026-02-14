# Local News Reco

一个可运行的新闻推荐系统样板，包含：

- Spring Boot 后端 API（文章、推荐、行为上报、统计）
- Astro 前端多页面应用（推荐首页、新闻库、详情页、行为洞察）

## 技术栈

- 后端：Java 11, Spring Boot 2.7
- 前端：Astro 5

## 快速启动

### 1) 启动后端

```bash
mvn clean package -DskipTests
java -jar target/local-news-reco-0.0.1.jar
```

后端默认端口为 `8080`，可用 `PORT` 覆盖：

```bash
PORT=9090 java -jar target/local-news-reco-0.0.1.jar
```

### 2) 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址 `http://localhost:4321`。  
本地开发时会自动请求 `http://localhost:8080`，线上默认走同域 `/api`。

### 3) 一键启动/停止脚本

```bash
# 启动前后端
./scripts/devctl.sh start

# 停止前后端
./scripts/devctl.sh stop

# 先 stop 再 start
./scripts/devctl.sh restart

# 查看状态
./scripts/devctl.sh status

# 查看日志
./scripts/devctl.sh logs
```

## 前端页面

- `/` 推荐首页：个性化推荐流 + 热标签 + 事件类型分布
- `/articles` 新闻库：全量文章、关键词和标签筛选、行为按钮
- `/article?id=xxx` 新闻详情：曝光/点击/点赞/分享上报
- `/insights` 行为洞察：系统统计 + 当前用户最近事件

## API 清单

- `GET /api/health`
- `GET /api/articles`
- `GET /api/articles/{articleId}`
- `GET /api/users/{userId}/recommendations?limit=10`
- `POST /api/events`
- `GET /api/users/{userId}/events?limit=20`
- `GET /api/stats/overview`

## 真实新闻源配置

后端会从 RSS 实时拉取新闻（默认美国新闻源），并在拉取失败时自动回退到内置占位数据，避免接口空结果。

默认源配置在 `/src/main/resources/application.yml`：

- Google News US RSS
- New York Times US RSS
- NPR News RSS

可通过环境变量调整：

- `NEWS_REFRESH_MINUTES`：拉取刷新间隔（分钟，默认 20）
- `NEWS_MAX_ARTICLES`：最多保留文章数（默认 120）
- `NEWS_REQUEST_TIMEOUT_SECONDS`：单个源超时秒数（默认 12）

### 行为上报示例

```bash
curl http://localhost:8080/api/articles | head

curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","itemId":"<articleId-from-api-articles>","type":"click","ts":1739500000000}'
```

## 模板参考

前端视觉和页面组织参考了免费开源的 Astro 新闻主题风格（MIT）：

- [Astro Themes: Astro News](https://astro.build/themes/details/astro-news/)
- [GitHub: astro-news](https://github.com/RinaShin/astro-news)
