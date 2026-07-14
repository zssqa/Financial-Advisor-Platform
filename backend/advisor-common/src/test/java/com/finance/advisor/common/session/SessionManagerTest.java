package com.finance.advisor.common.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionManager 单元测试
 */
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    void testCreateSession() {
        String sessionId = sessionManager.createSession();
        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
    }

    @Test
    void testGetThreadId() {
        String sessionId = sessionManager.createSession();
        String threadId = sessionManager.getThreadId(sessionId);
        assertNotNull(threadId);
        assertFalse(threadId.isBlank());
    }

    @Test
    void testGetThreadIdConsistency() {
        String sessionId = sessionManager.createSession();
        String threadId1 = sessionManager.getThreadId(sessionId);
        String threadId2 = sessionManager.getThreadId(sessionId);
        assertEquals(threadId1, threadId2);
    }

    @Test
    void testGetThreadIdAutoCreate() {
        // 不存在的 sessionId 应自动创建 threadId
        String threadId = sessionManager.getThreadId("auto-session");
        assertNotNull(threadId);
    }

    @Test
    void testRemoveSession() {
        String sessionId = sessionManager.createSession();
        assertNotNull(sessionManager.getSessionInfo(sessionId));

        sessionManager.removeSession(sessionId);
        assertNull(sessionManager.getSessionInfo(sessionId));
    }

    @Test
    void testListActiveSessions() {
        String s1 = sessionManager.createSession();
        String s2 = sessionManager.createSession();
        List<String> sessions = sessionManager.listActiveSessions();
        assertTrue(sessions.size() >= 2);
        assertTrue(sessions.contains(s1));
        assertTrue(sessions.contains(s2));
    }

    @Test
    void testGetSessionInfo() {
        String sessionId = sessionManager.createSession();
        SessionManager.SessionInfo info = sessionManager.getSessionInfo(sessionId);
        assertNotNull(info);
        assertEquals(sessionId, info.getSessionId());
        assertTrue(info.getCreatedAt() > 0);
    }

    @Test
    void testGetActiveSessionCount() {
        sessionManager.createSession();
        sessionManager.createSession();
        assertTrue(sessionManager.getActiveSessionCount() >= 2);
    }
}
