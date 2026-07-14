# 金融理财顾问平台 (Financial Advisor Platform)

基于 **Spring AI Alibaba** 框架构建的金融领域多智能体对话平台，采用 **ReAct 模式**实现智能理财顾问。集成 17 个金融工具（全部注册到 Agent）、大模型推理、联网搜索、RAG 知识库（混合检索）、SSE 流式对话、投资组合管理、Markowitz 组合优化、真实 K 线图、价格预警、定时任务、理财目标规划等能力，为个人投资者提供专业的金融理财咨询服务。

---

## 目录

- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [模块依赖关系](#模块依赖关系)
- [核心功能](#核心功能)
  - [1. Agent 工作流程与 Hook 机制](#1-agent-工作流程与-hook-机制)
  - [2. Agent System Prompt 详解](#2-agent-system-prompt-详解)
  - [3. 金融工具 (17 个)](#3-金融工具-17-个)
  - [4. RAG 知识库与混合检索](#4-rag-知识库与混合检索)
  - [5. 用户认证与风险偏好](#5-用户认证与风险偏好)
  - [6. 投资组合与理财目标](#6-投资组合与理财目标)
  - [7. 定时任务与价格预警](#7-定时任务与价格预警)
  - [8. SSE 流式对话协议](#8-sse-流式对话协议)
  - [9. 个性化推荐机制](#9-个性化推荐机制)
  - [10. 可观测性](#10-可观测性)
- [数据库表结构](#数据库表结构)
- [快速启动](#快速启动)
- [API 接口文档](#api-接口文档)
  - [认证接口](#认证接口)
  - [对话接口](#对话接口)
  - [知识库接口](#知识库接口)
  - [资产组合接口](#资产组合接口)
  - [理财目标接口](#理财目标接口)
  - [会话管理接口](#会话管理接口)
  - [市场行情接口](#市场行情接口)
  - [投资分析接口](#投资分析接口)
  - [工具箱接口](#工具箱接口)
  - [价格预警接口](#价格预警接口)
- [前端架构](#前端架构)
  - [路由与页面](#路由与页面)
  - [状态管理](#状态管理)
  - [API 层](#api-层)
  - [字体规范](#字体规范)
  - [SSE 流式渲染](#sse-流式渲染)
- [数据存储架构](#数据存储架构)
- [环境变量](#环境变量)
- [完整配置参考](#完整配置参考)
- [测试](#测试)
- [部署指南](#部署指南)
  - [开发环境](#开发环境)
  - [Docker 一键部署](#docker-一键部署)
  - [生产环境 (手动)](#生产环境-手动)
  - [Nginx 反向代理配置](#nginx-反向代理配置)
- [安全设计](#安全设计)
- [扩展与自定义](#扩展与自定义)
  - [添加新金融工具](#添加新金融工具)
  - [添加新前端页面](#添加新前端页面)
  - [切换大模型](#切换大模型)
- [常见问题](#常见问题)
- [License](#license)

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         用户浏览器 (Vue 3 + Naive UI)                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │ 智能对话  │ │ 我的资产  │ │ 理财目标  │ │ 市场行情  │ │ 投资分析  │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                      │
│  │ 工具箱   │ │  知识库   │ │  仪表盘   │ │   设置    │                      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘                      │
│                              │ axios (JWT)                                   │
──────────────────────────────┼───────────────────────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────┼───────────────────────────────────────────────┐
│                    Spring Boot 后端 (8 模块)                                   │
│  ┌──────────────────────────┴────────────────────────────────────────────┐  │
│  │                    advisor-api (API 接口层)                            │  │
│  │  ChatController │ DocumentController │ SessionController              │  │
│  │  MarketController │ AnalysisController │ ToolController               │  │
│  │  PriceAlertController │ ChatFileController                            │  │
│  └──────────────────────────┬────────────────────────────────────────────┘  │
│                             │                                                │
│  ┌──────────────┐  ┌────────┴────────┐  ┌──────────────────────────────┐   │
│  │ advisor-user │  │ advisor-portfolio│  │      advisor-rag             │   │
│  │  JWT 认证    │  │  资产/目标 CRUD   │  │  RAG 知识库 / 混合检索       │   │
│  │              │  │  价格预警/定时刷新 │  │  知识库定时更新              │   │
│  └──────────────┘  └─────────────────┘  └──────────────────────────────┘   │
│                             │                                                │
│  ┌──────────────────────────┴────────────────────────────────────────────┐  │
│  │                   advisor-agent (Agent 核心层)                          │  │
│  │  FinancialAdvisorAgent (ReAct) → 17 工具注册 → Hook 机制              │  │
│  │  ContextCompressionHook │ ConfirmationHook │ PostgresSaver            │  │
│  └──────────────────────────┬────────────────────────────────────────────┘  │
│                             │                                                │
│  ┌──────────────────────────┴────────────────────────────────────────────┐  │
│  │                    advisor-tool (17 个金融工具)                          │  │
│  │  Tavily搜索 │ 基金净值 │ 股票行情 │ K线图(真实) │ 组合优化(Markowitz)  │  │
│  │  汇率 │ 个税计算 │ 基金筛选 │ 风险评估 │ 信用卡分期 │ 保险对比 │ ...    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                             │                                                │
│  ┌──────────────────────────┴────────────────────────────────────────────┐  │
│  │                   advisor-common (公共模块)                             │  │
│  │  ApiResponse │ AdvisorConstants │ SessionManager                       │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               │ JDBC / pgvector
──────────────────────────────┼───────────────────────────────────────────────┐
│                      PostgreSQL + pgvector                                    │
│  ┌────────┐ ┌────────────┐ ┌────────────┐ ┌────────┐ ┌────────┐           │
│  │ users  │ │vector_store│ │knowledge   │ │ assets │ │ goals  │           │
│  └────────┘ └────────────┘ └────────────┘ └────────┘ └────────┘           │
│  ┌────────────────┐ ┌──────────────┐ ┌──────────────────┐                  │
│  │asset_price_    │ │price_alerts  │ │SPRING_AI_CHAT_   │                  │
│  │history         │ │              │ │MEMORY             │                  │
│  └────────────────┘ └──────────────┘ └──────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 请求链路

```
浏览器 → Nginx (SPA fallback + /api 代理)
       → Spring Boot (JwtAuthFilter 校验)
       → Controller → Service → Agent/Tool
       → PostgreSQL (数据持久化) / pgvector (向量检索)
       → SSE 推流 → 浏览器实时渲染
```

---

## 技术栈

### 后端技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|---|---|---|---|
| **后端框架** | Spring Boot | 3.5.8 | 内嵌 Tomcat，支持 WebFlux SSE |
| **JDK** | Oracle OpenJDK (Temurin) | 17+ | 虚拟线程、Record 等特性 |
| **AI 框架** | Spring AI + Spring AI Alibaba | 1.1.2 / 1.1.2.2 | ReactAgent、Graph、Hook 机制 |
| **大模型** | 阿里云百炼 qwen3.6-plus (DashScope) | - | 云端推理，支持 function calling |
| **嵌入模型** | text-embedding-v3 (DashScope) | - | **1024 维**向量嵌入 |
| **向量数据库** | PostgreSQL + pgvector | pg16 | cosine similarity 语义检索 |
| **认证** | JWT (jjwt) | 0.12.6 | HS256 签名，24h 过期 |
| **搜索引擎** | Tavily API | - | 实时财经资讯，限定近 7 天 |
| **K 线数据** | 新浪行情 API + 东方财富 API | - | 真实 OHLC 数据，自动降级 |
| **组合优化** | Apache Commons Math3 | 3.6.1 | Markowitz 均值-方差模型 |
| **图表生成** | XChart | 3.8.8 | K 线蜡烛图 PNG 输出 |
| **可观测性** | Actuator + Prometheus + Grafana | - | 6 面板监控仪表盘 |
| **Agent 持久化** | PostgresSaver (默认) | - | 检查点持久化到数据库 |
| **配置中心** | Nacos (可选) | ^3.1+ | 动态配置刷新 |

### 前端技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|---|---|---|---|
| **前端框架** | Vue 3 (Composition API) | ^3.4.0 | `<script setup>` 语法 |
| **前端路由** | Vue Router | ^4.3.0 | History 模式 + 路由守卫 |
| **前端组件库** | Naive UI | ^2.44.1 | 暗色主题、表单、日期选择器 |
| **图标库** | @vicons/ionicons5 | ^0.12.0 | 侧边栏/操作图标 |
| **构建工具** | Vite | ^5.4.0 | HMR 热更新、代理、dedupe |
| **HTTP 客户端** | Axios | ^1.7.0 | 拦截器、JWT 注入、401 处理 |
| **Markdown 渲染** | markdown-it + highlight.js | ^14.0.0 / ^11.9.0 | 代码高亮、表格、链接 |
| **图表** | Chart.js + vue-chartjs | ^4.4.0 / ^5.3.0 | 饼图、折线图、散点图、仪表盘 |

### 基础设施

| 组件 | 技术选型 | 版本 | 说明 |
|---|---|---|---|
| **容器化** | Docker + docker-compose | - | 5 服务编排 |
| **反向代理** | Nginx (生产环境) | Alpine | SPA fallback + API 代理 |
| **数据库** | PostgreSQL | 16 | 主数据库 + pgvector 扩展 |

---

## 项目结构

```
financial-advisor-platform/
├── backend/                              # Spring Boot 多模块后端 (8 个子模块)
│   ├── pom.xml                           # 父 POM (pom 包装，统一管理依赖版本)
│   │
│   ├── advisor-common/                   # 公共模块 — 跨模块共享的基础设施
│   │   └── src/main/java/.../common/
│   │       ├── constant/AdvisorConstants.java    # 常量定义
│   │       ├── dto/ApiResponse.java              # 统一响应包装 {code, message, data, timestamp}
│   │       └── session/SessionManager.java       # 会话管理 (按 userId+sessionId 隔离)
│   │
│   ├── advisor-user/                     # 用户与认证模块
│   │   └── src/main/java/.../user/
│   │       ├── AuthController.java             # 登录/注册/资料/风险等级接口
│   │       ├── JwtAuthFilter.java              # JWT 鉴权过滤器 (OncePerRequestFilter)
│   │       ├── JwtService.java                 # JWT 签发/校验 (HS256, jjwt 0.12.x)
│   │       ├── SecurityConfig.java             # Spring Security 配置
│   │       ├── User.java / UserRepository.java / UserService.java
│   │       └── RiskLevelRequest.java           # R1-R5 风险等级更新请求 DTO
│   │
│   ├── advisor-tool/                     # 工具层 (17 个金融工具，全部注册到 Agent)
│   │   └── src/main/java/.../tool/
│   │       ├── FinancialTools.java             # 基础工具集 (联网搜索/复利/贷款/研报检索)
│   │       └── finance/
│   │           ├── StockQuoteTool.java             # 股票实时行情 (新浪 API)
│   │           ├── FundNavTool.java                # 基金净值查询 (东方财富 API)
│   │           ├── KlineChartTool.java             # K线图生成 (真实行情 + XChart 蜡烛图)
│   │           ├── KlineFetcher.java               # K线数据获取 (新浪 API + 东方财富降级)
│   │           ├── PortfolioOptimizerTool.java     # 投资组合优化 (Markowitz + Commons Math3)
│   │           ├── RiskQuestionnaireTool.java      # 风险评估问卷 (R1-R5)
│   │           ├── FundScreenerTool.java           # 基金筛选器
│   │           ├── ExchangeRateTool.java           # 汇率转换
│   │           ├── TaxCalculatorTool.java          # 个人所得税计算 (累进税率)
│   │           ├── CreditCardInstallmentTool.java  # 信用卡分期计算
│   │           ├── InsuranceCompareTool.java       # 保险产品对比分析
│   │           ├── FinancialCalendarTool.java      # 金融日历查询
│   │           ├── SavingsGoalTool.java            # 储蓄目标规划
│   │           └── MarketSentimentTool.java        # 市场情绪指数分析
│   │
│   ├── advisor-rag/                      # RAG 知识库
│   │   └── src/main/java/.../rag/
│   │       ├── DocumentIngestionService.java     # 多格式文档导入 (PDF/Excel/CSV/MD/TXT/图片OCR)
│   │       ├── DocumentMetadataService.java      # 文档元数据管理 + URL 去重
│   │       ├── HybridSearchService.java          # 混合检索 (向量语义 + ILIKE 关键词 + RRF 融合)
│   │       ├── KnowledgeRefreshTask.java         # 知识库定时更新 (每周一 8:00 Tavily 摄入)
│   │       └── reader/                           # CsvReader / ExcelReader / MarkdownReader / OcrReader
│   │
│   ├── advisor-portfolio/                # 投资组合模块
│   │   └── src/main/java/.../portfolio/
│   │       ├── PortfolioController.java          # 资产 CRUD + 汇总 + 导入 + 历史趋势
│   │       ├── PortfolioService.java             # 组合管理 + 行情刷新 + 快照写入
│   │       ├── Asset.java / AssetRepository.java # 资产实体与数据访问
│   │       ├── AssetImportParser.java            # Excel/CSV 批量导入解析
│   │       ├── PortfolioPriceRefreshRunner.java  # 启动时自动刷新行情 (ApplicationRunner)
│   │       ├── MarketDataRefreshTask.java        # 定时行情刷新 (交易日 15:30)
│   │       ├── PriceAlertService.java            # 价格预警服务 (涨破/跌破)
│   │       ├── PriceAlertController.java         # 预警 CRUD 接口 (/api/alert)
│   │       ├── SecurityUtil.java                 # 当前登录用户上下文
│   │       └── goal/                             # GoalController / GoalService / Goal / GoalRepository
│   │
│   ├── advisor-agent/                    # Agent 核心层
│   │   └── src/main/java/.../agent/
│   │       ├── core/
│   │       │   └── FinancialAdvisorAgent.java    # 金融顾问 ReactAgent (17 工具 + Hook + 风险约束)
│   │       ├── config/
│   │       │   ├── AgentConfig.java              # Agent 全局配置
│   │       │   ├── ChatMemoryConfig.java         # 对话记忆 (JDBC 持久化, MessageWindowChatMemory)
│   │       │   ├── CheckpointConfig.java         # 检查点持久化 (PostgresSaver.builder())
│   │       │   ├── AgentEnhancementConfig.java   # 多 Agent 编排 (Supervisor/Sequential/Parallel)
│   │       │   ├── MultiAgentConfig.java         # RoutingAgent 多路由
│   │       │   └── A2AConfig.java                # Agent 间通信 (A2A 协议)
│   │       └── hook/
│   │           ├── ContextCompressionHook.java   # 上下文压缩 (>20 条裁剪至 6 条)
│   │           └── ConfirmationHook.java         # 人工确认 (大额资金操作 >= 100,000 元)
│   │
│   ├── advisor-api/                      # API 接口层
│   │   └── src/main/java/.../api/
│   │       ├── controller/
│   │       │   ├── ChatController.java           # SSE 流式对话 + 非流式对话
│   │       │   ├── ChatFileController.java       # 聊天文件上传
│   │       │   ├── DocumentController.java       # 知识库文档管理
│   │       │   └── SessionController.java        # 会话管理
│   │       ├── MarketController.java             # 市场行情 (指数/情绪/日历)
│   │       ├── AnalysisController.java           # 投资分析 (K线/优化/风险收益)
│   │       ├── ToolController.java               # 工具箱 (个税/基金筛选/汇率/分期)
│   │       └── dto/                              # ChatRequest / ChatResponse
│   │
│   ├── advisor-bootstrap/                # 启动入口
│   │   ├── src/main/java/.../bootstrap/
│   │   │   └── AdvisorApplication.java           # Spring Boot 启动类 (@EnableScheduling)
│   │   └── src/main/resources/
│   │       ├── application.yml                   # 主配置 (checkpoint.saver=postgres)
│   │       ├── application-dev.yml               # 开发环境
│   │       ├── application-prod.yml              # 生产环境
│   │       ├── application-test.yml              # 测试环境
│   │       └── bootstrap.yml                     # Nacos 配置中心 (可选)
│   │
│   └── Dockerfile                                # 后端镜像 (eclipse-temurin:17-jre-alpine)
│
├── frontend/                             # Vue 3 + Naive UI 前端
│   ├── src/
│   │   ├── views/                        # 10 个页面
│   │   │   ├── ChatView.vue                # 智能对话 (SSE 流式 + 推理链 + 文件上传)
│   │   │   ├── DashboardView.vue           # 仪表盘 (统计卡 + 盈亏趋势 + AI配置建议 + 预警红点)
│   │   │   ├── PortfolioView.vue           # 资产组合管理 (CRUD + 导入 + 汇总)
│   │   │   ├── GoalView.vue                # 理财目标管理 (CRUD + 进度)
│   │   │   ├── MarketView.vue              # 市场行情 (四大指数 + 情绪仪表盘 + 金融日历)
│   │   │   ├── AnalysisView.vue            # 投资分析 (真实K线图 + 优化权重饼图 + 散点图)
│   │   │   ├── ToolboxView.vue             # 工具箱 (个税/基金筛选/汇率/分期)
│   │   │   ├── KnowledgeView.vue           # 知识库管理 (上传/搜索/联网导入)
│   │   │   ├── LoginView.vue               # 登录/注册
│   │   │   └── SettingsView.vue            # 系统设置
│   │   ├── components/
│   │   │   ├── AppLayout.vue               # 整体布局 (侧边栏 + 内容区)
│   │   │   ├── SessionSidebar.vue          # 会话侧边栏 (9 个导航入口)
│   │   │   ├── MessageList.vue             # 消息列表 (Markdown + 工具调用)
│   │   │   ├── MessageInput.vue            # 输入框 (文件附件)
│   │   │   ├── MarkdownContent.vue         # Markdown 渲染
│   │   │   ├── AssetForm.vue               # 资产表单
│   │   │   └── GoalForm.vue                # 目标表单
│   │   ├── api/                          # 10 个 API 模块
│   │   │   ├── http.js                     # axios 实例 (JWT 拦截器)
│   │   │   ├── auth.js / chat.js / documents.js / portfolio.js
│   │   │   ├── goal.js / session.js
│   │   │   ├── market.js                   # 市场行情 API
│   │   │   ├── analysis.js                 # 投资分析 API
│   │   │   └── tool.js                     # 工具箱 API
│   │   ├── stores/                         # auth.js / sessions.js / settings.js
│   │   ├── composables/                    # useMarkdown.js / useScroll.js
│   │   ├── App.vue                         # 根组件
│   │   └── main.js                         # 入口 (11 个路由)
│   ├── vite.config.js                      # Vite 配置 (代理/dedupe)
│   ├── Dockerfile                          # 前端镜像 (多阶段构建)
│   └── nginx.conf                          # Nginx 配置
│
├── deploy/                                 # 运维部署
│   ├── prometheus.yml                      # Prometheus 抓取配置
│   ├── grafana/dashboard.json              # Grafana 6 面板仪表盘
│   └── jmeter/performance.jmx              # JMeter 性能测试
│
├── db/
│   └── financial_rag_backup.sql            # 数据库备份
├── docker-compose.yml                      # Docker Compose 编排
└── README.md                               # 本文档
```

---

## 模块依赖关系

```
advisor-bootstrap (启动入口)
    ├── advisor-api (API 接口层)
    │       ├── advisor-agent (Agent 核心)
    │       │       ├── advisor-tool (17 个金融工具)
    │       │       ├── advisor-rag (RAG 知识库)
    │       │       └── advisor-common (公共模块)
    │       ├── advisor-portfolio (投资组合 + 预警)
    │       │       └── advisor-common
    │       ├── advisor-tool (工具层, 直接依赖)
    │       ├── advisor-user (用户认证)
    │       │       └── advisor-common
    │       └── advisor-common
    ├── advisor-user
    ├── advisor-portfolio
    └── advisor-common
```

**依赖说明**:
- `advisor-common` 是最底层模块，被所有其他模块依赖
- `advisor-tool` 和 `advisor-rag` 被 `advisor-agent` 依赖
- `advisor-agent` 被 `advisor-api` 依赖
- `advisor-portfolio` 独立于 Agent，直接被 `advisor-api` 依赖
- `advisor-user` 提供认证能力，被 `advisor-api` 依赖
- `advisor-bootstrap` 汇聚所有模块，提供启动入口和配置文件

---

## 核心功能

### 1. Agent 工作流程与 Hook 机制

Agent 采用 **ReAct (Reasoning + Acting)** 模式，自主决策工具调用时机和顺序。

```
用户输入 → ChatController (SSE 推流入口)
         → FinancialAdvisorAgent (注入用户财务画像 + R1-R5 风险约束)
         → ReAct 循环:
             ├── Reasoning: 大模型分析意图，决定调用哪个工具
             ├── Tool Calling: 调用工具 (17 个可选)
             ├── Hook (BEFORE_MODEL): ContextCompressionHook 压缩 >20 条消息
             ├── Reasoning: 综合工具结果，生成最终回复
             └── Hook (AFTER_MODEL): ConfirmationHook 检测大额操作 (>=10万)
         → SSE 推流: reasoning → tool_call → tool_result → message → [DONE]
```

**Hook 机制**:

| Hook | 位置 | 触发条件 | 功能 |
|---|---|---|---|
| `ContextCompressionHook` | BEFORE_MODEL | 消息数 > 20 | 保留首条(系统提示) + 最近 6 条，压缩上下文 |
| `ConfirmationHook` | AFTER_MODEL | 涉及金额 >= 100,000 元 | 插入人工确认提示 (Human-in-the-loop) |

**ContextCompressionHook 工作原理**:
1. 在每次大模型调用前检查消息历史长度
2. 当消息数超过 `max-messages` (默认 20) 时触发
3. 保留第 1 条消息 (系统提示) + 最近 `keep-after-trim` (默认 6) 条消息
4. 中间的消息被裁剪，避免上下文窗口溢出

**ConfirmationHook 工作原理**:
1. 在大模型生成回复后检查内容
2. 使用正则匹配检测回复中涉及的金额
3. 当金额 >= `required-amount` (默认 100,000 元) 时
4. 在回复末尾追加人工确认提示，要求用户二次确认

**检查点持久化**: 默认使用 `PostgresSaver`，Agent 运行状态持久化到 PostgreSQL，重启不丢失。支持三种模式:
- `postgres` (默认) - 生产环境推荐
- `memory` - 开发调试用
- `redis` - 高性能场景

### 2. Agent System Prompt 详解

Agent 的系统提示词包含以下关键部分:

**角色定位**: 专业金融理财顾问，精通个人理财、投资规划、风险评估和金融产品分析。

**核心原则**:
- 始终以客户利益为先，提供客观、中立的建议
- 明确说明投资有风险，不承诺收益
- 给出的建议应基于数据和事实
- 对于时效性问题，必须使用联网搜索获取最新信息

**工具使用指导**: 17 个工具按场景分类说明，包括:
- 信息查询类: `tavily_web_search`、`query_stock_quote`、`query_fund_nav`
- 计算分析类: `calculate_compound_interest`、`calculate_loan_interest`、`tax_calculator`
- 可视化类: `generate_kline_chart`
- 优化建议类: `portfolio_optimizer`、`risk_questionnaire`、`fund_screener`
- 知识检索类: `search_research_reports`
- 生活金融类: `exchange_rate`、`credit_card_installment`、`insurance_compare`
- 规划类: `savings_goal`、`financial_calendar`、`market_sentiment`

**风险约束**: 严格根据用户风险等级 (R1-R5) 过滤产品推荐:
- R1 保守型: 仅推荐存款、国债、货币基金
- R2 稳健型: 可推荐债券基金、稳健型理财
- R3 平衡型: 可推荐混合基金、指数定投
- R4 进取型: 可推荐股票基金、成长股
- R5 激进型: 可推荐股票、期货等高风险产品

**数据时效性约束**:
- 严禁将 2024 年或更早的数据当作 2026 年数据使用
- 搜索结果中只有旧数据时，必须明确告知用户
- 回答中引用数据时，必须标注数据来源和日期

**个性化建议要求**:
- 当上下文中提供了"用户财务画像"时，必须基于用户的实际持仓和理财目标给出针对性建议
- 回答中应引用用户的真实数据 (持仓金额、配置比例、目标进度等)
- 不得无视用户画像给出泛泛建议

### 3. 金融工具 (17 个)

全部 17 个工具已注册到生产 Agent，LLM 可根据用户意图自由调用:

| # | 工具名 | 功能 | 底层实现 |
|---|---|---|---|
| 1 | `tavily_web_search` | 联网搜索财经资讯 (近 7 天) | Tavily API + `topic: "news"` + `days: 7`，自动入库相关结果 |
| 2 | `calculate_compound_interest` | 复利计算 | 复利公式: A = P(1 + r/n)^(nt) |
| 3 | `calculate_loan_interest` | 贷款月供计算 | 等额本息公式 |
| 4 | `search_research_reports` | 知识库研报检索 | **HybridSearchService** (向量 + 关键词 + RRF 融合) |
| 5 | `risk_questionnaire` | 风险评估问卷 (R1-R5) | 评分算法 → 风险等级 + 配置建议 |
| 6 | `query_stock_quote` | 股票实时行情 | 新浪行情 API (GBK 解码 + Referer 防拒) |
| 7 | `query_fund_nav` | 基金净值查询 | 东方财富基金 API (正则提取) |
| 8 | `generate_kline_chart` | K线图生成 | **真实行情** (新浪 API + 东方财富降级) + XChart 蜡烛图 |
| 9 | `portfolio_optimizer` | 投资组合优化 | **Markowitz 均值-方差** (Commons Math3 SimplexOptimizer) |
| 10 | `fund_screener` | 基金筛选器 | 多维度筛选 (类型/收益率/规模/费率) |
| 11 | `exchange_rate` | 汇率转换 | 实时汇率 API |
| 12 | `tax_calculator` | 个人所得税计算 | 累进税率表 (3%-45% 七级) |
| 13 | `credit_card_installment` | 信用卡分期计算 | 等额手续费公式 |
| 14 | `insurance_compare` | 保险产品对比 | 多维度对比分析 (重疾/医疗/意外/寿险/养老) |
| 15 | `financial_calendar` | 金融日历查询 | 财报发布、分红派息等事件日历 |
| 16 | `savings_goal` | 储蓄目标规划 | 目标倒推算法 (每月需存金额) |
| 17 | `market_sentiment` | 市场情绪指数 | 多指标综合分析 (贪婪/恐惧) |

**K 线图工具详解**:
- 调用新浪行情 API 获取最近 60 个交易日真实 OHLC 数据
- 请求头携带 `Referer: https://finance.sina.com.cn` 防止被拒
- 响应使用 GBK 解码 (新浪 API 特殊编码)
- 新浪 API 失败时自动降级到东方财富 API
- 用 XChart 绘制蜡烛图 PNG，返回图片路径供前端展示

**组合优化工具详解**:
- 基于 Commons Math3 的 `SimplexOptimizer` + `NelderMead` 算法
- 目标函数: 最大化夏普比率 `(Rp - Rf) / σp`
- 约束条件: 权重之和=1、各权重>=0 (不允许做空)
- 多初始点策略 (10 个随机起点) 提升全局收敛性
- 失败时降级返回等权重基准
- 返回: 最优权重 + 预期年化收益 + 预期波动率 + 夏普比率

**个税计算工具**:
- 采用中国个人所得税累进税率表 (7 级: 3%-45%)
- 支持专项扣除 (子女教育/继续教育/大病医疗/住房贷款/住房租金/赡养老人)
- 支持社保公积金扣除
- 返回: 应纳税所得额、适用税率、速算扣除数、应纳税额、税后收入

### 4. RAG 知识库与混合检索

#### 文档摄入

支持 6 种格式: PDF / Excel (.xlsx/.xls) / CSV / Markdown / TXT / 图片 (OCR)。

```
上传文件 → Reader 解析 → TokenTextSplitter 分块 (500 tokens)
         → text-embedding-v3 向量化 (1024 维)
         → 存入 vector_store + knowledge_documents 元数据
```

**Reader 实现**:
- `PdfReader`: 基于 Apache PDFBox
- `ExcelReader`: 基于 Apache POI (支持 .xlsx/.xls)
- `CsvReader`: 逐行解析，自动检测分隔符
- `MarkdownReader`: 保留标题层级结构
- `OcrReader`: 基于 Tess4J (Tesseract OCR)，支持 PNG/JPG/WEBP

#### 混合检索 (Hybrid Search)

`search_research_reports` 工具使用 `HybridSearchService` 进行 RRF 融合检索:

1. **向量语义检索**: pgvector cosine similarity，擅长语义匹配 ("股票下跌" 近似 "股市下挫")
2. **关键词检索**: ILIKE 全文匹配，擅长精确匹配 (基金代码 "026789")
3. **RRF 融合排序**: Reciprocal Rank Fusion (k=60)，公式: `score = 1/(k+rank_vector) + 1/(k+rank_keyword)`

**检索流程**:
```
用户查询 → 同时发起两路检索:
  ├── 向量检索: embedding → pgvector cosine similarity → Top 10
  └── 关键词检索: ILIKE '%keyword%' → Top 110
→ RRF 融合排序 → 取 Top 5 返回
```

#### 联网搜索自动入库

`tavily_web_search` 搜索时自动将相关结果入库:
- 相关性过滤: 标题或内容命中 query 关键词才入库 (`tokenizeQuery` + `isRelevant`)
- URL 去重: 通过 `DocumentMetadataService.existsByUrl` 避免重复入库
- 入库流程: 搜索结果 → 相关性检查 → 向量化 → 存入 vector_store + knowledge_documents

#### 知识库定时更新

`KnowledgeRefreshTask` 每周一 8:00 自动从 Tavily 摄入最新财经要闻:
- 关键词: "A股市场行情"、"本周财经要闻"、"宏观经济政策"、"央行货币政策"、"股市分析"
- 去重逻辑: `existsByUrl` 检查
- 每次搜索限制 `max_results: 5`，避免重复内容过多

### 5. 用户认证与风险偏好

#### JWT 认证流程

```
登录/注册 → JwtService.generateToken(userId, username)
          → 返回 JWT (subject=username, claim.userId=userId, exp=24h)
          → 前端存储到 localStorage (fa_auth)
          → axios 拦截器自动注入 Authorization: Bearer <token>
          → JwtAuthFilter (OncePerRequestFilter) 校验
          → SecurityContext 注入 userId (principal)
```

**JWT 配置**:
- 签名算法: HS256 (HMAC-SHA256)
- 密钥: `advisor.jwt.secret` (至少 32 字节 / 256 位)
- 过期时间: `advisor.jwt.expiration` (默认 86400000ms = 24h)
- Payload: `{sub: username, userId: Long, iat: timestamp, exp: timestamp}`

#### 风险等级 (R1-R5)

Agent 推理时注入风险约束，过滤产品推荐范围:

| 等级 | 类型 | 可推荐产品 |
|---|---|---|
| R1 | 保守型 | 货币基金、国债、银行定期 |
| R2 | 稳健型 | 债券基金、银行理财 |
| R3 | 平衡型 | 混合基金、指数基金 |
| R4 | 成长型 | 股票基金、ETF、QDII |
| R5 | 进取型 | 个股、期货、期权 |

**风险等级解析优先级**:
1. 数据库 `users.risk_level` (用户设置的等级)
2. 请求参数 `riskLevel` (前端传递的等级)
3. 默认 R3 (平衡型)

#### 会话隔离

- 按 `userId` 隔离存储，切换用户自动加载
- 前端会话存储 key: `fa_sessions_${userId}`
- 后端 ChatMemory: 按 `conversationId` 隔离 (包含 userId 前缀)
- 密码安全: BCrypt 哈希存储 (Spring Security 默认强度)

### 6. 投资组合与理财目标

#### 资产管理

**6 种资产类型**: 股票 (stock) / 基金 (fund) / 存款 (deposit) / 债券 (bond) / 现金 (cash) / 其他 (other)

**行情刷新机制**:
- 启动时自动刷新: `PortfolioPriceRefreshRunner` (ApplicationRunner) 在应用启动后执行
- 首次访问实时刷新: `PortfolioController.list` 首次调用时触发 `refreshAllMarketValues`
- 定时刷新: `MarketDataRefreshTask` 交易日 15:30 自动刷新
- 股票/基金类型资产调用 `StockQuoteTool` / `FundNavTool` 获取实时价格
- 刷新后写入 `asset_price_history` 历史快照

**组合汇总**:
- 总成本: 各资产 `amount * cost_price` 之和
- 总市值: 各资产 `amount * current_price` 之和
- 累计盈亏: 总市值 - 总成本
- 按类型分布占比: 各类型市值 / 总市值 * 100%
- 未读预警数: `unreadAlerts` 字段

**批量导入**:
- 支持格式: xlsx / xls / csv
- 解析器: `AssetImportParser`
- Excel 模板下载: `/api/portfolio/template`
- CSV 格式要求: 首行为表头，字段顺序: `type,symbol,name,amount,cost_price,buy_date,notes`

**历史快照**:
- `asset_price_history` 表记录每日行情快照
- 字段: `asset_id`, `user_id`, `symbol`, `snapshot_date`, `price`, `market_value`
- 唯一约束: `UNIQUE(asset_id, snapshot_date)`
- 查询接口: `/api/portfolio/history?days=30` 返回近 N 天市值趋势

#### 理财目标

**5 种目标类型**: 退休 (retirement) / 教育 (education) / 购房 (house) / 应急基金 (emergency_fund) / 自定义 (custom)

**自动计算**:
- 剩余月数: 截止日期 - 当前日期 (月)
- 每月需储蓄: `(target_amount - current_amount) / 剩余月数`
- 达成百分比: `current_amount / target_amount * 100%`

### 7. 定时任务与价格预警

#### 定时任务一览

| 定时任务 | Cron 表达式 | 触发时间 | 功能 |
|---|---|---|---|
| `MarketDataRefreshTask` | `0 30 15 * * MON-FRI` | 交易日 15:30 | 刷新所有资产行情 + 写入历史快照 + 检查价格预警 |
| `KnowledgeRefreshTask` | `0 0 8 * * MON` | 每周一 8:00 | 从 Tavily 摄入最新财经要闻入向量库 |

**MarketDataRefreshTask 执行流程**:
```
15:30 触发 → PortfolioService.refreshAllMarketValues(userId)
           → 遍历所有资产，调用行情 API 更新 current_price/market_value
           → 写入 asset_price_history 当日快照
           → PriceAlertService.checkAlerts(userId) 检查所有 active 预警
           → 触发条件匹配时更新 status=triggered, triggered_at=当前时间
```

**KnowledgeRefreshTask 执行流程**:
```
周一 8:00 触发 → 遍历 5 个关键词
              → Tavily 搜索 (max_results=5, days=7)
              → 相关性过滤 (tokenizeQuery + isRelevant)
              → URL 去重 (existsByUrl)
              → 向量化 + 存入 vector_store + knowledge_documents
```

#### 价格预警服务

`PriceAlertService` 提供完整的价格预警功能:

**预警类型**:
- `above`: 涨破预警 (当前价 >= 阈值价时触发)
- `below`: 跌破预警 (当前价 <= 阈值价时触发)

**预警状态**:
- `active`: 活跃状态，等待触发
- `triggered`: 已触发，等待用户确认

**API 接口**:
- `POST /api/alert` - 创建预警 `{symbol, assetName, alertType, thresholdPrice}`
- `GET /api/alert/list` - 当前用户预警列表
- `DELETE /api/alert/{id}` - 删除预警
- `PUT /api/alert/{id}/read` - 标记已读 (status 改为 triggered)

**Dashboard 集成**:
- 未读预警数 > 0 时，侧边栏"仪表盘"图标显示红点
- 汇总接口 `/api/portfolio/summary` 返回 `unreadAlerts` 字段

### 8. SSE 流式对话协议

后端通过 Server-Sent Events 推送结构化 JSON 事件，前端 `fetch + ReadableStream` 实时渲染。

#### 事件类型

| 事件类型 | 字段 | 说明 |
|---|---|---|
| `reasoning` | `content` | Agent 推理过程 (思考链)，灰色折叠区域 |
| `message` | `content` | 最终回复内容 (Markdown)，实时追加渲染 |
| `tool_call` | `tool` | 工具调用开始，显示工具名称芯片 |
| `tool_result` | `tool`, `result` | 工具调用完成，显示结果摘要 (前 200 字符) |
| `error` | `content` | 错误信息 |
| `[DONE]` | - | 流结束标记 (纯文本，非 JSON) |

#### SSE 数据格式

每条 SSE 事件以 `data:` 开头，格式如下:

```
data: {"type":"reasoning","content":"让我分析一下用户的需求..."}
data: {"type":"tool_call","tool":"query_stock_quote"}
data: {"type":"tool_result","tool":"query_stock_quote","result":"招商银行 35.20 +1.2%"}
data: {"type":"message","content":"根据查询结果，"}
data: {"type":"message","content":"招商银行当前价格为"}
data: {"type":"message","content":"35.20元。"}
data: [DONE]
```

#### 后端 SSE 实现 (ChatController)

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 1. 获取用户信息和风险等级
    Long userId = currentUserId();
    String riskLevel = resolveRiskLevel(userId, request.getRiskLevel());

    // 2. 拼接附件文本
    String enhancedMessage = buildMessageWithFiles(request);

    // 3. 调用 Agent 流式接口
    Flux<NodeOutput> agentFlux = advisorAgent.stream(enhancedMessage, userId, riskLevel);

    // 4. 转换 NodeOutput → SSE 事件
    agentFlux.doOnNext(output -> {
        if (output instanceof StreamingOutput streaming) {
            OutputType type = streaming.getOutputType();
            switch (type) {
                case AGENT_MODEL_STREAMING → emit message 事件
                case AGENT_TOOL_STREAMING → emit tool_call 事件
                case AGENT_TOOL_FINISHED → emit tool_result 事件
                case AGENT_HOOK_STREAMING → emit reasoning 事件
            }
        }
    }).doOnComplete(() -> {
        sink.tryEmitNext("[DONE]");
        sink.tryEmitComplete();
    }).subscribe();

    return sink.asFlux();
}
```

#### 前端 SSE 消费 (chat.js)

```javascript
export function streamChat(message, files, onEvent, signal) {
    return fetch('/api/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify({ message, files }),
        signal  // AbortController.signal
    }).then(async (response) => {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });

            // 按 data: 分割事件
            const lines = buffer.split('data: ');
            buffer = lines.pop(); // 保留不完整行

            for (const line of lines) {
                if (line.trim() === '[DONE]') {
                    onEvent({ type: 'done' });
                    return;
                }
                try {
                    const event = JSON.parse(line);
                    onEvent(event);
                } catch (e) {
                    // 忽略解析失败
                }
            }
        }
    });
}
```

#### 关键设计

- **单次触发保护**: `triggerDone()` 确保 `[DONE]` 只触发一次，避免重复渲染
- **AbortController**: 组件卸载时终止 SSE 连接，`onUnmounted(() => controller.abort())`
- **按 `data:` 分割**: 不使用 `\n` 分割，因为 JSON 内容可能包含换行
- **DEBUG 开关**: `VITE_DEBUG_SSE=true` 打印原始 SSE 分片，便于调试
- **JSON 转义**: 后端 `escapeJson()` 处理 `\n`、`\r`、`\t`、`"`、`\` 等特殊字符

### 9. 个性化推荐机制

Agent 在每次对话时自动注入用户财务画像，实现个性化建议。

#### 画像构建流程

```
用户发送消息 → FinancialAdvisorAgent.stream(message, userId, riskLevel)
             → buildPersonalizedMessage(message, userId, riskLevel)
             → 1. 获取资产组合画像 (PortfolioService.summary)
             → 2. 获取理财目标画像 (GoalService.summary)
             → 3. 拼接风险约束前缀
             → 4. 返回增强后的消息
```

#### 画像内容示例

```
[用户财务画像]
当前持仓总成本：500000.00 元，估算市值：523000.00 元，累计盈亏：23000.00 元。
资产配置：股票 45.0%（235000元）、基金 35.0%（183000元）、存款 20.0%（105000元）。
理财目标：
- 退休目标：目标 2000000.00 元，已达成 26.15%（523000元），还需每月储蓄 12350.00 元。

[系统提示：当前用户风险等级为 R3 - 平衡型（可接受中等风险，偏好混合型基金、指数定投）。
请在给出投资建议时严格匹配此风险等级，不得推荐超出其风险承受能力的产品。]

用户原始问题...
```

#### 降级策略

- 获取资产组合失败/为空 → 跳过该部分
- 获取理财目标失败/为空 → 跳过该部分
- userId 为 null → 不拼接画像
- 所有画像都为空 → 仅保留风险约束前缀
- 所有降级都不抛异常，绝不影响主流程

### 10. 可观测性

| 组件 | 端点/端口 | 说明 |
|---|---|---|
| Actuator | `/actuator/health` | 健康检查 |
| Actuator | `/actuator/prometheus` | Prometheus 指标 |
| Actuator | `/actuator/metrics` | Spring Boot 指标 |
| Actuator | `/actuator/info` | 应用信息 |
| Prometheus UI | `localhost:9090` | 指标查询 |
| Grafana | `localhost:3001` | 6 面板仪表盘 |

**Grafana 面板**:
1. Agent 调用次数 (Counter)
2. 工具平均耗时 (Gauge)
3. Token 消耗 (Counter)
4. HTTP QPS (Rate)
5. JVM 内存使用 (Gauge)
6. API P50/P95/P99 延迟 (Histogram)

**Prometheus 抓取配置** (`deploy/prometheus.yml`):
```yaml
scrape_configs:
  - job_name: 'financial-advisor'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['backend:8080']
```

---

## 数据库表结构

所有环境统一使用 `financial_rag` 数据库，共 **8 张表**:

### users — 用户表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `username` | VARCHAR(64) UNIQUE | 用户名 |
| `password_hash` | VARCHAR(255) | BCrypt 哈希密码 |
| `risk_level` | VARCHAR(8) DEFAULT 'R3' | 风险等级 (R1-R5) |
| `created_at` | BIGINT | 创建时间 |

### assets — 资产表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `user_id` | BIGINT | 所属用户 |
| `type` | VARCHAR(16) | 资产类型 (stock/fund/deposit/bond/cash/other) |
| `symbol` | VARCHAR(32) | 代码 (如 sh600036, 012921) |
| `name` | VARCHAR(128) | 名称 |
| `amount` | NUMERIC(20,4) | 数量 |
| `cost_price` | NUMERIC(20,4) | 成本价 |
| `buy_date` | DATE | 买入日期 |
| `notes` | TEXT | 备注 |
| `current_price` | NUMERIC(20,4) | 当前价格 (行情刷新更新) |
| `market_value` | NUMERIC(20,4) | 当前市值 |
| `price_updated_at` | BIGINT | 行情更新时间戳 |
| `created_at` | BIGINT | 创建时间 |

### goals — 理财目标表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `user_id` | BIGINT | 所属用户 |
| `type` | VARCHAR(24) | 目标类型 (retirement/education/house/emergency_fund/custom) |
| `target_amount` | NUMERIC(20,2) | 目标金额 |
| `current_amount` | NUMERIC(20,2) | 当前金额 |
| `deadline` | DATE | 截止日期 |
| `monthly_contribution` | NUMERIC(20,2) | 每月储蓄 |
| `notes` | TEXT | 备注 |
| `created_at` | BIGINT | 创建时间 |

### vector_store — 向量表 (pgvector)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID (PK) | 主键 |
| `content` | TEXT | 文档内容 |
| `metadata` | JSON | 元数据 |
| `embedding` | vector(1024) | 向量嵌入 (text-embedding-v3, 1024 维) |

### knowledge_documents — 文档元数据表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) (PK) | 主键 |
| `filename` | VARCHAR(255) | 文件名 |
| `file_type` | VARCHAR(20) | 文件类型 (pdf/xlsx/csv/md/txt/png/jpg/web) |
| `category` | VARCHAR(50) | 分类 |
| `status` | VARCHAR(20) | 状态 (processing/indexed/failed) |
| `chunk_count` | INT | 分块数 |
| `source_url` | VARCHAR(2048) | 来源 URL (用于去重) |
| `source_type` | VARCHAR(20) | 来源类型 (upload/web/internet) |
| `upload_time` | TIMESTAMP | 上传时间 |

### asset_price_history — 资产历史快照表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `asset_id` | BIGINT | 资产 ID |
| `user_id` | BIGINT | 用户 ID |
| `symbol` | VARCHAR(32) | 资产代码 |
| `snapshot_date` | DATE | 快照日期 |
| `price` | NUMERIC(20,4) | 当日价格 |
| `market_value` | NUMERIC(20,4) | 当日市值 |
| `created_at` | BIGINT | 创建时间 |

唯一约束: `UNIQUE(asset_id, snapshot_date)`

### price_alerts — 价格预警表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `user_id` | BIGINT | 用户 ID |
| `symbol` | VARCHAR(32) | 资产代码 |
| `asset_name` | VARCHAR(128) | 资产名称 |
| `alert_type` | VARCHAR(10) | 预警类型 (above/below) |
| `threshold_price` | NUMERIC(20,4) | 阈值价格 |
| `status` | VARCHAR(20) | 状态 (active/triggered) |
| `created_at` | BIGINT | 创建时间 |
| `triggered_at` | BIGINT | 触发时间 |

### SPRING_AI_CHAT_MEMORY — 对话记忆表 (Spring AI 自动管理)

由 `JdbcChatMemoryRepository` 自动创建和管理，持久化对话历史。

配置: `spring.ai.chat.memory.repository.jdbc.initialize-schema: always`

---

## 快速启动

### 前置条件

- **JDK 17+** (推荐 Temurin)
- **Node.js 20+**
- **Maven 3.9+**
- **Docker** (可选，用于 PostgreSQL pgvector)
- **阿里云百炼 API Key** (https://bailian.console.aliyun.com)
- **Tavily API Key** (https://tavily.com，可选)

### 1. 设置环境变量

```powershell
# Windows PowerShell
$env:AI_DASHSCOPE_API_KEY="your-dashscope-api-key"
$env:TAVILY_API_KEY="your-tavily-api-key"
```

```bash
# Linux/macOS
export AI_DASHSCOPE_API_KEY="your-dashscope-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

### 2. 启动 PostgreSQL

```powershell
# 方式一: Docker (推荐)
docker compose up -d postgres

# 方式二: 本地 PostgreSQL
psql -U postgres -h localhost -c "CREATE DATABASE financial_rag;"
psql -U postgres -h localhost -d financial_rag -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run -pl advisor-bootstrap -am -Dspring-boot.run.profiles=dev
```

后端启动后监听 `http://localhost:8080`，自动初始化所有数据库表。

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端启动后监听 `http://localhost:3000`。

### 5. Docker 一键部署

```powershell
docker compose up -d --build
```

| 服务 | 地址 | 说明 |
|---|---|---|
| 前端 | `http://localhost:3000` | Vue 3 SPA (Nginx) |
| 后端 API | `http://localhost:8080` | Spring Boot |
| PostgreSQL | `localhost:5432` | pgvector 向量库 |
| Prometheus | `http://localhost:9090` | 指标监控 |
| Grafana | `http://localhost:3001` | 可视化仪表盘 |

---

## API 接口文档

### 统一响应格式

```json
{
    "code": 200,
    "message": "success",
    "data": { ... },
    "timestamp": "2026-07-15T10:00:00"
}
```

### 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 `{username, password}` → `{token, userId, username, riskLevel}` |
| POST | `/api/auth/login` | 登录 `{username, password}` → `{token, userId, username, riskLevel}` |
| GET | `/api/auth/profile` | 获取当前用户资料 (需 JWT) |
| PUT | `/api/auth/risk-level` | 更新风险等级 `{riskLevel: "R1"~"R5"}` (需 JWT) |

### 对话接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat/stream` | SSE 流式对话 `{message, files?, riskLevel?}` → SSE 事件流 (需 JWT) |
| POST | `/api/chat/call` | 非流式对话 `{message, files?, riskLevel?}` → 直接返回文本 (需 JWT) |
| POST | `/api/chat/upload` | 聊天文件上传 (multipart/form-data, 最大 50MB) (需 JWT) |

### 知识库接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/documents/upload` | 上传文档 (PDF/Excel/CSV/MD/TXT/图片) (需 JWT) |
| POST | `/api/documents/ingest-web` | 联网导入 `{query}` (Tavily 搜索 + 自动入库) (需 JWT) |
| GET | `/api/documents/list` | 文档列表 (需 JWT) |
| GET | `/api/documents/search?keyword=` | 关键词搜索 (需 JWT) |
| GET | `/api/documents/category/{category}` | 按分类查询 (需 JWT) |
| GET | `/api/documents/stats` | 统计信息 (文档数/分块数/类型分布) (需 JWT) |
| DELETE | `/api/documents/{id}` | 删除文档 (需 JWT) |

### 资产组合接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/portfolio/list` | 资产列表 (stock/fund 实时刷新行情) (需 JWT) |
| POST | `/api/portfolio` | 创建资产 `{type, symbol, name, amount, costPrice, buyDate, notes}` (需 JWT) |
| PUT | `/api/portfolio/{id}` | 更新资产 (需 JWT) |
| DELETE | `/api/portfolio/{id}` | 删除资产 (需 JWT) |
| GET | `/api/portfolio/summary` | 组合汇总 (含 `unreadAlerts` 预警数) (需 JWT) |
| POST | `/api/portfolio/import` | 批量导入 xlsx/xls/csv (multipart/form-data) (需 JWT) |
| GET | `/api/portfolio/template` | 下载 Excel 模板 (需 JWT) |
| GET | `/api/portfolio/history?days=30` | 组合市值历史趋势 (需 JWT) |

### 理财目标接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/goal/list` | 目标列表 (需 JWT) |
| POST | `/api/goal` | 创建目标 `{type, targetAmount, currentAmount, deadline, monthlyContribution, notes}` (需 JWT) |
| PUT | `/api/goal/{id}` | 更新目标 (需 JWT) |
| DELETE | `/api/goal/{id}` | 删除目标 (需 JWT) |
| GET | `/api/goal/summary` | 目标汇总 (进度/剩余月数/每月需储蓄) (需 JWT) |

### 会话管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/session/create` | 创建会话 (需 JWT) |
| GET | `/api/session/{sessionId}` | 获取会话 (需 JWT) |
| GET | `/api/session/list` | 会话列表 (需 JWT) |
| DELETE | `/api/session/{sessionId}` | 删除会话 (需 JWT) |

### 市场行情接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/market/indices` | 四大指数 (上证/深证/创业板/科创50) 实时点位与涨跌幅 (需 JWT) |
| GET | `/api/market/sentiment` | 市场情绪指数 (贪婪/恐惧) (需 JWT) |
| GET | `/api/market/calendar` | 本周金融日历 (需 JWT) |

### 投资分析接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/analysis/kline?symbol=sh600036` | 生成真实 K 线图 (返回图表路径) (需 JWT) |
| GET | `/api/analysis/optimize` | 组合优化 (Markowitz 最优权重 + 预期收益/波动率/夏普) (需 JWT) |
| GET | `/api/analysis/risk-return` | 风险收益散点图数据 (各资产年化收益与波动率) (需 JWT) |

### 工具箱接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tool/tax` | 个税计算 `{annualIncome, socialInsurance, specialDeduction}` (需 JWT) |
| POST | `/api/tool/fund-screener` | 基金筛选 `{fundType, minReturn, maxRisk}` (需 JWT) |
| GET | `/api/tool/exchange-rate?from=USD&to=CNY&amount=100` | 汇率换算 (需 JWT) |
| POST | `/api/tool/installment` | 信用卡分期 `{totalAmount, months, annualRate}` (需 JWT) |

### 价格预警接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/alert` | 创建预警 `{symbol, assetName, alertType, thresholdPrice}` (需 JWT) |
| GET | `/api/alert/list` | 当前用户预警列表 (需 JWT) |
| DELETE | `/api/alert/{id}` | 删除预警 (需 JWT) |
| PUT | `/api/alert/{id}/read` | 标记已读 (需 JWT) |

---

## 前端架构

### 路由与页面

| 路径 | 组件 | 说明 | 侧边栏图标 | 路由守卫 |
|---|---|---|---|---|
| `/` | - | 重定向至 `/chat` | - | - |
| `/login` | `LoginView` | 登录/注册页 | - | `guest: true` |
| `/chat` | `ChatView` | 智能对话 (SSE 流式) | 智能对话 | `requiresAuth` |
| `/portfolio` | `PortfolioView` | 资产组合管理 | 我的资产 | `requiresAuth` |
| `/goal` | `GoalView` | 理财目标管理 | 理财目标 | `requiresAuth` |
| `/market` | `MarketView` | 市场行情 (指数/情绪/日历) | 市场行情 | `requiresAuth` |
| `/analysis` | `AnalysisView` | 投资分析 (K线/优化/散点图) | 投资分析 | `requiresAuth` |
| `/toolbox` | `ToolboxView` | 工具箱 (个税/筛选/汇率/分期) | 工具箱 | `requiresAuth` |
| `/knowledge` | `KnowledgeView` | 知识库管理 | 知识库 | `requiresAuth` |
| `/dashboard` | `DashboardView` | 仪表盘 (趋势/AI建议/预警) | 仪表盘 | `requiresAuth` |
| `/settings` | `SettingsView` | 系统设置 | 设置 | `requiresAuth` |

**路由守卫逻辑**:
- `requiresAuth: true` 且未登录 → 重定向到 `/login?redirect=原路径`
- `guest: true` 且已登录 → 重定向到 `/chat`
- 页面标题自动设置: `document.title = "${meta.title} - 金融理财顾问"`

### 状态管理

| Store | 持久化 Key | 职责 |
|---|---|---|
| `auth` | `fa_auth` | JWT token + 用户信息 (userId/username/riskLevel) |
| `sessions` | `fa_sessions_{userId}` | 会话列表 (按用户隔离，切换用户自动加载) |
| `settings` | `fa_settings` | 主题 (暗色/亮色) / 风险等级 / 推理过程显示开关 |

### API 层

| 文件 | 职责 |
|---|---|
| `http.js` | axios 实例 (baseURL=/api, JWT 拦截器, 401 自动跳转登录) |
| `auth.js` | 认证 API (login/register/profile/riskLevel) |
| `chat.js` | 对话 API (streamChat/callChat/uploadFile) |
| `documents.js` | 知识库 API (upload/ingestWeb/list/search/stats/delete) |
| `portfolio.js` | 资产 API (CRUD/summary/import/template/history) |
| `goal.js` | 目标 API (CRUD/summary) |
| `session.js` | 会话 API (create/get/list/delete) |
| `market.js` | 市场行情 API (indices/sentiment/calendar) |
| `analysis.js` | 投资分析 API (kline/optimize/riskReturn) |
| `tool.js` | 工具箱 API (tax/fundScreener/exchangeRate/installment) |

### 字体规范

| 元素 | 字号 | 说明 |
|---|---|---|
| 用户消息 / AI 回复 | 18px | 主要对话内容 |
| 推理过程文本 | 16px | 折叠区域的思考链 |
| 工具调用芯片 | 15px | tool_call/tool_result 标签 |
| 建议问题 | 16px | 快捷提问按钮 |
| 输入框 / 发送按钮 | 20px | 消息输入区域 |

### SSE 流式渲染

前端 `ChatView.vue` 中的 SSE 渲染流程:

```
用户发送消息 → streamChat(message, files, onEvent, signal)
             → onEvent 回调:
                 ├── reasoning → 追加到推理链区域 (灰色折叠)
                 ├── tool_call → 显示工具名称芯片 (蓝色)
                 ├── tool_result → 显示结果摘要 (绿色)
                 ├── message → 追加到 Markdown 渲染区域 (实时)
                 ├── done → 触发 triggerDone() 标记结束
                 └── error → 显示错误信息 (红色)
```

**AbortController 使用**:
```javascript
const controller = new AbortController();
streamChat(message, files, onEvent, controller.signal);

// 组件卸载或用户切换路由时
onUnmounted(() => controller.abort());
```

---

## 数据存储架构

| 数据类型 | 存储方式 | 表名/Key | 说明 |
|---|---|---|---|
| 对话历史 | PostgresSaver + JDBC ChatMemory | `GraphThread` / `GraphCheckpoint` / `SPRING_AI_CHAT_MEMORY` | 重启不丢失 |
| 向量数据 | pgvector | `vector_store` | 1024 维语义检索 |
| 文档元数据 | PostgreSQL | `knowledge_documents` | 文件管理/分类/状态 |
| 用户/认证 | PostgreSQL | `users` | BCrypt 密码 + R1-R5 |
| 资产记录 | PostgreSQL | `assets` | 含实时行情字段 |
| 理财目标 | PostgreSQL | `goals` | 按 user_id 隔离 |
| 历史快照 | PostgreSQL | `asset_price_history` | 每日行情快照 |
| 价格预警 | PostgreSQL | `price_alerts` | 涨破/跌破预警 |
| 前端认证 | localStorage | `fa_auth` | JWT token |
| 前端会话 | localStorage | `fa_sessions_{userId}` | 按用户隔离 |
| 前端设置 | localStorage | `fa_settings` | 主题/偏好 |

---

## 环境变量

### 后端

| 变量名 | 说明 | 必填 | 默认值 |
|---|---|---|---|
| `AI_DASHSCOPE_API_KEY` | DashScope API Key | 是 | - |
| `TAVILY_API_KEY` | Tavily 搜索 API Key | 否 | 空 (联网搜索不可用) |
| `DB_PASSWORD` | PostgreSQL 密码 | 否 | `root` |
| `DB_HOST` | PostgreSQL 主机 | 否 | `localhost` |
| `PGVECTOR_HOST` | pgvector 主机 | 否 | `localhost` |
| `JWT_SECRET` | JWT 签名密钥 (至少 32 字节) | 否 | 内置默认值 |

### 前端 (`.env.development`)

| 变量名 | 说明 | 默认值 |
|---|---|---|
| `VITE_DEBUG_SSE` | 打印原始 SSE 分片 | `false` |

---

## 完整配置参考

### application.yml 核心配置

```yaml
server:
  port: 8080

spring:
  application:
    name: financial-advisor
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        model: qwen3.6-plus
        options:
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-v3
    vectorstore:
      pgvector:
        host: ${PGVECTOR_HOST:localhost}
        port: 5432
        database: financial_rag
        username: postgres
        password: ${DB_PASSWORD:root}
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: always  # 自动创建 SPRING_AI_CHAT_MEMORY 表
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/financial_rag
    username: postgres
    password: ${DB_PASSWORD:root}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

tavily:
  api-key: ${TAVILY_API_KEY:}

# 框架配置
advisor:
  context-compression:
    max-messages: 20          # 上下文压缩阈值
    keep-after-trim: 6        # 压缩后保留消息数
  confirmation:
    required-amount: 100000   # 大额确认阈值 (元)
  checkpoint:
    saver: postgres           # memory | postgres | redis
  jwt:
    secret: ${JWT_SECRET:financial-advisor-super-secret-key-2026-please-change-in-production}
    expiration: 86400000      # 24 小时 (毫秒)
```

---

## 测试

```powershell
# 运行全部测试
cd backend
mvn test

# 运行单个模块测试
mvn test -pl advisor-tool -am
mvn test -pl advisor-portfolio -am
mvn test -pl advisor-api -am
mvn test -pl advisor-agent -am

# 跳过测试打包
mvn clean package -DskipTests=true
```

### 测试覆盖

| 模块 | 测试类 | 测试数 | 说明 |
|---|---|---|---|
| advisor-tool | `KlineFetcherTest` | 5 | JSON解析/symbol透传/降级/空数组 |
| advisor-tool | `PortfolioOptimizerToolTest` | 5 | 权重和=1/非负/夏普>等权重/单资产 |
| advisor-tool | `FinancialToolsTest` | 4 | 联网搜索/复利/贷款/研报检索 |
| advisor-portfolio | `PriceAlertServiceTest` | 7 | CRUD/触发逻辑/统计 |
| advisor-portfolio | `MarketDataRefreshTaskTest` | 3 | 调用顺序/容错/异常隔离 |
| advisor-portfolio | `PortfolioServiceTest` | 多个 | 组合管理/行情刷新 |
| advisor-portfolio | `AssetImportParserTest` | 多个 | Excel/CSV 解析 |
| advisor-portfolio | `PortfolioPriceRefreshRunnerTest` | 多个 | 启动刷新 |
| advisor-api | `ChatControllerIntegrationTest` | 多个 | SSE 流式/非流式 |
| advisor-api | `AuthControllerIntegrationTest` | 多个 | 登录/注册/JWT |
| advisor-agent | `AgentEndToEndTest` | 多个 | Agent 端到端 |
| advisor-agent | `ConfirmationHookTest` | 多个 | 大额确认 |
| advisor-agent | `ContextCompressionTest` | 多个 | 上下文压缩 |

---

## 部署指南

### 开发环境

```powershell
# 1. 启动 PostgreSQL
docker compose up -d postgres

# 2. 设置环境变量
$env:AI_DASHSCOPE_API_KEY="your-key"
$env:TAVILY_API_KEY="your-key"

# 3. 启动后端
cd backend
mvn spring-boot:run -pl advisor-bootstrap -am -Dspring-boot.run.profiles=dev

# 4. 启动前端
cd frontend
npm install
npm run dev
```

### Docker 一键部署

```powershell
# 设置环境变量
$env:AI_DASHSCOPE_API_KEY="your-key"
$env:TAVILY_API_KEY="your-key"

# 构建并启动所有服务
docker compose up -d --build
```

**docker-compose.yml 完整配置**:

```yaml
version: '3.8'

services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - AI_DASHSCOPE_API_KEY=${AI_DASHSCOPE_API_KEY}
      - TAVILY_API_KEY=${TAVILY_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend

  postgres:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: financial_rag
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: root
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./deploy/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports:
      - "3001:3000"
    depends_on:
      - prometheus

volumes:
  pgdata:
```

**后端 Dockerfile**:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY advisor-bootstrap/target/advisor-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**前端 Dockerfile** (多阶段构建):
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 生产环境 (手动)

```powershell
# 后端
cd backend && mvn clean package -DskipTests=true
java -jar advisor-bootstrap/target/advisor-bootstrap-1.0.0.jar --spring.profiles.active=prod

# 前端
cd frontend && npm install && npm run build
# 将 dist/ 部署到 Nginx
```

### Nginx 反向代理配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;  # SPA fallback
    }

    # API 代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # SSE 支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

---

## 安全设计

### 认证流程

```
┌─────────┐     POST /api/auth/login      ┌─────────────┐
│  浏览器  │  ─────────────────────────→  │ AuthController│
│         │  {username, password}          │              │
│         │                                │ UserService  │
│         │  ←─────────────────────────  │ .authenticate │
│         │  {token, userId, username}     │              │
└────┬────┘                                └──────────────┘
     │
     │  localStorage.setItem('fa_auth', token)
     │
     │  后续请求:
     │  Authorization: Bearer <token>
     │
     ▼
┌─────────────────┐     ┌───────────────┐     ┌─────────────┐
│ JwtAuthFilter   │ ──→ │ JwtService    │ ──→ │ Security    │
│ (拦截所有请求)   │     │ .validateToken│     │ Context     │
│                 │     │ .extractUserId│     │ (注入userId) │
└─────────────────┘     └───────────────┘     └─────────────┘
```

### 安全措施

| 措施 | 实现 | 说明 |
|---|---|---|
| **密码加密** | BCrypt | Spring Security 默认强度 (10 rounds) |
| **JWT 签名** | HS256 (HMAC-SHA256) | 密钥至少 32 字节 |
| **JWT 过期** | 24 小时 | 可配置 `advisor.jwt.expiration` |
| **CORS** | Spring Security 配置 | 仅允许前端域名 |
| **SQL 注入** | JPA / PreparedStatement | 参数化查询 |
| **XSS** | Vue 自动转义 | `{{ }}` 模板语法 |
| **CSRF** | JWT 无状态 | 不使用 Cookie，无需 CSRF |
| **文件上传** | 大小限制 50MB | `spring.servlet.multipart.max-file-size` |
| **API 限流** | 可扩展 | 可通过 Spring Cloud Gateway 添加 |

### 敏感配置

- `AI_DASHSCOPE_API_KEY`: 阿里云百炼 API Key，不要提交到代码仓库
- `TAVILY_API_KEY`: Tavily 搜索 API Key
- `JWT_SECRET`: JWT 签名密钥，生产环境必须更换
- `DB_PASSWORD`: 数据库密码，生产环境必须更换

---

## 扩展与自定义

### 添加新金融工具

**步骤 1**: 创建工具类

```java
// backend/advisor-tool/src/main/java/.../tool/finance/MyNewTool.java
package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyNewTool {

    @Tool(description = "工具功能描述，LLM 根据此描述决定何时调用")
    public String my_tool_method(
            @ToolParam(description = "参数1说明") String param1,
            @ToolParam(description = "参数2说明") int param2) {
        // 实现逻辑
        return "结果";
    }
}
```

**步骤 2**: 注册到 Agent

```java
// backend/advisor-agent/src/main/java/.../agent/core/FinancialAdvisorAgent.java
// 构造函数注入 MyNewTool
public FinancialAdvisorAgent(..., MyNewTool myNewTool) {
    this.agent = ReactAgent.builder()
            // ...
            .methodTools(financialTools, ..., myNewTool)  // 追加到工具列表
            .build();
}
```

**步骤 3**: 更新 System Prompt

在 `FinancialAdvisorAgent` 的 system prompt 中添加工具说明:

```
18. my_tool_method - 工具功能描述，调用场景说明
```

**步骤 4**: (可选) 添加前端 API 和页面

如果需要独立的前端入口，添加 Controller 和前端页面。

### 添加新前端页面

**步骤 1**: 创建 Vue 组件

```vue
<!-- frontend/src/views/MyNewView.vue -->
<template>
    <n-layout content-style="padding: 24px;">
        <n-h1>新页面</n-h1>
        <!-- 页面内容 -->
    </n-layout>
</template>

<script setup>
import { NLayout, NH1 } from 'naive-ui'
// 显式导入所有 Naive UI 组件
</script>
```

**步骤 2**: 注册路由

```javascript
// frontend/src/main.js
import MyNewView from './views/MyNewView.vue'

const routes = [
    // ...
    { path: '/my-new-page', name: 'MyNewPage', component: MyNewView, meta: { title: '新页面', requiresAuth: true } }
]
```

**步骤 3**: 添加侧边栏导航

```javascript
// frontend/src/components/SessionSidebar.vue
import { NewIcon } from '@vicons/ionicons5'

const navItems = [
    // ...
    { path: '/my-new-page', label: '新页面', icon: NewIcon }
]
```

**步骤 4**: (可选) 添加 API 模块

```javascript
// frontend/src/api/myNewApi.js
import http from './http'

export function getData() {
    return http.get('/api/my-endpoint')
}
```

### 切换大模型

修改 `application.yml`:

```yaml
spring:
  ai:
    dashscope:
      chat:
        model: qwen-max  # 或 qwen-turbo, qwen-plus
```

可选模型:
- `qwen3.6-plus` (默认) - 平衡性能与成本
- `qwen-max` - 最强能力，成本较高
- `qwen-turbo` - 快速响应，成本较低

---

## 常见问题

**Q: 前端组件报错 "Failed to resolve component: n-xxx"**
Naive UI 组件必须在 `<script setup>` 中显式 import。例如:
```javascript
import { NButton, NInput, NDataTable } from 'naive-ui'
```

**Q: 前端页面空白或组件不渲染**
Vite 缓存过期: 删除 `node_modules/.vite`，执行 `npx vite --force`，浏览器硬刷新 (Ctrl+Shift+R)。

**Q: 数据库连接失败**
所有环境统一使用 `financial_rag` 数据库。确保 PostgreSQL 已启动且 pgvector 扩展已安装:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**Q: 如何切换检查点存储方式**
修改 `application.yml` 中 `advisor.checkpoint.saver`: `postgres` (默认) / `memory` / `redis`。

**Q: 如何修改大模型**
修改 `spring.ai.dashscope.chat.model`: `qwen3.6-plus` / `qwen-max` / `qwen-turbo`。

**Q: 手动设置 Content-Type: multipart/form-data 导致上传失败**
不要在 axios 中手动设置 Content-Type，让浏览器自动处理 boundary。使用共享 `http` 实例直接传 `FormData`:
```javascript
// 正确
http.post('/api/documents/upload', formData)

// 错误
http.post('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }  // 不要手动设置
})
```

**Q: SSE 流式对话在路由切换后报网络错误**
`chat.js` 使用 AbortController 在组件卸载时终止 SSE 连接，确保 `onUnmounted` 中调用 `controller.abort()`。

**Q: 跨用户看到其他人的聊天记录**
会话存储使用 `fa_sessions_${userId}` 隔离。如看到异常数据，清除 localStorage 后重新登录。

**Q: 前端构建后刷新页面 404**
Nginx 配置需要添加 SPA fallback:
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

**Q: 后端启动报 "advisor.jwt.secret 必须至少 32 字节"**
JWT 密钥长度不足。设置环境变量 `JWT_SECRET` 为至少 32 个字符的字符串，或修改 `application.yml` 中的默认值。

**Q: K 线图生成失败**
检查网络是否能访问新浪行情 API (`https://hq.sinajs.cn`)。如果新浪 API 不可用，系统会自动降级到东方财富 API。

**Q: 组合优化返回等权重**
Markowitz 优化失败时会降级返回等权重基准。可能原因:
- 资产数量不足 (至少 2 个)
- 历史数据不足
- 协方差矩阵奇异

**Q: 如何查看 SSE 原始数据**
设置前端环境变量 `VITE_DEBUG_SSE=true`，浏览器控制台会打印所有 SSE 分片。

**Q: 如何备份数据库**
```powershell
# 备份
docker exec postgres pg_dump -U postgres financial_rag > backup.sql

# 恢复
docker exec -i postgres psql -U postgres financial_rag < backup.sql
```

**Q: 如何查看 Prometheus 指标**
访问 `http://localhost:9090`，使用 PromQL 查询:
```
# Agent 调用次数
agent_calls_total

# 工具平均耗时
tool_duration_seconds_sum / tool_duration_seconds_count

# HTTP QPS
rate(http_server_requests_seconds_count[1m])
```

---

## License

Apache License 2.0
