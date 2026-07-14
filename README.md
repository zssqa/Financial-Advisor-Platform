# 金融理财顾问平台 (Financial Advisor Platform)

基于 Spring AI Alibaba 框架构建的金融领域多智能体对话平台，采用 ReAct 模式实现智能理财顾问，集成大模型推理、联网搜索、RAG 知识库、工具调用、流式对话、投资组合管理和理财目标规划等能力，为个人投资者提供专业的金融理财咨询服务。

---

## 目录

- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [模块依赖关系](#模块依赖关系)
- [核心功能](#核心功能)
  - [1. 多 Agent 协作模式](#1-多-agent-协作模式)
  - [2. Agent 工作流程](#2-agent-工作流程)
  - [3. 金融工具 (16 个)](#3-金融工具-16-个)
  - [4. RAG 知识库](#4-rag-知识库)
  - [5. 用户认证与风险偏好](#5-用户认证与风险偏好)
  - [6. 投资组合与理财目标](#6-投资组合与理财目标)
  - [7. 对话增强](#7-对话增强)
  - [8. 可观测性](#8-可观测性)
- [数据库表结构](#数据库表结构)
- [快速启动](#快速启动)
  - [前置条件](#前置条件)
  - [1. 设置环境变量](#1-设置环境变量)
  - [2. 启动 PostgreSQL](#2-启动-postgresql)
  - [3. 启动后端](#3-启动后端)
  - [4. 启动前端](#4-启动前端)
  - [5. Docker 一键部署](#5-docker-一键部署)
- [API 接口文档](#api-接口文档)
  - [统一响应格式](#统一响应格式)
  - [认证接口](#认证接口)
  - [对话接口](#对话接口)
  - [知识库接口](#知识库接口)
  - [资产组合接口](#资产组合接口)
  - [理财目标接口](#理财目标接口)
  - [会话管理接口](#会话管理接口)
- [前端架构](#前端架构)
  - [路由与页面](#路由与页面)
  - [状态管理](#状态管理)
  - [API 层](#api-层)
  - [组件库](#组件库)
  - [Vite 配置](#vite-配置)
- [数据存储架构](#数据存储架构)
- [架构说明](#架构说明)
- [环境变量](#环境变量)
- [测试](#测试)
- [部署指南](#部署指南)
- [常见问题](#常见问题)
- [License](#license)

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           用户浏览器 (Vue 3 + Naive UI)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ 智能对话  │ │ 我的资产  │ │ 理财目标  │ │  知识库   │ │  仪表盘   │     │
│  └────┬───── └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────────┘     │
│       └────────────┴────────────┴────────────────────────┘            │
│                              │ axios (JWT)                              │
──────────────────────────────┼─────────────────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────┼─────────────────────────────────────────┐
│                    Spring Boot 后端 (8 模块)                             │
│  ┌──────────────────────────┴──────────────────────────────────────  │
│  │                    advisor-api (API 接口层)                       │  │
│  │  ChatController │ DocumentController │ SessionController         │  │
│  ──────────────────────────┬──────────────────────────────────────┘  │
│                             │                                          │
│  ┌──────────────┐  ┌────────┴────────┐  ┌──────────────────────────┐ │
│  │ advisor-user │  │ advisor-portfolio│  │      advisor-rag         │ │
│  │  JWT 认证    │  │  资产/目标 CRUD   │  │  RAG 知识库 / 向量检索    │ │
│  └──────────────┘  └─────────────────┘  └──────────────────────────┘ │
│                             │                                          │
│  ┌──────────────────────────┴──────────────────────────────────────┐  │
│  │                   advisor-agent (Agent 核心层)                    │  │
│  │  FinancialAdvisorAgent (ReAct) → 多 Agent 编排 → Hook 机制       │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │                                          │
│  ──────────────────────────┴──────────────────────────────────────┐  │
│  │                    advisor-tool (16 个金融工具)                    │  │
│  │  Tavily搜索 │ 基金净值 │ 股票行情 │ 汇率 │ 风险评估 │ 组合优化 ... │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                             │                                          │
│  ┌──────────────────────────┴──────────────────────────────────────┐  │
│  │                   advisor-common (公共模块)                       │  │
│  │  ApiResponse │ AdvisorConstants │ SessionManager                 │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ JDBC / pgvector
──────────────────────────────┼────────────────────────────────────────┐
│                      PostgreSQL + pgvector                             │
│  ┌────────── ┌──────────────┐ ──────────┐ ┌──────┐ ┌──────┐       │
│  │  users   │ │vector_store  │ │knowledge │ │asset │ │ goal │       │
│  │ 认证/风控 │ │ 向量语义检索   │ │_documents│ │ 资产  │ │ 目标  │       │
│  └──────────┘ └──────────────┘ └──────────┘ └──────┘ └──────┘       │
└───────────────────────────────────────────────────────────────────────┘
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
| **嵌入模型** | text-embedding-v3 (DashScope) | - | 1536 维向量嵌入 |
| **向量数据库** | PostgreSQL + pgvector | pg16 | cosine similarity 语义检索 |
| **认证** | JWT (jjwt) | 0.12.6 | HS256 签名，24h 过期 |
| **搜索引擎** | Tavily API | - | 实时财经资讯，限定近 7 天 |
| **可观测性** | Actuator + Prometheus + Grafana | - | 6 面板监控仪表盘 |
| **Agent 持久化** | MemorySaver / PostgresSaver | - | 可配置切换 |
| **配置中心** | Nacos (可选) | ^3.1+ | 动态配置刷新 |

### 前端技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|---|---|---|---|
| **前端框架** | Vue 3 (Composition API) | 3.5.13 | `<script setup>` 语法 |
| **前端路由** | Vue Router | 4.5.0 | History 模式 + 路由守卫 |
| **前端组件库** | Naive UI | 2.40.3 | 暗色主题、表单、日期选择器 |
| **图标库** | @vicons/ionicons5 | 0.12.0 | 侧边栏/操作图标 |
| **构建工具** | Vite | 5.4.11 | HMR 热更新、代理、dedupe |
| **HTTP 客户端** | Axios | 1.7.9 | 拦截器、JWT 注入、401 处理 |
| **Markdown 渲染** | markdown-it + highlight.js | 14.1.0 / 11.9.0 | 代码高亮、表格、链接 |
| **图表** | Chart.js + vue-chartjs | 4.4.1 / 5.3.0 | 资产分布饼图、趋势图 |

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
│   │   └── src/main/java/com/finance/advisor/common/
│   │       ├── constant/AdvisorConstants.java    # 常量定义 (Agent 名称、事件类型等)
│   │       ├── dto/ApiResponse.java              # 统一响应包装 {code, message, data, timestamp}
│   │       └── session/SessionManager.java       # 会话管理
│   │
│   ├── advisor-user/                     # 用户与认证模块
│   │   └── src/main/java/com/finance/advisor/user/
│   │       ├── AuthController.java             # 登录/注册/资料/风险等级接口
│   │       ├── JwtAuthFilter.java              # JWT 鉴权过滤器 (从 Authorization 头提取 token)
│   │       ├── JwtService.java                 # JWT 签发/校验 (jjwt 0.12.6)
│   │       ├── SecurityConfig.java             # Spring Security 配置 (放行 /auth/**, /api/**)
│   │       ├── User.java                       # 用户实体 (username, password, riskLevel)
│   │       ├── UserRepository.java             # 用户数据访问
│   │       ├── UserService.java                # 用户注册/登录/风险等级管理
│   │       ── RiskLevelRequest.java           # R1-R5 风险等级更新请求 DTO
│   │
│   ├── advisor-tool/                     # 工具层 (16 个金融工具)
│   │   ── src/main/java/com/finance/advisor/tool/
│   │       ├── FinancialTools.java             # 基础工具集 (联网搜索/复利/贷款/研报)
│   │       └── finance/
│   │           ├── CreditCardInstallmentTool.java  # 信用卡分期计算
│   │           ├── ExchangeRateTool.java           # 汇率转换
│   │           ├── FinancialCalendarTool.java      # 金融日历查询
│   │           ├── FundNavTool.java                # 基金净值查询
│   │           ├── FundScreenerTool.java           # 基金筛选器
│   │           ├── InsuranceCompareTool.java       # 保险产品对比分析
│   │           ├── KlineChartTool.java             # K线图生成 (XChart)
│   │           ├── MarketSentimentTool.java        # 市场情绪指数分析
│   │           ├── PortfolioOptimizerTool.java     # 投资组合优化 (Markowitz 均值-方差)
│   │           ├── RiskQuestionnaireTool.java      # 风险评估问卷 (R1-R5)
│   │           ├── SavingsGoalTool.java            # 储蓄目标规划
│   │           ├── StockQuoteTool.java             # 股票实时行情
│   │           └── TaxCalculatorTool.java          # 个人所得税计算
│   │
│   ├── advisor-rag/                      # RAG 知识库
│   │   └── src/main/java/com/finance/advisor/rag/
│   │       ├── DocumentIngestionService.java     # 多格式文档导入 (PDF/Excel/CSV/MD/TXT/图片)
│   │       ├── DocumentMetadataService.java      # 文档元数据管理
│   │       ├── HybridSearchService.java          # 混合检索 (向量语义 + RRF 融合排序)
│   │       ├── config/VectorStoreConfig.java     # 向量库配置 (pgvector)
│   │       └── reader/
│   │           ├── CsvDocumentReader.java        # CSV 文档读取
│   │           ├── ExcelDocumentReader.java      # Excel 文档读取 (Apache POI)
│   │           ├── MarkdownDocumentReader.java   # Markdown 文档读取
│   │           └── OcrDocumentReader.java        # 图片 OCR 读取 (Tess4J)
│   │
│   ├── advisor-portfolio/                # 投资组合模块
│   │   └── src/main/java/com/finance/advisor/portfolio/
│   │       ├── PortfolioController.java          # 资产 CRUD 接口 (/api/portfolio)
│   │       ├── PortfolioService.java             # 组合汇总/分类统计/收益率计算
│   │       ├── PortfolioSummary.java             # 组合汇总 DTO (totalCost/totalMarketValue/profitLoss/breakdown)
│   │       ├── Asset.java                        # 资产实体 (type/symbol/name/amount/costPrice/buyDate/notes)
│   │       ├── AssetRepository.java              # 资产数据访问 (按 userId 隔离)
│   │       ├── SecurityUtil.java                 # 当前登录用户上下文提取
│   │       └── goal/
│   │           ├── GoalController.java           # 理财目标 CRUD 接口 (/api/goal)
│   │           ├── GoalService.java              # 目标进度计算 (剩余月数/每月需储蓄)
│   │           ├── Goal.java                     # 目标实体 (type/targetAmount/currentAmount/deadline/monthlyContribution)
│   │           ├── GoalRepository.java           # 目标数据访问
│   │           ├── GoalSummary.java              # 目标进度聚合 DTO
│   │           └── GoalProgress.java             # 单目标进度 (progressPercent/remainingAmount/monthsRemaining/monthlyNeeded)
│   │
│   ├── advisor-agent/                    # Agent 核心层
│   │   └── src/main/java/com/finance/advisor/agent/
│   │       ├── core/
│   │       │   └── FinancialAdvisorAgent.java    # 金融顾问 ReactAgent (含 R1-R5 风险约束)
│   │       ├── config/
│   │       │   ├── AgentConfig.java              # Agent 全局配置 (模型/工具/系统提示词)
│   │       │   ├── AgentEnhancementConfig.java   # Supervisor/Sequential/Parallel 多 Agent 编排
│   │       │   ├── MultiAgentConfig.java         # RoutingAgent 多路由配置
│   │       │   ├── A2AConfig.java                # Agent 间通信配置 (A2A 协议)
│   │       │   ├── ChatMemoryConfig.java         # 对话记忆配置 (JDBC 持久化)
│   │       │   └── CheckpointConfig.java         # 检查点持久化配置 (memory/postgres/redis)
│   │       └── hook/
│   │           ├── ConfirmationHook.java         # 人工确认 (Human-in-the-loop, 大额资金操作)
│   │           └── ContextCompressionHook.java   # 上下文压缩 (超过 20 条裁剪至 6 条)
│   │
│   ├── advisor-api/                      # API 接口层
│   │   └── src/main/java/com/finance/advisor/api/
│   │       ├── controller/
│   │       │   ├── ChatController.java           # SSE 流式对话 + 非流式对话 + 文件上传
│   │       │   ├── ChatFileController.java       # 聊天文件上传 (/api/chat/upload)
│   │       │   ├── DocumentController.java       # 知识库文档管理 (上传/删除/搜索/分类/统计/联网导入)
│   │       │   └── SessionController.java        # 会话管理 (创建/查询/列表)
│   │       └── dto/
│   │           ├── ChatRequest.java              # 聊天请求 DTO (含 FileInfo 附件信息)
│   │           └── ChatResponse.java             # 聊天响应 DTO
│   │
│   ├── advisor-bootstrap/                # 启动入口
│   │   ├── src/main/java/com/finance/advisor/bootstrap/
│   │   │   ├── AdvisorApplication.java           # Spring Boot 启动类
│   │   │   └── config/NacosConfig.java           # Nacos 动态配置 (可选)
│   │   └── src/main/resources/
│   │       ├── application.yml                   # 主配置 (端口/Jackson/模型/向量库/数据源/JWT)
│   │       ├── application-dev.yml               # 开发环境 (financial_rag_dev 库, DEBUG 日志)
│   │       ├── application-prod.yml              # 生产环境
│   │       ├── application-test.yml              # 测试环境
│   │       ── bootstrap.yml                     # Nacos 配置中心 (可选)
│   │
│   └── Dockerfile                                # 后端镜像 (eclipse-temurin:17-jre-alpine)
│
├── frontend/                             # Vue 3 + Naive UI 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── ChatView.vue                # 智能对话 (SSE 流式 + 推理链 + 文件上传)
│   │   │   ├── DashboardView.vue           # 金融仪表盘 (资产概览/图表)
│   │   │   ├── PortfolioView.vue           # 资产组合管理 (CRUD + 汇总统计)
│   │   │   ├── GoalView.vue                # 理财目标管理 (CRUD + 进度计算)
│   │   │   ├── KnowledgeView.vue           # 知识库管理 (上传/搜索/分类/联网导入)
│   │   │   ├── LoginView.vue               # 登录/注册
│   │   │   └── SettingsView.vue            # 系统设置 (主题/风险等级/推理显示)
│   │   ├── components/
│   │   │   ├── AppLayout.vue               # 整体布局 (n-layout 侧边栏 + 内容区)
│   │   │   ├── MessageList.vue             # 消息列表 (Markdown 渲染 + 工具调用展示)
│   │   │   ├── MessageInput.vue            # 输入框组件 (支持文件附件)
│   │   │   ├── MarkdownContent.vue         # Markdown 渲染封装 (markdown-it + highlight.js)
│   │   │   ├── SessionSidebar.vue          # 会话侧边栏 (新建/切换/删除)
│   │   │   ├── AssetForm.vue               # 资产表单 (Naive UI n-date-picker 时间戳处理)
│   │   │   └── GoalForm.vue                # 目标表单 (截止日期选择)
│   │   ├── composables/
│   │   │   ├── useMarkdown.js              # markdown-it 配置 (代码高亮/表格/链接)
│   │   │   └── useScroll.js                # 自动滚动到底部
│   │   ├── stores/
│   │   │   ├── auth.js                     # 认证状态 (JWT token + 用户信息, localStorage 持久化)
│   │   │   ├── sessions.js                 # 会话管理 (按 userId 隔离, localStorage 持久化)
│   │   │   └── settings.js                 # 用户设置 (主题/风险等级/推理显示, localStorage)
│   │   ├── api/
│   │   │   ├── http.js                     # axios 实例 (baseURL=/api, JWT 拦截器, 401 跳转)
│   │   │   ├── auth.js                     # 认证 API (login/register/profile/updateRiskLevel)
│   │   │   ├── chat.js                     # 对话 API (streamChat SSE 流式 + callChat 非流式)
│   │   │   ├── documents.js                # 知识库 API (upload/search/category/stats/ingest-web)
│   │   │   ├── portfolio.js                # 资产 API (list/create/update/delete/summary)
│   │   │   ├── goal.js                     # 目标 API (list/create/update/delete/summary)
│   │   │   ── session.js                  # 会话 API (create/get/list)
│   │   ├── App.vue                         # 根组件 (Naive UI 全局 Provider + 主题切换)
│   │   ├── main.js                         # 入口 (Vue Router + 路由守卫)
│   │   └── router/
│   │       └── index.js                    # 路由配置 (8 个页面 + requiresAuth 守卫)
│   ├── index.html                          # HTML 入口
│   ├── .env.development                    # 开发环境变量 (VITE_DEBUG_SSE=false)
│   ├── vite.config.js                      # Vite 配置 (代理/alias/dedupe: ['vue'])
│   ├── package.json                        # 前端依赖
│   ├── Dockerfile                          # 前端镜像 (node:20-alpine 构建 + nginx:alpine 运行)
│   └── nginx.conf                          # Nginx 配置 (SPA fallback + /api/ 反向代理)
│
├── deploy/                                 # 运维部署配置
│   ├── prometheus.yml                      # Prometheus 抓取配置
│   ├── grafana/dashboard.json              # Grafana 仪表盘 (6 面板)
│   └── jmeter/performance.jmx              # JMeter 性能测试脚本
│
── qa-browser-tests/                       # 浏览器端到端测试
│   ├── full-test.ps1                       # 完整测试脚本 (PowerShell)
│   ├── run-all-tests.bat                   # 批量运行脚本
│   └── FINAL-TEST-REPORT.md                # 测试报告
│
── docker-compose.yml                      # Docker Compose 编排 (backend/frontend/postgres/prometheus/grafana)
└── README.md                               # 本文档
```

---

## 模块依赖关系

```
advisor-bootstrap (启动入口)
    ├── advisor-api (API 接口层)
    │       ├── advisor-agent (Agent 核心)
    │       │       ├── advisor-tool (金融工具)
    │       │       ├── advisor-rag (RAG 知识库)
    │       │       └── advisor-common (公共模块)
    │       ├── advisor-portfolio (投资组合)
    │       │       └── advisor-common
    │       ├── advisor-user (用户认证)
    │       │       └── advisor-common
    │       └── advisor-common
    ├── advisor-user
    ├── advisor-portfolio
    └── advisor-common
```

**依赖方向**: 所有业务模块依赖 `advisor-common`；`advisor-api` 作为接口聚合层，依赖所有业务模块；`advisor-agent` 依赖 `advisor-tool` 和 `advisor-rag`。

---

## 核心功能

### 1. 多 Agent 协作模式

| 模式 | 说明 | 实现位置 | 适用场景 |
|---|---|---|---|
| **RoutingAgent** | 按问题类型路由到专业 Agent (理财规划/风险评估/产品推荐) | `MultiAgentConfig` | 用户问题类型明确，需分发到不同专业领域 |
| **Supervisor/Sub-Agent** | 主管 Agent 将 Sub-Agent 注册为工具，自动调度协同 | `AgentEnhancementConfig` | 复杂任务需要多步骤协作 |
| **SequentialAgent** | 串行流水线: 风险评估 -> 产品推荐 -> 理财规划 | `AgentEnhancementConfig` | 有明确先后顺序的任务链 |
| **ParallelAgent** | 并行查询: 同时分析 A股 + 基金 + 汇率市场 | `AgentEnhancementConfig` | 无依赖关系的并行查询 |

### 2. Agent 工作流程

```
用户输入 "帮我分析一下基金026789的最新净值"
         │
         ▼
┌─────────────────────┐
│   ChatController     │  接收请求，提取 sessionId / userId / riskLevel
│   (SSE 推流入口)      │  拼接附件内容到消息
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ FinancialAdvisorAgent│  ReAct 循环开始
│  (ReactAgent)        │  注入用户财务画像 + 风险约束
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    │  Reasoning │  大模型分析用户意图，决定调用哪个工具
    │  (思考)    │  输出: "需要查询基金净值和搜索最新新闻"
    └──────────┘
          │
          ▼
┌─────────────────────┐
│   Tool Calling       │  调用 FundNavTool 查询基金净值
│   (工具执行)          │  同时 TavilyWebSearch 搜索最新新闻
│                      │  自动入库相关搜索结果到知识库
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    │  Tool     │  执行工具，返回结果
    │  Result   │  基金净值: 1.2345 (2026-07-14)
    └─────┬─────│  新闻: 3 条相关结果已入库
          │
          ▼
┌─────────────────────┐
│   Hook 拦截          │  ContextCompressionHook: 检查消息数量
│   (BEFORE_MODEL)     │  如果 > 20 条，压缩至最近 6 条
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│   Reasoning          │  大模型综合工具结果，生成最终回复
│   (合成答案)          │  注入用户风险等级约束 (R1-R5)
│                      │  引用数据来源和日期
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│   Hook 拦截          │  ConfirmationHook: 检测大额操作
│   (AFTER_MODEL)      │  如果涉及 >= 100,000 元，插入确认提示
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│   SSE 推流           │  分事件推送: reasoning → tool_call →
│   (结构化 JSON)       │  tool_result → message → [DONE]
└─────────────────────┘
```

**Hook 机制详解**:

#### ContextCompressionHook (BEFORE_MODEL)

在模型调用前拦截，自动压缩过长的对话历史:

```java
@HookPositions({HookPosition.BEFORE_MODEL})
public AgentCommand beforeModel(List<Message> messages, RunnableConfig config) {
    if (messages.size() <= maxMessages) {  // 默认 20 条
        return new AgentCommand(messages);
    }
    
    // 保留第一条 (系统提示) + 最近 keepAfterTrim 条 (默认 6 条)
    List<Message> compressedMessages = new ArrayList<>();
    compressedMessages.add(messages.get(0));  // 系统提示
    int startIndex = Math.max(1, messages.size() - keepAfterTrim);
    for (int i = startIndex; i < messages.size(); i++) {
        compressedMessages.add(messages.get(i));
    }
    
    return new AgentCommand(compressedMessages, UpdatePolicy.REPLACE);
}
```

**配置项**:
```yaml
advisor:
  context-compression:
    max-messages: 20        # 触发压缩的阈值
    keep-after-trim: 6      # 压缩后保留的消息数
```

#### ConfirmationHook (AFTER_MODEL)

在模型响应后拦截，检测大额金融操作并插入人工确认:

```java
@HookPositions({HookPosition.AFTER_MODEL})
public AgentCommand afterModel(List<Message> messages, RunnableConfig config) {
    String content = assistantMessage.getText();
    
    if (containsConfirmationTrigger(content)) {
        // 插入确认提示
        String confirmationMsg = "\n\n⚠️ **需要您的确认**\n\n"
            + "以上操作涉及大额资金，请确认是否继续？\n"
            + "回复「确认」继续执行，回复「取消」放弃操作。";
        
        // 替换最后一条消息
        return new AgentCommand(updatedMessages, UpdatePolicy.REPLACE);
    }
    
    return new AgentCommand(messages);
}
```

**触发条件**:
- 包含 "投资" + 金额 (>= 100,000)
- 包含 "转账" + 金额 (>= 100,000)
- 包含 "购买" + 金额 (>= 100,000)
- 包含 "支付" + 金额 (>= 100,000)
- 同时包含 "确认" 和 "金额"
- 同时包含 "万" 和 ("投资" | "购买" | "转账")

**配置项**:
```yaml
advisor:
  confirmation:
    required-amount: 100000  # 大额确认阈值 (元)
```

### 3. 金融工具 (16 个)

| 工具 | 功能 | 底层实现 | 返回格式 |
|---|---|---|---|
| **TavilyWebSearch** | 联网搜索财经资讯 (限定近 7 天新闻) | Tavily API + `topic: "news"` + `days: 7` | 标题/URL/摘要/发布日期 |
| **FundNavTool** | 基金净值查询 | 第三方基金数据 API | 基金代码/名称/净值/日期 |
| **StockQuoteTool** | 股票实时行情 | 第三方股票数据 API | 股票代码/名称/现价/涨跌幅 |
| **ExchangeRateTool** | 汇率转换 | 实时汇率 API | 源币种/目标币种/汇率 |
| **RiskQuestionnaireTool** | 风险承受能力评估 (R1-R5) | 问卷评分算法 | 风险等级 + 评估说明 |
| **CreditCardInstallmentTool** | 信用卡分期计算 | 等额本息/等额本金公式 | 每期还款额/总利息 |
| **CompoundInterestTool** | 复利计算 | 复利公式 | 终值/利息收益 |
| **LoanCalculatorTool** | 贷款计算 | 等额本息/等额本金 | 月供/总利息/还款计划表 |
| **FundScreenerTool** | 基金筛选器 | 多维度筛选 | 符合条件的基金列表 |
| **TaxCalculatorTool** | 个人所得税计算 | 累进税率表 | 应纳税额/税后收入 |
| **InsuranceCompareTool** | 保险产品对比分析 | 多维度对比 | 对比表格/推荐建议 |
| **PortfolioOptimizerTool** | 投资组合优化 | Markowitz 均值-方差 (Commons Math3) | 最优权重/预期收益/风险 |
| **SavingsGoalTool** | 储蓄目标规划 | 目标倒推算法 | 每月需储蓄金额/达成时间 |
| **MarketSentimentTool** | 市场情绪指数分析 | 多指标综合 | 情绪指数/解读 |
| **FinancialCalendarTool** | 金融日历查询 | 金融事件日历 | 近期金融事件列表 |
| **KlineChartTool** | K线图生成 | XChart 图表库 | 图表图片 URL |

### 4. RAG 知识库

- **多格式文档导入**: PDF / Excel (.xlsx/.xls) / CSV / Markdown / 纯文本 / 图片 (JPEG/PNG, OCR)
- **混合检索**: 向量语义检索 (pgvector cosine similarity) + RRF (Reciprocal Rank Fusion) 融合排序
- **文档管理**: 上传、删除、分类、搜索、统计
- **自动建表**: 启动时自动初始化 `vector_store` 向量表和 `knowledge_documents` 元数据表
- **聊天内上传**: 对话页支持文件上传，上传后自动入知识库并参与当轮回答
- **联网导入**: 通过 Tavily 搜索关键词，将搜索结果自动存入知识库
- **文档读取器**:
  - `CsvDocumentReader` — CSV 文件解析
  - `ExcelDocumentReader` — Excel 文件解析 (Apache POI 5.4.0)
  - `MarkdownDocumentReader` — Markdown 文件解析
  - `OcrDocumentReader` — 图片 OCR 文字提取 (Tess4J 5.4.0)

### 5. 用户认证与风险偏好

- **JWT 认证**: 登录/注册返回 JWT token，前端 axios 请求拦截器自动注入 `Authorization: Bearer <token>`
- **风险等级 (R1-R5)**: 用户档案保存风险等级，Agent 推理时在系统提示词中注入风险约束，据此过滤产品推荐范围
  - **R1 保守型**: 货币基金、国债、银行定期存款
  - **R2 稳健型**: 债券基金、银行理财、同业存单基金
  - **R3 平衡型**: 混合基金、指数基金、可转债
  - **R4 成长型**: 股票基金、ETF、QDII 基金
  - **R5 进取型**: 个股、期货、期权、加密货币
- **路由守卫**: 未登录访问受保护页面自动跳转至 `/login`，已登录访问 `/login` 自动跳转至 `/chat`
- **会话隔离**: 不同用户的会话按 `userId` 隔离存储，切换用户时自动加载对应用户的会话列表
- **密码安全**: 使用 Spring Security `PasswordEncoder` (BCrypt) 哈希存储

### 6. 投资组合与理财目标

**资产管理**:
- CRUD 资产记录，支持 6 种类型: 股票 (stock) / 基金 (fund) / 存款 (deposit) / 债券 (bond) / 现金 (cash) / 其他 (other)
- 字段: 代码 (symbol)、名称 (name)、数量 (amount)、成本价 (costPrice)、买入日期 (buyDate)、备注 (notes)
- 按用户隔离 (`user_id` 字段)，每个用户只能看到自己的资产
- 组合汇总: 总成本、总估值、累计盈亏、按类型分布占比 (PortfolioSummary)
- 收益率计算: `(总估值 - 总成本) / 总成本 * 100%`

**理财目标**:
- 支持 5 种目标类型: 退休 (retirement) / 教育 (education) / 购房 (house) / 应急基金 (emergency_fund) / 自定义 (custom)
- 字段: 目标金额 (targetAmount)、当前金额 (currentAmount)、截止日期 (deadline)、每月储蓄 (monthlyContribution)、备注 (notes)
- 进度计算: GoalService 自动计算剩余月数、每月需储蓄金额、达成百分比
  - `remainingMonths = (deadline - today) / 30`
  - `remainingAmount = targetAmount - currentAmount`
  - `monthlyNeeded = remainingAmount / remainingMonths`
  - `progressPercent = currentAmount / targetAmount * 100`
- 按用户隔离，支持 CRUD 操作

### 7. 对话增强

- **SSE 流式输出**: 后端通过 Server-Sent Events 推送结构化 JSON 事件，前端 `fetch + ReadableStream` 实时渲染
  - 事件类型: `reasoning` (推理过程) / `message` (回复内容) / `tool_call` (工具调用开始) / `tool_result` (工具调用完成) / `error` (错误) / `[DONE]` (完成)
  - 调试开关: `VITE_DEBUG_SSE=true` 可在浏览器控制台打印原始 SSE 分片
  - 单次触发保护: `triggerDone()` 确保 `[DONE]` 事件只触发一次
- **上下文压缩**: 超过 20 条消息时自动裁剪，保留最近的 6 条 (可通过 `advisor.context-compression` 配置)
- **人工确认**: 涉及大额资金操作 (默认 >= 100,000 元) 时插入确认流程 (Human-in-the-loop)
- **会话管理**: 会话隔离，每个会话独立线程，支持新建/切换/删除/自动命名 (取首条消息前 20 字)
- **空消息拦截**: 后端对空内容返回 400 Bad Request，防止无效请求
- **文件上传**: 支持在对话中上传文件，文件内容作为上下文参与当轮回答
- **检查点持久化**: 支持 MemorySaver (默认，内存) / PostgresSaver (数据库) / Redis (可扩展)
- **系统提示词**: 内置当前时间 (如 "当前时间：2026年7月")，确保联网搜索返回时效性数据

### 8. 可观测性

| 组件 | 端点/端口 | 说明 |
|---|---|---|
| Actuator | `/actuator/health` | 健康检查 (UP/DOWN) |
| Actuator | `/actuator/prometheus` | Prometheus 指标暴露 |
| Actuator | `/actuator/metrics` | 指标查询 |
| Actuator | `/actuator/info` | 应用信息 |
| Prometheus UI | `localhost:9090` | 指标查询与告警规则 |
| Grafana | `localhost:3001` | 6 面板仪表盘 |

**Grafana 仪表盘面板**:
1. Agent 调用次数 (按工具类型分组)
2. 工具平均耗时 (ms)
3. Token 消耗 (输入/输出)
4. HTTP 请求 QPS
5. JVM 内存使用
6. API 响应时间 P50/P95/P99

---

## 数据库表结构

### users — 用户表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `username` | VARCHAR(64) UNIQUE | 用户名 |
| `password` | VARCHAR(255) | BCrypt 哈希密码 |
| `risk_level` | VARCHAR(4) | 风险等级 (R1-R5)，默认 R3 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 更新时间 |

### asset — 资产表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `user_id` | BIGINT (FK -> users) | 所属用户 |
| `type` | VARCHAR(20) | 资产类型 (stock/fund/deposit/bond/cash/other) |
| `symbol` | VARCHAR(64) | 代码 (如 012921, 300050) |
| `name` | VARCHAR(255) | 名称 |
| `amount` | DECIMAL(20,4) | 数量 |
| `cost_price` | DECIMAL(20,4) | 成本价 |
| `buy_date` | DATE | 买入日期 |
| `notes` | TEXT | 备注 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 更新时间 |

### goal — 理财目标表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `user_id` | BIGINT (FK -> users) | 所属用户 |
| `type` | VARCHAR(20) | 目标类型 (retirement/education/house/emergency_fund/custom) |
| `target_amount` | DECIMAL(20,2) | 目标金额 |
| `current_amount` | DECIMAL(20,2) | 当前金额 |
| `deadline` | DATE | 截止日期 |
| `monthly_contribution` | DECIMAL(20,2) | 每月储蓄 |
| `notes` | TEXT | 备注 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 更新时间 |

### vector_store — 向量表 (pgvector)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID (PK) | 主键 |
| `content` | TEXT | 文档内容 |
| `metadata` | JSONB | 元数据 (来源/分类/时间) |
| `embedding` | vector(1536) | 向量嵌入 (text-embedding-v3) |

### knowledge_documents — 文档元数据表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL (PK) | 主键 |
| `filename` | VARCHAR(255) | 文件名 |
| `category` | VARCHAR(64) | 分类 |
| `status` | VARCHAR(20) | 状态 (processed/failed/pending) |
| `source` | VARCHAR(20) | 来源 (upload/web) |
| `created_at` | TIMESTAMP | 创建时间 |

---

## 快速启动

### 前置条件

- **JDK 17+** (推荐 Oracle OpenJDK Temurin 21)
- **Node.js 20+**
- **Maven 3.9+**
- **Docker** (可选，用于 PostgreSQL pgvector / Prometheus / Grafana)
- **阿里云百炼 API Key** (获取: https://bailian.console.aliyun.com)
- **Tavily API Key** (获取: https://tavily.com)

### 1. 设置环境变量

**Windows PowerShell**:
```powershell
$env:AI_DASHSCOPE_API_KEY="your-dashscope-api-key"      # 阿里百炼
$env:TAVILY_API_KEY="your-tavily-api-key"               # Tavily
```

**Linux/macOS**:
```bash
export AI_DASHSCOPE_API_KEY="your-dashscope-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

### 2. 启动 PostgreSQL

**方式一: Docker (推荐)**
```powershell
docker compose up -d postgres
```
这将启动 `pgvector/pgvector:pg16` 镜像，创建 `financial_rag_dev` 数据库，用户 `postgres`，密码 `root`。

**方式二: 本地 PostgreSQL**
```sql
-- 创建数据库
CREATE DATABASE financial_rag_dev;

-- 安装 pgvector 扩展
\c financial_rag_dev
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 启动后端

**方式一: Maven 命令行**
```powershell
cd backend
mvn spring-boot:run -pl advisor-bootstrap -am -Dspring-boot.run.profiles=dev
```

**方式二: IDEA 运行**
1. 打开 `backend/advisor-bootstrap/src/main/java/com/finance/advisor/bootstrap/AdvisorApplication.java`
2. 设置 Active Profiles: `dev`
3. 点击 Run

后端启动后监听 `http://localhost:8080`，自动初始化数据库表 (`vector_store`, `knowledge_documents`, `users`, `asset`, `goal`)。

**启动成功标志**:
```
Started AdvisorApplication in X.XXX seconds
assets 表已就绪
goals 表已就绪
```

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端启动后监听 `http://localhost:3000`，通过 Vite 代理将 `/api` 请求转发至后端 `http://localhost:8080`。

**首次使用**:
1. 访问 `http://localhost:3000`，自动跳转至登录页
2. 点击"注册"创建账号
3. 登录后进入智能对话页面
4. 尝试提问: "1+1等于几" (测试流式对话)、"帮我查一下基金026789的最新净值" (测试工具调用)

### 5. Docker 一键部署

```powershell
# 设置环境变量
$env:AI_DASHSCOPE_API_KEY="your-dashscope-api-key"
$env:TAVILY_API_KEY="your-tavily-api-key"

# 构建并启动所有服务
docker compose up -d --build
```

服务端口:
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

所有接口返回统一的 `ApiResponse` 包装:

```json
{
    "code": 200,
    "message": "success",
    "data": { ... },
    "timestamp": "2026-07-14T17:00:00"
}
```

| code | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 请求参数错误 (空消息/必填字段缺失) |
| 401 | 未认证 (JWT 过期/无效) |
| 403 | 无权限 |
| 500 | 服务器内部错误 |

### 认证接口

| 方法 | 路径 | 说明 | 请求体 | 响应 data |
|---|---|---|---|---|
| POST | `/api/auth/register` | 注册 | `{username, password}` | `{token, userId, username, riskLevel}` |
| POST | `/api/auth/login` | 登录 | `{username, password}` | `{token, userId, username, riskLevel}` |
| GET | `/api/auth/profile` | 获取当前用户资料 | - | `{userId, username, riskLevel}` |
| PUT | `/api/auth/risk-level` | 更新风险等级 | `{riskLevel: "R1"~"R5"}` | 更新后的用户信息 |

**注册/登录响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "userId": 1,
        "username": "admin",
        "riskLevel": "R3"
    },
    "timestamp": "2026-07-14T17:00:00"
}
```

### 对话接口

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|---|---|---|---|---|
| POST | `/api/chat/stream` | SSE 流式对话 | `{message, files?, riskLevel?}` | SSE 事件流 |
| POST | `/api/chat/call` | 非流式对话 | `{message, riskLevel?}` | `{content}` |
| POST | `/api/chat/upload` | 聊天文件上传 | `multipart/form-data` | 文件信息 |

**SSE 事件格式**:
```
data: {"type": "reasoning", "content": "正在分析用户意图..."}

data: {"type": "tool_call", "tool": "tavily_web_search", "args": "基金026789最新净值"}

data: {"type": "tool_result", "tool": "tavily_web_search", "result": "搜索结果..."}

data: {"type": "message", "content": "根据搜索结果，基金026789..."}

data: [DONE]
```

**流式对话请求示例**:
```json
{
    "message": "帮我查一下基金026789的最新净值",
    "riskLevel": "R3"
}
```

### 知识库接口

| 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/documents/list` | 文档列表 | - | 文档列表 |
| POST | `/api/documents/upload` | 上传文档 | `multipart/form-data` (file) | 导入结果 |
| GET | `/api/documents/search` | 搜索文档 | `?keyword=xxx` | 匹配文档 |
| GET | `/api/documents/category/{category}` | 按分类查询 | 路径参数 | 分类文档 |
| GET | `/api/documents/stats` | 统计信息 | - | 文档数量/分类统计 |
| POST | `/api/documents/ingest-web` | 联网导入 | `{keyword}` | 导入结果 |

### 资产组合接口

所有接口需要 JWT 认证 (`Authorization: Bearer <token>`)，数据按用户隔离。

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|---|---|---|---|---|
| GET | `/api/portfolio/list` | 资产列表 | - | 资产列表 |
| POST | `/api/portfolio` | 创建资产 | `{type, symbol, name, amount, costPrice, buyDate, notes}` | 创建的资产 |
| PUT | `/api/portfolio/{id}` | 更新资产 | 同创建 | 更新后的资产 |
| DELETE | `/api/portfolio/{id}` | 删除资产 | - | 删除结果 |
| GET | `/api/portfolio/summary` | 组合汇总 | - | `{totalCost, totalMarketValue, profitLoss, breakdown}` |

**创建资产请求示例**:
```json
{
    "type": "fund",
    "symbol": "012921",
    "name": "易方达全球成长精选混合(QDII)美元现汇A",
    "amount": 1000,
    "costPrice": 0.7428,
    "buyDate": "2026-06-25",
    "notes": ""
}
```

**组合汇总响应示例**:
```json
{
    "code": 200,
    "data": {
        "totalCost": 742.80,
        "totalMarketValue": 742.80,
        "profitLoss": 0.00,
        "breakdown": {
            "fund": {"cost": 742.80, "marketValue": 742.80, "percent": 100.0}
        }
    }
}
```

**资产类型**: `stock` (股票) / `fund` (基金) / `deposit` (存款) / `bond` (债券) / `cash` (现金) / `other` (其他)

### 理财目标接口

所有接口需要 JWT 认证，数据按用户隔离。

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|---|---|---|---|---|
| GET | `/api/goal/list` | 目标列表 | - | 目标列表 |
| POST | `/api/goal` | 创建目标 | `{type, targetAmount, currentAmount, deadline, monthlyContribution, notes}` | 创建的目标 |
| PUT | `/api/goal/{id}` | 更新目标 | 同创建 | 更新后的目标 |
| DELETE | `/api/goal/{id}` | 删除目标 | - | 删除结果 |
| GET | `/api/goal/summary` | 目标汇总 | - | `{goals: [{goal, progressPercent, remainingAmount, monthsRemaining, monthlyNeeded}]}` |

**创建目标请求示例**:
```json
{
    "type": "retirement",
    "targetAmount": 10000000,
    "currentAmount": 10000,
    "deadline": "2030-07-06",
    "monthlyContribution": 10000,
    "notes": "退休储蓄计划"
}
```

**目标类型**: `retirement` (退休) / `education` (教育) / `house` (购房) / `emergency_fund` (应急基金) / `custom` (自定义)

### 会话管理接口

| 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/session/create` | 创建会话 | - | 会话信息 |
| GET | `/api/session/{sessionId}` | 获取会话 | 路径参数 | 会话详情 (含消息) |
| GET | `/api/session/list` | 会话列表 | - | 会话列表 |

---

## 前端架构

### 路由与页面

| 路径 | 组件 | 说明 | 认证要求 | 图标 |
|---|---|---|---|---|
| `/` | - | 重定向至 `/chat` | - | - |
| `/login` | `LoginView` | 登录/注册页 | 仅访客 (`guest: true`) | - |
| `/chat` | `ChatView` | 智能对话 (SSE 流式) | 需登录 | 智能对话 |
| `/knowledge` | `KnowledgeView` | 知识库管理 | 需登录 | 知识库 |
| `/dashboard` | `DashboardView` | 金融仪表盘 | 需登录 | 仪表盘 |
| `/settings` | `SettingsView` | 系统设置 | 需登录 | 设置 |
| `/portfolio` | `PortfolioView` | 资产组合管理 | 需登录 | 我的资产 |
| `/goal` | `GoalView` | 理财目标管理 | 需登录 | 理财目标 |

路由守卫逻辑:
- 未登录访问 `requiresAuth: true` 的页面 -> 跳转 `/login?redirect=<原路径>`
- 已登录访问 `guest: true` 的页面 (登录页) -> 跳转 `/chat`
- 页面标题自动设置: `<页面名> - 金融理财顾问`

### 状态管理

前端使用 Vue 3 `reactive` + `watch` 实现轻量级状态管理 (非 Pinia)，数据持久化至 `localStorage`。

| Store | 文件 | 职责 | 持久化 Key | 核心方法 |
|---|---|---|---|---|
| `auth` | `stores/auth.js` | JWT token、用户信息、登录状态 | `fa_auth` | `setAuth()`, `clear()`, `setRiskLevel()` |
| `sessions` | `stores/sessions.js` | 会话列表、当前会话、消息管理 | `fa_sessions_{userId}` | `createSession()`, `selectSession()`, `deleteSession()`, `addMessage()` |
| `settings` | `stores/settings.js` | 主题 (dark/light)、风险等级、推理显示 | `fa_settings` | `resetSettings()` |

**会话隔离**: 会话数据按 `userId` 隔离存储 (`fa_sessions_{userId}`)，切换用户时自动加载对应用户的会话列表。

**认证流程**:
1. 用户登录 -> `auth.setAuth(token, user)` -> 写入 `localStorage['fa_auth']`
2. 每次请求 -> `http.js` 拦截器读取 `auth.token` -> 注入 `Authorization: Bearer <token>`
3. 收到 401 -> `auth.clear()` -> 跳转 `/login`
4. 登出 -> `auth.clear()` -> 清除当前用户会话引用

### API 层

| 文件 | 职责 | 核心方法 |
|---|---|---|
| `api/http.js` | axios 实例 (baseURL=/api, 30s 超时, JWT 请求拦截器, 401 响应拦截器) | - |
| `api/auth.js` | 认证 API | `login()`, `register()`, `getProfile()`, `updateRiskLevel()` |
| `api/chat.js` | 对话 API | `streamChat()` (SSE), `callChat()` (非流式) |
| `api/documents.js` | 知识库 API | `listDocuments()`, `uploadDocument()`, `searchDocuments()`, `ingestFromWeb()` |
| `api/portfolio.js` | 资产 API | `listAssets()`, `createAsset()`, `updateAsset()`, `deleteAsset()`, `getSummary()` |
| `api/goal.js` | 目标 API | `listGoals()`, `createGoal()`, `updateGoal()`, `deleteGoal()`, `getSummary()` |
| `api/session.js` | 会话 API | `createSession()`, `getSession()`, `listSessions()` |

### 组件库

| 组件 | 文件 | 说明 |
|---|---|---|
| `AppLayout` | `components/AppLayout.vue` | 整体布局 (Naive UI n-layout 侧边栏 + 内容区) |
| `MessageList` | `components/MessageList.vue` | 消息列表 (Markdown 渲染 + 工具调用展示 + 推理过程) |
| `MessageInput` | `components/MessageInput.vue` | 输入框组件 (支持文件附件上传) |
| `MarkdownContent` | `components/MarkdownContent.vue` | Markdown 渲染封装 (markdown-it + highlight.js 代码高亮) |
| `SessionSidebar` | `components/SessionSidebar.vue` | 会话侧边栏 (新建/切换/删除/自动命名) |
| `AssetForm` | `components/AssetForm.vue` | 资产表单 (Naive UI 日期选择器，时间戳/字符串双向转换) |
| `GoalForm` | `components/GoalForm.vue` | 目标表单 (截止日期选择，进度预览) |

**Naive UI 注意事项**:
- 所有 `n-*` 组件必须在 `<script setup>` 中显式 import
- `n-date-picker` 的 `value` 类型为 `Number | Array` (时间戳)，不能直接传字符串
- 后端返回的 `LocalDate` 序列化为 `"yyyy-MM-dd"` 字符串，需在组件内转换为时间戳
- `vite.config.js` 配置 `resolve.dedupe: ['vue']` 防止 Vue 模块实例重复

### Vite 配置

```js
// vite.config.js
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': resolve(__dirname, 'src') },
    dedupe: ['vue']              // 防止 Vue 模块实例重复
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true                  // WebSocket 代理 (SSE 需要)
      }
    }
  }
})
```

---

## 数据存储架构

| 数据类型 | 存储方式 | 表名/Key | 说明 |
|---|---|---|---|
| 对话历史 | JDBC ChatMemory -> PostgreSQL | `chat_memory` (Spring AI 自动管理) | 重启不丢失 |
| Agent 运行状态 | MemorySaver (默认) / PostgresSaver | 可配置切换 | 检查点持久化 |
| 向量数据 | pgvector | `vector_store` | 语义相似度检索 (cosine distance) |
| 文档元数据 | PostgreSQL | `knowledge_documents` | 文件管理/分类/状态/导入来源 |
| 用户/认证 | PostgreSQL | `users` | 账号、BCrypt 密码哈希、风险等级 R1-R5 |
| 资产记录 | PostgreSQL | `asset` | 投资组合，按 user_id 隔离 |
| 理财目标 | PostgreSQL | `goal` | 理财目标，按 user_id 隔离 |
| 前端认证 | localStorage | `fa_auth` | JWT token + 用户信息 |
| 前端会话 | localStorage | `fa_sessions_{userId}` | 会话列表 + 消息 (按用户隔离) |
| 前端设置 | localStorage | `fa_settings` | 主题/风险等级/推理显示 |

**数据库自动初始化**: 后端启动时自动创建 `vector_store`、`knowledge_documents`、`users`、`asset`、`goal` 表 (通过 `@PostConstruct` 执行 DDL)。

---

## 架构说明

- **多模块架构**: 拆分为 8 个 Maven 子模块 (common/user/tool/rag/portfolio/agent/api/bootstrap)，职责清晰，可按需独立演进
- **纯云端模型**: 默认使用阿里云百炼 qwen3.6-plus 云端大模型，无需本地模型，配置环境变量即可使用
- **ReAct 模式**: Agent 采用 Reasoning + Acting 循环，自主决策工具调用时机和顺序，并结合用户 R1-R5 风险等级约束推荐
- **SSE 流式**: 后端通过 Server-Sent Events 推流，前端 fetch + ReadableStream 实时渲染，支持推理过程/工具调用/回复内容分事件推送
- **Hook 机制**: 复用 Spring AI Alibaba Agent Framework 的 MessagesModelHook，支持上下文压缩和人工确认
- **前端组件库**: 基于 Naive UI 2.44 + @vicons/ionicons5，vite.config.js 配置 `dedupe: ['vue']` 防止 Vue 实例重复
- **Nacos 可选**: 支持通过 Nacos 配置中心动态管理配置 (bootstrap.yml)，也可纯本地配置运行
- **日期序列化**: Jackson 配置 `write-dates-as-timestamps: false` + `@JsonFormat(pattern = "yyyy-MM-dd")`，`LocalDate` 序列化为 ISO 字符串
- **文件上传限制**: 单文件最大 50MB，请求体最大 100MB
- **安全设计**: JWT 24h 过期、BCrypt 密码哈希、路由守卫、API 按用户数据隔离

---

## 环境变量

### 后端

| 变量名 | 说明 | 默认值 | 必填 |
|---|---|---|---|
| `AI_DASHSCOPE_API_KEY` | DashScope API Key | - | 是 |
| `TAVILY_API_KEY` | Tavily 搜索 API Key | 空 (联网搜索不可用) | 否 |
| `DB_PASSWORD` | PostgreSQL 密码 | `root` | 否 |
| `DB_HOST` | PostgreSQL 主机 | `localhost` | 否 |
| `PGVECTOR_HOST` | pgvector 主机 | `localhost` | 否 |
| `NACOS_SERVER` | Nacos 服务器地址 | `localhost:8848` | 否 |
| `NACOS_NAMESPACE` | Nacos 命名空间 | `financial-advisor` | 否 |
| `JWT_SECRET` | JWT 签名密钥 | 配置文件内置 | 否 |

### 前端 (`.env.development`)

| 变量名 | 说明 | 默认值 |
|---|---|---|
| `VITE_DEBUG_SSE` | 打印原始 SSE 分片 (调试流式对话) | `false` |
| `VITE_API_BASE` | 后端 API 基址 | `/api` (走 vite 代理) |

### 后端配置项 (`application.yml`)

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `server.port` | 后端服务端口 | `8080` |
| `spring.jackson.serialization.write-dates-as-timestamps` | LocalDate 序列化为字符串 | `false` |
| `spring.ai.dashscope.chat.model` | 大模型名称 | `qwen3.6-plus` |
| `spring.ai.dashscope.chat.options.temperature` | 模型温度 | `0.7` |
| `spring.ai.dashscope.embedding.options.model` | 嵌入模型 | `text-embedding-v3` |
| `spring.ai.vectorstore.pgvector.database` | 向量库数据库名 | `financial_rag` |
| `spring.ai.vectorstore.pgvector.initialize-schema` | 自动初始化向量表 | `false` (dev: `true`) |
| `spring.datasource.url` | 数据库连接 URL | `jdbc:postgresql://localhost:5432/financial_rag` |
| `spring.servlet.multipart.max-file-size` | 单文件上传限制 | `50MB` |
| `spring.servlet.multipart.max-request-size` | 请求体大小限制 | `100MB` |
| `advisor.context-compression.max-messages` | 上下文压缩触发阈值 | `20` |
| `advisor.context-compression.keep-after-trim` | 压缩后保留消息数 | `6` |
| `advisor.confirmation.required-amount` | 人工确认金额阈值 (元) | `100000` |
| `advisor.checkpoint.saver` | 检查点存储方式 | `memory` (memory/postgres/redis) |
| `advisor.jwt.secret` | JWT 签名密钥 | 内置默认值 |
| `advisor.jwt.expiration` | JWT 过期时间 (毫秒) | `86400000` (24h) |

---

## 测试

```powershell
# 运行全部测试
cd backend
mvn test

# 运行单个模块测试
mvn test -pl advisor-tool -am

# 运行集成测试
mvn test -pl advisor-api -am

# 跳过测试打包
mvn clean package -DskipTests=true
```

### 测试覆盖

- **工具层**: 复利/贷款/税务/风险评估/信用卡/基金筛选/市场情绪/储蓄规划/金融日历/保险对比/投资组合优化
- **Hook 层**: ConfirmationHook / ContextCompressionHook
- **集成测试**: SSE 流式对话 / 向量库检索 / Agent 端到端 / 会话管理
- **浏览器测试**: `qa-browser-tests/` 目录包含 PowerShell 端到端测试脚本

---

## 部署指南

### 开发环境

```powershell
# 1. 启动 PostgreSQL
docker compose up -d postgres

# 2. 启动后端 (dev profile, 自动建表)
cd backend
mvn spring-boot:run -pl advisor-bootstrap -am -Dspring-boot.run.profiles=dev

# 3. 启动前端
cd frontend
npm install
npm run dev
```

### 生产环境 (Docker)

```powershell
# 1. 设置环境变量
$env:AI_DASHSCOPE_API_KEY="your-key"
$env:TAVILY_API_KEY="your-key"

# 2. 构建并启动
docker compose up -d --build

# 3. 查看日志
docker compose logs -f backend
docker compose logs -f frontend
```

### 生产环境 (手动部署)

```powershell
# 1. 后端打包
cd backend
mvn clean package -DskipTests=true

# 2. 运行后端 jar
java -jar advisor-bootstrap/target/advisor-bootstrap-1.0.0.jar --spring.profiles.active=prod

# 3. 前端打包
cd frontend
npm install
npm run build

# 4. 将 dist/ 目录部署到 Nginx
```

### Nginx 配置 (生产环境)

```nginx
server {
    listen 80;
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;   # SPA fallback
    }
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_buffering off;                 # SSE 需要关闭缓冲
        proxy_cache off;
    }
}
```

---

## 常见问题

### Q: 前端组件报错 "Failed to resolve component: n-xxx"

Naive UI 组件必须在 `<script setup>` 中显式 import。检查是否遗漏了组件导入。

### Q: 前端页面空白或组件不渲染

可能是 Vite 预构建缓存过期。执行以下操作:
```powershell
# 删除缓存
Remove-Item -Recurse -Force node_modules/.vite
# 强制重启
npx vite --force
# 浏览器硬刷新 (Ctrl+Shift+R)
```

### Q: 选择日期后表单验证失败 "请选择买入日期"

Naive UI 的 `n-date-picker` 返回时间戳 (number)，而 `@JsonFormat` 期望字符串。已在 `AssetForm.vue` 和 `GoalForm.vue` 中处理转换: 内部保持时间戳，提交时转为 `"yyyy-MM-dd"` 字符串。

### Q: 后端返回 500 但数据已入库

Jackson 序列化 `LocalDate` 失败。确保:
1. `application.yml` 中有 `spring.jackson.serialization.write-dates-as-timestamps: false`
2. 实体类日期字段有 `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")`
3. **重启后端** (配置变更需要重启)

### Q: Tavily 搜索返回过期数据

系统提示词中已注入当前时间，Tavily 调用使用 `topic: "news"` + `days: 7` 限定近 7 天新闻。如仍返回旧数据，检查 `TAVILY_API_KEY` 是否正确配置。

### Q: 数据库连接失败

检查 `application-dev.yml` 中的数据库配置:
- 数据库名: `financial_rag_dev`
- 用户: `postgres`
- 密码: `root`
- 确保 PostgreSQL 已启动且 pgvector 扩展已安装

### Q: Vite 代理报错 ECONNREFUSED

前端 Vite 代理目标为 `http://localhost:8080`，确保后端已启动。

### Q: 如何切换检查点存储方式

修改 `application.yml`:
```yaml
advisor:
  checkpoint:
    saver: memory    # memory | postgres | redis
```
切换为 `postgres` 需要确保 PostgreSQL 已启动。

### Q: 如何修改大模型

修改 `application.yml`:
```yaml
spring:
  ai:
    dashscope:
      chat:
        model: qwen-max    # 可选: qwen3.6-plus / qwen-max / qwen-turbo
```

---

## Agent 系统提示词 (System Prompt)

Agent 的核心行为由以下系统提示词控制 (定义在 `FinancialAdvisorAgent.java`):

```
你是一位专业的金融理财顾问，精通个人理财、投资规划、风险评估和金融产品分析。

当前时间：2026年7月

核心原则：
- 始终以客户利益为先，提供客观、中立的建议
- 明确说明投资有风险，不承诺收益
- 给出的建议应基于数据和事实
- 对于时效性问题，必须使用联网搜索获取最新信息

风险约束：
- 必须根据用户的风险等级（R1~R5）调整投资建议
- R1保守型：仅推荐存款、国债、货币基金等低风险产品
- R2稳健型：可推荐债券基金、稳健型理财产品
- R3平衡型：可推荐混合型基金、指数定投
- R4进取型：可推荐股票型基金、成长股
- R5激进型：可推荐股票、期货等高风险产品
- 不得推荐超出用户风险等级的产品

回答要求：
- 先分析用户需求，再决定使用哪些工具
- 对于涉及当前市场、价格、政策等时效性问题，务必使用 tavily_web_search 获取2026年最新信息
- 严禁将2024年或更早的数据当作2026年数据来使用
- 回答中引用数据时，必须标注数据来源和日期
```

**个性化注入**: 当用户已登录且有资产/目标数据时，Agent 会自动在消息前拼接 `[用户财务画像]` 前缀，包含:
- 当前持仓总成本、估算市值、累计盈亏
- 资产配置比例 (股票/基金/债券/存款 各占百分比)
- 理财目标进度 (目标金额、已达成百分比、每月需储蓄金额)

---

## SSE 流式协议详解

### 事件类型

| 事件类型 | 字段 | 说明 | 前端处理 |
|---|---|---|---|
| `reasoning` | `content` | Agent 推理过程 (思考链) | 灰色折叠区域，可通过设置隐藏 |
| `message` | `content` | 最终回复内容 (Markdown) | 实时追加渲染，支持代码高亮 |
| `tool_call` | `tool`, `args` | 工具调用开始 | 显示工具名称芯片 (如 "正在使用 tavily_web_search") |
| `tool_result` | `tool`, `result` | 工具调用完成 | 显示工具结果摘要 |
| `error` | `content` | 错误信息 | 红色错误提示 |
| `[DONE]` | - | 流结束标记 | 触发 `onDone` 回调，启用发送按钮 |

### 前端 SSE 实现要点

```javascript
// chat.js - SSE 流式解析核心逻辑
fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify({ message, riskLevel, files }),
    signal: controller.signal   // AbortController 支持组件卸载时取消
}).then(async (response) => {
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
        const { done, value } = await reader.read()
        buffer += decoder.decode(value, { stream: true })
        // 按 'data:' 分割事件（而非 \n），避免内容中的换行符导致 JSON 解析失败
        const parts = buffer.split('data:')
        buffer = parts.pop() || ''
        for (const part of parts) {
            const parsed = JSON.parse(part.trim())
            // 根据 parsed.type 分发到不同回调
        }
    }
})
```

**关键设计**:
- **单次触发保护**: `triggerDone()` 使用 `doneCalled` 标志确保 `[DONE]` 事件只触发一次 `onDone`
- **AbortController**: 组件卸载时调用 `controller.abort()` 终止 SSE 连接，防止网络错误
- **DEBUG 开关**: 设置 `VITE_DEBUG_SSE=true` 可在控制台打印原始 SSE 分片
- **HTTP 状态检查**: `response.ok` 检查确保非 200 响应立即触发 `onError`

---

## Docker 部署详解

### Docker Compose 服务编排

```yaml
# docker-compose.yml
services:
  backend:                              # Spring Boot 后端
    build: ./backend
    ports: ["8080:8080"]
    environment:
      - AI_DASHSCOPE_API_KEY=${AI_DASHSCOPE_API_KEY}
      - TAVILY_API_KEY=${TAVILY_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      postgres:
        condition: service_healthy      # 等待 PostgreSQL 健康检查通过

  frontend:                             # Vue 3 前端 (Nginx)
    build: ./frontend
    ports: ["3000:80"]
    depends_on: [backend]

  postgres:                             # PostgreSQL + pgvector
    image: pgvector/pgvector:pg16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: financial_rag_dev
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: root
    volumes:
      - pgdata:/var/lib/postgresql/data # 数据持久化
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      retries: 5

  prometheus:                           # 指标监控
    image: prom/prometheus
    ports: ["9090:9090"]
    volumes:
      - ./deploy/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:                              # 可视化仪表盘
    image: grafana/grafana
    ports: ["3001:3000"]
    depends_on: [prometheus]
```

### 后端 Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY advisor-bootstrap/target/advisor-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建前需先在 `backend/` 目录执行 `mvn clean package -DskipTests=true` 生成 fat jar。

### 前端 Dockerfile (多阶段构建)

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

### Nginx 配置 (生产环境)

```nginx
server {
    listen 80;
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;   # SPA fallback
    }
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_buffering off;                 # SSE 需要关闭缓冲
        proxy_cache off;
    }
}
```

### Prometheus 配置

```yaml
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: 'financial-advisor'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']
```

---

## 安全设计

### 认证流程

```
┌──────────┐  POST /api/auth/login   ┌──────────┐
│  浏览器   │  {username, password}   │  后端     │
│          │ ─────────────────────── > │          │
│          │                          │ BCrypt    │
│          │  {token, userId, ...}    │ 验证密码  │
│          │ < ────────────────────── │          │
│          │                          │ 签发 JWT  │
│  存储     │                          └──────────┘
│ fa_auth  │  每次请求自动附带:
│ (local   │  Authorization: Bearer <token>
│  Storage)│
└──────────┘
```

### 安全机制清单

| 机制 | 实现 | 说明 |
|---|---|---|
| **密码哈希** | BCrypt (Spring Security `PasswordEncoder`) | 数据库不存储明文密码 |
| **JWT 签名** | jjwt 0.12.6, HS256 | 24h 过期，可通过 `advisor.jwt.expiration` 配置 |
| **请求鉴权** | `JwtAuthFilter` (OncePerRequestFilter) | 从 `Authorization` 头提取 token，校验后设置 SecurityContext |
| **路由守卫** | Vue Router `beforeEach` | 未登录跳转 `/login?redirect=<原路径>` |
| **401 处理** | axios 响应拦截器 | 收到 401 自动清除登录态并跳转登录页 |
| **数据隔离** | `user_id` 字段 + `SecurityUtil` | 资产/目标/会话按用户隔离，不可跨用户访问 |
| **会话隔离** | `fa_sessions_{userId}` | 前端会话按 userId 分 key 存储 |
| **CORS** | Spring Security CORS 配置 | 仅允许同源和配置的域名 |
| **文件上传** | 50MB 单文件 / 100MB 请求体 | 防止大文件攻击 |

### Spring Security 配置

```java
// SecurityConfig.java
http.csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**", "/api/auth/**").permitAll()
        .requestMatchers("/actuator/**").permitAll()
        .anyRequest().authenticated()
    )
    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

---

## 个性化推荐机制

Agent 在每次对话时会自动构建用户财务画像，注入到消息前缀中:

### 画像构建流程

```
用户发送消息
     │
     ▼
┌─────────────────────┐
│ buildPersonalized   │
│ Message()           │
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    │ 1. 获取资产 │  PortfolioService.summary(userId)
    │    组合画像  │  → 总成本/总市值/盈亏/分类占比
    └──────────┘
          │
    ┌─────┴─────┐
    │ 2. 获取理财 │  GoalService.summary(userId)
    │    目标画像  │  → 各目标进度/每月需储蓄
    └──────────┘
          │
    ┌─────┴─────┐
    │ 3. 拼接风险 │  buildRiskAwareMessage(message, riskLevel)
    │    约束前缀  │  → "[系统提示：当前用户风险等级为 R3 - 平衡型...]"
    └──────────┘
          │
          ▼
    最终消息 = 画像前缀 + 风险约束 + 原始消息
```

**画像示例** (注入到 Agent 输入):
```
[用户财务画像]
当前持仓总成本：50000.00 元，估算市值：52300.00 元，累计盈亏：2300.00 元。
资产配置：股票 40%（20000元）、基金 35%（17500元）、债券 15%（7500元）、存款 10%（5000元）。
理财目标：
- 退休目标：目标 10000000.00 元，已达成 5.23%（523000元），还需每月储蓄 8000.00 元。

[系统提示：当前用户风险等级为 R3 - 平衡型（可接受中等风险，偏好混合型基金、指数定投）。请在给出投资建议时严格匹配此风险等级，不得推荐超出其风险承受能力的产品。]

帮我分析一下当前的资产配置是否合理
```

**降级策略**: 如果 `PortfolioService` 或 `GoalService` 调用失败，画像对应部分会被跳过 (仅打印 WARN 日志)，不影响主流程。

---

## 资产自动刷新机制

后端启动时，`PortfolioPriceRefreshRunner` 会在 `ApplicationReadyEvent` 触发后自动刷新所有资产的当前价格:

```java
@Component
public class PortfolioPriceRefreshRunner implements ApplicationListener<ApplicationReadyEvent> {
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 1. 查询所有资产
        // 2. 根据资产类型调用行情接口 (StockQuoteTool / FundNavTool)
        // 3. 更新 current_price / market_value / price_updated_at
        // 4. 计算累计盈亏
    }
}
```

**行情接口**:
- **股票**: 调用第三方股票数据 API (需 Referer 头防止拒绝)
- **基金**: 调用第三方基金净值 API (需 Referer 头防止拒绝)

---

## 资产批量导入

### 支持的格式

| 格式 | 扩展名 | 解析器 | 说明 |
|---|---|---|---|
| Excel | `.xlsx` | Apache POI (XSSF) | 支持多 Sheet，读取第一个 |
| Excel | `.xls` | Apache POI (HSSF) | 旧版 Excel 格式 |
| CSV | `.csv` | 自定义 `AssetImportParser` | 支持 UTF-8 BOM 头 |

### CSV 格式要求

```csv
type,symbol,name,amount,costPrice,buyDate,notes
stock,300050,世纪鼎利,1000,5.50,2026-01-15,看好5G
fund,012921,易方达全球成长,500,1.20,2026-03-01,QDII
```

**注意**: CSV 文件必须使用 UTF-8 编码 (可带 BOM)，列名必须与上表完全匹配。`buyDate` 格式为 `yyyy-MM-dd`。

### 导入接口

```
POST /api/portfolio/import
Content-Type: multipart/form-data

file: <xlsx/xls/csv 文件>
```

响应:
```json
{
    "code": 200,
    "data": {
        "imported": 5,
        "failed": 0,
        "errors": []
    }
}
```

---

## 前端字体规范

| 元素 | 字号 | 说明 |
|---|---|---|
| 用户消息 / AI 回复 | 18px | 主要阅读区域 |
| 推理过程文本 | 16px | 折叠区域内的思考链 |
| 工具调用芯片 | 15px | "正在使用 tavily_web_search" |
| 快捷建议 | 16px | 预设问题按钮 |
| 输入框 | 20px | 用户输入区域 |
| 发送按钮 | 20px | 与输入框匹配 |

---

## 完整配置参考

### application.yml 核心配置项

```yaml
server:
  port: 8080

spring:
  application:
    name: financial-advisor
  jackson:
    serialization:
      write-dates-as-timestamps: false    # LocalDate -> "yyyy-MM-dd"
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        model: qwen3.6-plus
        options:
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-v3        # 1536 维向量
    vectorstore:
      pgvector:
        host: ${PGVECTOR_HOST:localhost}
        database: financial_rag
        initialize-schema: false          # dev 环境为 true

advisor:
  context-compression:
    max-messages: 20                      # 超过此数触发压缩
    keep-after-trim: 6                    # 压缩后保留最近 6 条
  confirmation:
    required-amount: 100000               # 大额确认阈值 (元)
  checkpoint:
    saver: memory                         # memory | postgres | redis
  jwt:
    secret: ${JWT_SECRET:default-secret}
    expiration: 86400000                  # 24h (毫秒)
```

### application-dev.yml 开发环境差异

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true           # 自动建向量表
  datasource:
    url: jdbc:postgresql://localhost:5432/financial_rag
  jpa:
    hibernate:
      ddl-auto: update                    # 自动更新表结构
    show-sql: true                        # 打印 SQL

logging:
  level:
    com.finance.advisor: DEBUG
    com.alibaba.cloud.ai.graph: DEBUG
```

---

## 扩展与自定义

### 添加新的金融工具

1. 在 `advisor-tool/src/main/java/com/finance/advisor/tool/finance/` 创建工具类:

```java
@Component
public class MyCustomTool {
    @Tool(description = "工具描述，Agent 会根据此决定是否调用")
    public String myTool(@ToolParam(description = "参数说明") String param) {
        // 实现逻辑
        return "结果";
    }
}
```

2. 在 `FinancialTools.java` 中注册 (或在 `AgentConfig` 中添加到工具列表)
3. 重启后端，Agent 即可自动使用新工具

### 添加新的 Agent 编排模式

在 `AgentEnhancementConfig.java` 中配置:

```java
// Supervisor 模式
@Bean
public SupervisorAgent supervisorAgent(ChatModel model, List<SubAgent> subAgents) {
    return SupervisorAgent.builder()
        .model(model)
        .subAgents(subAgents)
        .build();
}

// Sequential 模式
@Bean
public SequentialAgent sequentialAgent(List<Agent> agents) {
    return SequentialAgent.builder()
        .agents(agents)  // 按顺序执行
        .build();
}
```

### 切换检查点存储

```yaml
advisor:
  checkpoint:
    saver: postgres    # 从 memory 切换为 postgres
```

切换为 `postgres` 后，Agent 运行状态会持久化到数据库，重启不丢失。

---

## 常见问题

### Q: 前端组件报错 "Failed to resolve component: n-xxx"

Naive UI 组件必须在 `<script setup>` 中显式 import。检查是否遗漏了组件导入。

### Q: 前端页面空白或组件不渲染

可能是 Vite 预构建缓存过期。执行以下操作:
```powershell
# 删除缓存
Remove-Item -Recurse -Force node_modules/.vite
# 强制重启
npx vite --force
# 浏览器硬刷新 (Ctrl+Shift+R)
```

### Q: 选择日期后表单验证失败 "请选择买入日期"

Naive UI 的 `n-date-picker` 返回时间戳 (number)，而 `@JsonFormat` 期望字符串。已在 `AssetForm.vue` 和 `GoalForm.vue` 中处理转换: 内部保持时间戳，提交时转为 `"yyyy-MM-dd"` 字符串。

### Q: 后端返回 500 但数据已入库

Jackson 序列化 `LocalDate` 失败。确保:
1. `application.yml` 中有 `spring.jackson.serialization.write-dates-as-timestamps: false`
2. 实体类日期字段有 `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")`
3. **重启后端** (配置变更需要重启)

### Q: Tavily 搜索返回过期数据

系统提示词中已注入当前时间，Tavily 调用使用 `topic: "news"` + `days: 7` 限定近 7 天新闻。如仍返回旧数据，检查 `TAVILY_API_KEY` 是否正确配置。

### Q: 数据库连接失败

检查 `application-dev.yml` 中的数据库配置:
- 数据库名: `financial_rag_dev`
- 用户: `postgres`
- 密码: `root`
- 确保 PostgreSQL 已启动且 pgvector 扩展已安装

### Q: Vite 代理报错 ECONNREFUSED

前端 Vite 代理目标为 `http://localhost:8080`，确保后端已启动。

### Q: 如何切换检查点存储方式

修改 `application.yml`:
```yaml
advisor:
  checkpoint:
    saver: memory    # memory | postgres | redis
```
切换为 `postgres` 需要确保 PostgreSQL 已启动。

### Q: 如何修改大模型

修改 `application.yml`:
```yaml
spring:
  ai:
    dashscope:
      chat:
        model: qwen-max    # 可选: qwen3.6-plus / qwen-max / qwen-turbo
```

### Q: 手动设置 Content-Type: multipart/form-data 导致上传失败

**不要**在 axios 请求中手动设置 `Content-Type: multipart/form-data`，这会破坏 boundary 生成。使用共享的 `http` 实例直接传 `FormData` 对象，浏览器会自动设置正确的 Content-Type 和 boundary。

### Q: SSE 流式对话在路由切换后报网络错误

`chat.js` 中使用 `AbortController` 在组件卸载时终止 SSE 连接。确保 `ChatView.vue` 的 `onUnmounted` 中调用了 `controller.abort()`。

### Q: 跨用户看到其他人的聊天记录

会话存储使用 `fa_sessions_${userId}` 作为 key，切换用户时会自动加载对应用户的会话。如果看到异常数据，清除浏览器 localStorage 后重新登录。

---

## License

Apache License 2.0
