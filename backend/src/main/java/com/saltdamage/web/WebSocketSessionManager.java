package com.saltdamage.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket会话已添加, sessionId: {}", sessionId);
    }

    public void addSession(WebSocketSession session, String userId) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionUserMap.put(sessionId, userId);

        userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(sessionId, session);

        log.info("WebSocket会话已添加, sessionId: {}, userId: {}", sessionId, userId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        String userId = sessionUserMap.remove(sessionId);

        if (userId != null) {
            ConcurrentHashMap<String, WebSocketSession> userSessionMap = userSessions.get(userId);
            if (userSessionMap != null) {
                userSessionMap.remove(sessionId);
                if (userSessionMap.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }

        log.info("WebSocket会话已移除, sessionId: {}, userId: {}", sessionId, userId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    public Collection<WebSocketSession> getSessionsByUser(String userId) {
        ConcurrentHashMap<String, WebSocketSession> userSessionMap = userSessions.get(userId);
        if (userSessionMap != null) {
            return userSessionMap.values();
        }
        return java.util.Collections.emptyList();
    }

    public String getUserIdBySession(String sessionId) {
        return sessionUserMap.get(sessionId);
    }

    public Collection<String> getAllUserIds() {
        return userSessions.keySet();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public int getUserCount() {
        return userSessions.size();
    }

    public boolean isSessionOpen(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public Collection<String> getOnlineSessionIds() {
        return sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .map(WebSocketSession::getId)
                .collect(Collectors.toList());
    }
}
