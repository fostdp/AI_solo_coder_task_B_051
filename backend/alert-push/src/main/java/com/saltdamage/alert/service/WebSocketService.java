package com.saltdamage.alert.service;

import com.alibaba.fastjson2.JSON;
import com.saltdamage.alert.web.WebSocketSessionManager;
import com.saltdamage.entity.Alarm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final WebSocketSessionManager sessionManager;

    public void pushAlarm(Alarm alarm) {
        log.info("推送告警信息, alarmId: {}", alarm.getId());

        Map<String, Object> alarmData = new HashMap<>();
        alarmData.put("id", alarm.getId());
        alarmData.put("deviceNo", alarm.getDeviceNo());
        alarmData.put("alarmType", alarm.getAlarmType());
        alarmData.put("alarmLevel", alarm.getAlarmLevel());
        alarmData.put("alarmContent", alarm.getAlarmContent());
        alarmData.put("thresholdValue", alarm.getThresholdValue());
        alarmData.put("currentValue", alarm.getCurrentValue());
        alarmData.put("status", alarm.getStatus());
        alarmData.put("processResult", alarm.getProcessResult());
        alarmData.put("alarmTime", alarm.getAlarmTime());
        alarmData.put("processTime", alarm.getProcessTime());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "ALERT");
        message.put("data", alarmData);

        broadcastMessage(message);
    }

    public void pushMessage(String type, Object data) {
        log.debug("推送消息, type: {}", type);

        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        broadcastMessage(message);
    }

    public void sendToSession(String sessionId, String type, Object data) {
        log.debug("向指定会话发送消息, sessionId: {}, type: {}", sessionId, type);

        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session != null && session.isOpen()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            sendMessage(session, message);
        } else {
            log.warn("会话不存在或已关闭, sessionId: {}", sessionId);
        }
    }

    public void sendToUser(String userId, String type, Object data) {
        log.debug("向指定用户发送消息, userId: {}, type: {}", userId, type);

        Collection<WebSocketSession> sessions = sessionManager.getSessionsByUser(userId);
        if (sessions != null && !sessions.isEmpty()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    sendMessage(session, message);
                }
            }
        }
    }

    private void broadcastMessage(Map<String, Object> message) {
        Collection<WebSocketSession> sessions = sessionManager.getAllSessions();
        if (sessions == null || sessions.isEmpty()) {
            log.debug("没有在线的WebSocket会话");
            return;
        }

        String messageJson = JSON.toJSONString(message);
        TextMessage textMessage = new TextMessage(messageJson);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    log.debug("消息已发送, sessionId: {}", session.getId());
                } catch (IOException e) {
                    log.error("发送WebSocket消息失败, sessionId: {}", session.getId(), e);
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String messageJson = JSON.toJSONString(message);
            TextMessage textMessage = new TextMessage(messageJson);
            session.sendMessage(textMessage);
            log.debug("消息已发送, sessionId: {}", session.getId());
        } catch (IOException e) {
            log.error("发送WebSocket消息失败, sessionId: {}", session.getId(), e);
        }
    }

    public int getOnlineCount() {
        return sessionManager.getSessionCount();
    }

    public Collection<String> getOnlineUsers() {
        return sessionManager.getAllUserIds();
    }
}
