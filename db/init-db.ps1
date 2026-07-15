<#
.SYNOPSIS
    金融顾问平台 - 新设备一键建库与全量数据导入脚本 (Windows PowerShell)

.DESCRIPTION
    在新 Windows 设备上一键完成 PostgreSQL 数据库初始化：
      1. 检查 psql 客户端是否可用
      2. 测试数据库连接
      3. 创建 financial_rag 数据库（已存在则询问是否删除重建）
      4. 安装所需扩展 (hstore / uuid-ossp / vector)
      5. 导入 db/public.sql 全量表结构与数据
      6. 验证建库结果（表数量与关键表行数）

    默认参数对齐 application-dev.yml：localhost:5432 / postgres / root / financial_rag。

.PARAMETER DbHost
    数据库主机地址，默认 localhost。

.PARAMETER Port
    数据库端口，默认 5432。

.PARAMETER Username
    数据库超级用户名，默认 postgres（需具备 CREATEDB 权限）。

.PARAMETER Password
    数据库密码，默认 root。

.PARAMETER DbName
    数据库名，默认 financial_rag。

.PARAMETER Force
    跳过所有交互确认（数据库已存在时直接删除重建），适用于自动化场景。

.EXAMPLE
    # 使用默认参数本地一键建库
    .\db\init-db.ps1

.EXAMPLE
    # 指定远程数据库并跳过确认
    .\db\init-db.ps1 -DbHost 192.168.1.100 -Password mypass -Force
#>
param(
    [string]$DbHost   = "localhost",
    [int]   $Port      = 5432,
    [string]$Username  = "postgres",
    [string]$Password  = "root",
    [string]$DbName    = "financial_rag",
    [switch]$Force
)

# 强制遇到错误即终止
$ErrorActionPreference = "Stop"
# 控制台使用 UTF-8，避免中文乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8

# ---------------- 日志辅助 ----------------
function Write-Step { param($msg) Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn2{ param($msg) Write-Host "  [!]  $msg" -ForegroundColor Yellow }
function Write-Err2 { param($msg) Write-Host "  [X]  $msg" -ForegroundColor Red }

# ---------------- 路径与文件 ----------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PublicSql = Join-Path $ScriptDir "public.sql"

Write-Host ""
Write-Host "==============================================" -ForegroundColor DarkCyan
Write-Host "  金融顾问平台 - 一键建库与数据导入" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor DarkCyan
Write-Host "  目标库 : $DbName"
Write-Host "  主机   : $DbHost`:$Port"
Write-Host "  用户   : $Username"
Write-Host "  数据   : $PublicSql"
Write-Host "==============================================" -ForegroundColor DarkCyan
Write-Host ""

# ---------------- 1. 检查 psql 客户端 ----------------
Write-Step "步骤 1/6  检查 psql 客户端..."
$psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psqlCmd) {
    Write-Err2 "未找到 psql 客户端。请先安装 PostgreSQL，或将 bin 目录加入 PATH。"
    Write-Err2 "提示：可使用 Docker 启动 pgvector 容器：docker compose up -d postgres"
    exit 1
}
Write-Ok "psql 路径: $($psqlCmd.Source)"

# 通过 PGPASSWORD 环境变量传递密码，避免命令行明文与特殊字符问题
$env:PGPASSWORD = $Password

# ---------------- 2. 测试连接 ----------------
Write-Step "步骤 2/6  测试连接 PostgreSQL..."
& psql -h $DbHost -p $Port -U $Username -d postgres -tAc "SELECT 1;" 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Err2 "无法连接 PostgreSQL，请检查 Host/Port/Username/Password 是否正确。"
    Write-Err2 "命令：psql -h $DbHost -p $Port -U $Username -d postgres"
    exit 1
}
Write-Ok "连接成功"

# ---------------- 3. 创建 / 重建数据库 ----------------
Write-Step "步骤 3/6  检查并创建数据库 $DbName ..."
$dbExists = & psql -h $DbHost -p $Port -U $Username -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DbName';"
$dbExists = "$dbExists".Trim()

if ($dbExists -eq "1") {
    if ($Force) {
        $rebuild = $true
    } else {
        Write-Warn2 "数据库 $DbName 已存在。全量导入会 DROP 并重建所有表，原有数据将丢失。"
        $ans = Read-Host "  确认删除并重建？(y/N)"
        $rebuild = ($ans -imatch "^(y|yes)$")
    }
    if ($rebuild) {
        # 先断开已有连接，避免 DROP DATABASE 被占用
        & psql -h $DbHost -p $Port -U $Username -d postgres -c `
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DbName' AND pid <> pg_backend_pid();" 2>$null | Out-Null
        & psql -h $DbHost -p $Port -U $Username -d postgres -c "DROP DATABASE IF EXISTS $DbName;" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Err2 "删除数据库失败，请手动关闭占用 $DbName 的连接后重试。"
            exit 1
        }
        & psql -h $DbHost -p $Port -U $Username -d postgres -c "CREATE DATABASE $DbName;" | Out-Null
        Write-Ok "已删除并重建数据库 $DbName"
    } else {
        Write-Warn2 "保留现有数据库，将直接在其上导入（如有同名表将被 public.sql 重建）"
    }
} else {
    & psql -h $DbHost -p $Port -U $Username -d postgres -c "CREATE DATABASE $DbName;" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Err2 "创建数据库 $DbName 失败，请确认用户 $Username 具备 CREATEDB 权限。"
        exit 1
    }
    Write-Ok "已创建数据库 $DbName"
}

# ---------------- 4. 安装扩展 ----------------
Write-Step "步骤 4/6  安装扩展 (hstore / uuid-ossp / vector) ..."
# vector 扩展需要 pgvector 镜像或编译安装；hstore / uuid-ossp 为 PostgreSQL 自带 contrib
# 注意：用临时文件 -f 执行，避免 PowerShell 把 -c 多行参数中的双引号吞掉
#       （"uuid-ossp" 的双引号丢失后，- 会被 SQL 当作减号运算符报语法错误）
$extSql = @"
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
"@
$tmpExt = Join-Path $env:TEMP "fa_init_ext.sql"
Set-Content -Path $tmpExt -Value $extSql -Encoding UTF8
& psql -h $DbHost -p $Port -U $Username -d $DbName -f $tmpExt 2>&1 | Out-Null
Remove-Item $tmpExt -ErrorAction SilentlyContinue
# 单独验证 vector 是否真正可用（其余两张表与向量检索强依赖）
$vecOk = & psql -h $DbHost -p $Port -U $Username -d $DbName -tAc "SELECT 1 FROM pg_extension WHERE extname='vector';"
if ("$vecOk".Trim() -ne "1") {
    Write-Err2 "vector 扩展安装失败。请使用支持 pgvector 的 PostgreSQL（推荐 pgvector/pgvector:pg16）。"
    Write-Err2 "Docker 方式：docker compose up -d postgres （已配置 pgvector/pgvector:pg16）"
    exit 1
}
Write-Ok "扩展已就绪 (hstore, uuid-ossp, vector)"

# ---------------- 5. 导入 public.sql ----------------
Write-Step "步骤 5/6  导入 public.sql (表结构 + 全量数据) ..."
if (-not (Test-Path $PublicSql)) {
    Write-Err2 "未找到 $PublicSql，请确认脚本与 public.sql 同位于 db 目录。"
    exit 1
}
$fileSize = [math]::Round((Get-Item $PublicSql).Length / 1KB, 1)
Write-Host "  文件大小: ${fileSize} KB，导入中请耐心等待..." -ForegroundColor DarkGray

# ON_ERROR_STOP=0：public.sql 中 DROP TYPE 扩展成员类型等非致命错误不中断导入
# 表结构与数据 INSERT 不受影响
# 注意：psql 的进度/错误写到 stderr，PowerShell 在 ErrorActionPreference=Stop 下
#       用 2>&1 会把 stderr 当 ErrorRecord 抛异常终止脚本，故改为重定向到临时日志文件再分析
$tmpLog = Join-Path $env:TEMP "fa_import.log"
# 临时放宽错误策略，确保原生命令的 stderr 不会终止脚本
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& psql -h $DbHost -p $Port -U $Username -d $DbName -v ON_ERROR_STOP=0 -f $PublicSql *> $tmpLog
$importExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP

if ($importExit -ne 0) {
    Write-Warn2 "psql 退出码: $importExit（部分非致命错误已记录，将统计如下）"
}
# 读取日志并统计 ERROR（仅作提示，不阻断）
$importLog = Get-Content $tmpLog -Encoding UTF8
$errLines = $importLog | Where-Object { $_ -match "ERROR:" }
if ($errLines) {
    $errCount = ($errLines | Measure-Object).Count
    Write-Warn2 "导入过程中出现 $errCount 条 ERROR（多为扩展类型 DROP/CREATE 冲突，可忽略）"
    $errLines | Select-Object -First 3 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
} else {
    Write-Ok "导入完成，无错误"
}
Remove-Item $tmpLog -ErrorAction SilentlyContinue

# ---------------- 6. 验证 ----------------
Write-Step "步骤 6/6  验证建库结果..."
$tableCount = & psql -h $DbHost -p $Port -U $Username -d $DbName -tAc `
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
$tableCount = "$tableCount".Trim()
Write-Ok "public schema 业务表数量: $tableCount"

# 关键表行数校验
$keyTables = @("users","assets","goals","knowledge_documents","vector_store","asset_price_history","price_alerts","spring_ai_chat_memory")
Write-Host ""
Write-Host "  关键表数据量：" -ForegroundColor DarkGray
foreach ($t in $keyTables) {
    $cnt = & psql -h $DbHost -p $Port -U $Username -d $DbName -tAc "SELECT count(*) FROM $t;" 2>$null
    $cnt = "$cnt".Trim()
    if ($cnt -match "^\d+$") {
        Write-Ok ("{0,-28} : {1} 行" -f $t, $cnt)
    } else {
        Write-Warn2 ("{0,-28} : 不存在或查询失败" -f $t)
    }
}

# ---------------- 完成 ----------------
Write-Host ""
Write-Host "==============================================" -ForegroundColor Green
Write-Ok "一键建库与全量数据导入完成！"
Write-Host "  数据库: $DbName @ $DbHost`:$Port" -ForegroundColor Green
Write-Host "  下一步: 启动后端 (mvn spring-boot:run) 即可连接使用" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""

# 清理密码环境变量，避免泄漏
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
