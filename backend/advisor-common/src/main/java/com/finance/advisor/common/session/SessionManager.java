package com.finance.advisor.common.session;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户会话管理
 *
 * 维护会话ID与Agent图执行线程ID的映射，确保同一会话的所有对话
 * 在同一 Agent 执行线程上下文中，实现对话历史连续性。
 *
 * 隔离策略: 按 (userId, sessionId) 复合维度隔离，内部映射 key 为
 * {@code userId + ":" + sessionId}，不同用户即使 sessionId 相同也互不影响。
 *
 * 复用关系: 配合 spring-ai-alibaba-graph-core 的 RunnableConfig.threadId 概念，
 * 使得每个会话对应一个独立的 Agent 执行线程，实现会话级隔离。
 *
 * 来源参考: spring-ai-alibaba-graph-core RunnableConfig 类中的 threadId 概念
 */
@Component
public class SessionManager {

    /** 旧方法（不带 userId）委托时使用的默认 userId，保证向后兼容 */
    private static final String DEFAULT_USER_ID = "default";

    private final ConcurrentHashMap<String, String> sessionToThread = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> threadToSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    /** 组合 key：userId + ":" + sessionId */
    private static String compositeKey(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    /**
     * 创建新会话（按 userId 隔离）
     *
     * @param userId 用户ID
     * @return 新生成的会话ID
     */
    public String createSession(String userId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        String sessionId = UUID.randomUUID().toString();
        String threadId = UUID.randomUUID().toString();
        String key = compositeKey(userId, sessionId);
        sessionToThread.put(key, threadId);
        threadToSession.put(threadId, key);
        sessionInfoMap.put(key, new SessionInfo(sessionId, userId, System.currentTimeMillis()));
        return sessionId;
    }

    /**
     * 创建新会话（不区分用户）
     *
     * @deprecated 使用 {@link #createSession(String)} 按 userId 隔离
     */
    @Deprecated
    public String createSession() {
        return createSession(DEFAULT_USER_ID);
    }

    /**
     * 获取会话对应的线程ID（按 userId 隔离），如果不存在则自动创建
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 对应的线程ID
     */
    public String getThreadId(String userId, String sessionId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        final String uid = userId;
        String key = compositeKey(uid, sessionId);
        return sessionToThread.computeIfAbsent(key, k -> {
            String threadId = UUID.randomUUID().toString();
            threadToSession.put(threadId, k);
            sessionInfoMap.put(k, new SessionInfo(sessionId, uid, System.currentTimeMillis()));
            return threadId;
        });
    }

    /**
     * 获取会话对应的线程ID，如果不存在则自动创建
     *
     * @param sessionId 会话ID
     * @return 对应的线程ID
     * @deprecated 使用 {@link #getThreadId(String, String)} 按 userId 隔离
     */
    @Deprecated
    public String getThreadId(String sessionId) {
        return getThreadId(DEFAULT_USER_ID, sessionId);
    }

    /**
     * 删除会话（按 userId 隔离）
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     */
    public void removeSession(String userId, String sessionId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        String key = compositeKey(userId, sessionId);
        String threadId = sessionToThread.remove(key);
        if (threadId != null) {
            threadToSession.remove(threadId);
        }
        sessionInfoMap.remove(key);
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @deprecated 使用 {@link #removeSession(String, String)} 按 userId 隔离
     */
    @Deprecated
    public void removeSession(String sessionId) {
        removeSession(DEFAULT_USER_ID, sessionId);
    }

    /**
     * 获取会话信息（按 userId 隔离）
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 会话信息，不存在返回null
     */
    public SessionInfo getSessionInfo(String userId, String sessionId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        return sessionInfoMap.get(compositeKey(userId, sessionId));
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息，不存在返回null
     * @deprecated 使用 {@link #getSessionInfo(String, String)} 按 userId 隔离
     */
    @Deprecated
    public SessionInfo getSessionInfo(String sessionId) {
        return getSessionInfo(DEFAULT_USER_ID, sessionId);
    }

    /**
     * 获取指定用户的活跃会话ID
     *
     * @param userId 用户ID
     * @return 会话ID列表（按创建时间倒序）
     */
    public List<String> listActiveSessions(String userId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        final String uid = userId;
        return sessionInfoMap.values().stream()
                .filter(s -> uid.equals(s.userId))
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .map(s -> s.sessionId)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃会话ID
     *
     * @return 会话ID列表
     * @deprecated 使用 {@link #listActiveSessions(String)} 按 userId 隔离
     */
    @Deprecated
    public List<String> listActiveSessions() {
        return listActiveSessions(DEFAULT_USER_ID);
    }

    /**
     * 获取指定用户的活跃会话信息
     *
     * @param userId 用户ID
     * @return 会话信息列表（按创建时间倒序）
     */
    public List<SessionInfo> listActiveSessionInfos(String userId) {
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        final String uid = userId;
        return sessionInfoMap.values().stream()
                .filter(s -> uid.equals(s.userId))
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃会话信息
     *
     * @return 会话信息列表
     * @deprecated 使用 {@link #listActiveSessionInfos(String)} 按 userId 隔离
     */
    @Deprecated
    public List<SessionInfo> listActiveSessionInfos() {
        return listActiveSessionInfos(DEFAULT_USER_ID);
    }

    /**
     * 获取活跃会话数（全局，跨所有用户）
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessionInfoMap.size();
    }

    /**
     * 会话信息
     */
    public static class SessionInfo {
        private final String sessionId;
        private final String userId;
        private final long createdAt;
        private long lastActiveAt;

        SessionInfo(String sessionId, String userId, long createdAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.createdAt = createdAt;
            this.lastActiveAt = createdAt;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getLastActiveAt() {
            return lastActiveAt;
        }

        public void updateLastActive() {
            this.lastActiveAt = System.currentTimeMillis();
        }
    }
}
