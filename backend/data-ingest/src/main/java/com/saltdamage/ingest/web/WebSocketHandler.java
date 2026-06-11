package com.saltdamage.ingest.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    private static final String HEARTBEAT_PING = "ping";
    private static final String HEARTBEAT_PONG = "pong";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接建立, sessionId: {}", session.getId());

        String userId = getUserIdFromSession(session);
        if (userId != null) {
            sessionManager.addSession(session, userId);
        } else {
            sessionManager.addSession(session);
        }

        sendWelcomeMessage(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息, sessionId: {}, payload: {}", session.getId(), payload);

        if (HEARTBEAT_PING.equals(payload)) {
            sendPong(session);
            return;
        }

        try {
            JSONObject jsonObject = JSON.parseObject(payload);
            String type = jsonObject.getString("type");

            switch (type) {
                case "HEARTBEAT" -> sendPong(session);
                case "SUBSCRIBE" -> handleSubscribe(session, jsonObject);
                case "UNSUBSCRIBE" -> handleUnsubscribe(session, jsonObject);
                default -> handleUnknownMessage(session, type);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败, sessionId: {}", session.getId(), e);
            sendErrorMessage(session, "消息格式错误");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误, sessionId: {}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接关闭, sessionId: {}, status: {}", session.getId(), status);
        sessionManager.removeSession(session.getId());
    }

    private String getUserIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "userId".equals(pair[0])) {
                        return pair[1];
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从WebSocket会话获取userId失败", e);
        }
        return null;
    }

    private void sendWelcomeMessage(WebSocketSession session) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "WELCOME");
            message.put("message", "连接成功");
            message.put("sessionId", session.getId());
            message.put("timestamp", System.currentTimeMillis());

            sendMessage(session, message);
        } catch (Exception e) {
            log.error("发送欢迎消息失败", e);
        }
    }

    private void sendPong(WebSocketSession session) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", HEARTBEAT_PONG);
            message.put("timestamp", System.currentTimeMillis());

            sendMessage(session, message);
        } catch (Exception e) {
            log.error("发送心跳响应失败", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, JSONObject jsonObject) {
        String topic = jsonObject.getString("topic");
        log.info("用户订阅主题, sessionId: {}, topic: {}", session.getId(), topic);

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "SUBSCRIBED");
            response.put("topic", topic);
            response.put("timestamp", System.currentTimeMillis());

            sendMessage(session, response);
        } catch (Exception e) {
            log.error("处理订阅失败", e);
        }
    }

    private void handleUnsubscribe(WebSocketSession session, JSONObject jsonObject) {
        String topic = jsonObject.getString("topic");
        log.info("用户取消订阅主题, sessionId: {}, topic: {}", session.getId(), topic);

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "UNSUBSCRIBED");
            response.put("topic", topic);
            response.put("timestamp", System.currentTimeMillis());

            sendMessage(session, response);
        } catch (Exception e) {
            log.error("处理取消订阅失败", e);
        }
    }

    private void handleUnknownMessage(WebSocketSession session, String type) {
        log.warn("收到未知类型消息, sessionId: {}, type: {}", session.getId(), type);
        sendErrorMessage(session, "未知消息类型: " + type);
    }

    private void sendErrorMessage(WebSocketSession session, String error) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "ERROR");
            message.put("error", error);
            message.put("timestamp", System.currentTimeMillis());

            sendMessage(session, message);
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            if (session.isOpen()) {
                String messageJson = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(messageJson));
            }
        } catch (Exception e) {
            log.error("发送WebSocket消息失败, sessionId: {}", session.getId(), e);
        }
    }
}
