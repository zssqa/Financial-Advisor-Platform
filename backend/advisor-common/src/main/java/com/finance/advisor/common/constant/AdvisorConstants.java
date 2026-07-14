package com.finance.advisor.common.constant;

/**
 * 金融顾问平台常量定义
 *
 * 集中管理所有配置Key、工具名称常量、事件类型常量。
 * 避免硬编码字符串散落在各个模块中。
 */
public final class AdvisorConstants {

    private AdvisorConstants() {}

    // ========== Agent 名称常量 ==========
    public static final String AGENT_FINANCIAL_ADVISOR = "financial_advisor";
    public static final String AGENT_ENHANCED_ADVISOR = "enhanced_financial_advisor";
    public static final String AGENT_SUPERVISOR = "supervisor_advisor";
    public static final String AGENT_FINANCIAL_PLANNER = "financial_planner";
    public static final String AGENT_RISK_ASSESSOR = "risk_assessor";
    public static final String AGENT_PRODUCT_RECOMMENDER = "product_recommender";
    public static final String AGENT_A_STOCK_ANALYST = "a_stock_analyst";
    public static final String AGENT_FUND_ANALYST = "fund_analyst";
    public static final String AGENT_MACRO_ANALYST = "macro_analyst";
    public static final String AGENT_ROUTER = "router";

    // ========== 工具名称常量 ==========
    public static final String TOOL_TAVILY_SEARCH = "tavily_web_search";
    public static final String TOOL_COMPOUND_INTEREST = "calculate_compound_interest";
    public static final String TOOL_LOAN_INTEREST = "calculate_loan_interest";
    public static final String TOOL_SEARCH_REPORTS = "search_research_reports";

    // ========== SSE 事件类型常量 ==========
    public static final String SSE_EVENT_MESSAGE = "message";
    public static final String SSE_EVENT_REASONING = "reasoning";
    public static final String SSE_EVENT_TOOL_CALL = "tool_call";
    public static final String SSE_EVENT_TOOL_RESULT = "tool_result";
    public static final String SSE_EVENT_ERROR = "error";
    public static final String SSE_EVENT_DONE = "[DONE]";

    // ========== 配置 Key 常量 ==========
    public static final String PROP_CHECKPOINT_SAVER = "advisor.checkpoint.saver";
    public static final String PROP_CONFIRMATION_AMOUNT = "advisor.confirmation.required-amount";
    public static final String PROP_COMPRESSION_MAX_MSGS = "advisor.context-compression.max-messages";
    public static final String PROP_COMPRESSION_KEEP = "advisor.context-compression.keep-after-trim";

    // ========== 输出 Key 常量 ==========
    public static final String OUTPUT_KEY_PLANNING = "planning_result";
    public static final String OUTPUT_KEY_RISK = "risk_result";
    public static final String OUTPUT_KEY_PRODUCT = "product_result";
    public static final String OUTPUT_KEY_A_STOCK = "a_stock_result";
    public static final String OUTPUT_KEY_FUND = "fund_result";
    public static final String OUTPUT_KEY_MACRO = "macro_result";
}
