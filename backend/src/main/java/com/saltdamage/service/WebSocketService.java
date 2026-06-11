package com.saltdamage.service;

import com.alibaba.fastjson2.JSON;
import com.saltdamage.dto.AlarmDTO;
import com.saltdamage.dto.MonitorDataDTO;
import com.saltdamage.entity.Alarm;
import com.saltdamage.entity.SensorData;
import com.saltdamage.web.WebSocketSessionManager;
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

    public void pushRealtimeData(SensorData sensorData) {
        log.debug("推送实时数据, deviceNo: {}", sensorData.getDeviceNo());

        MonitorDataDTO dataDTO = convertToMonitorDTO(sensorData);
        Map<String, Object> message = new HashMap<>();
        message.put("type", "REALTIME_DATA");
        message.put("data", dataDTO);

        broadcastMessage(message);
    }

    public void pushAlarm(Alarm alarm) {
        log.info("推送告警信息, alarmId: {}", alarm.getId());

        AlarmDTO alarmDTO = convertToAlarmDTO(alarm);
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ALERT");
        message.put("data", alarmDTO);

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

    private MonitorDataDTO convertToMonitorDTO(SensorData sensorData) {
        MonitorDataDTO dto = new MonitorDataDTO();
        dto.setId(sensorData.getId());
        dto.setDeviceNo(sensorData.getDeviceNo());
        dto.setTombId(sensorData.getTombId());
        dto.setChamberId(sensorData.getChamberId());
        dto.setSaltConcentration(sensorData.getSaltConcentration());
        dto.setTemperature(sensorData.getTemperature());
        dto.setHumidity(sensorData.getHumidity());
        dto.setPhValue(sensorData.getPhValue());
        dto.setCo2Concentration(sensorData.getCo2Concentration());
        dto.setIlluminance(sensorData.getIlluminance());
        dto.setPressure(sensorData.getPressure());
        dto.setTotalSaltAmount(sensorData.getTotalSaltAmount());
        dto.setCollectTime(sensorData.getCollectTime());
        return dto;
    }

    private AlarmDTO convertToAlarmDTO(Alarm alarm) {
        AlarmDTO dto = new AlarmDTO();
        dto.setId(alarm.getId());
        dto.setDeviceNo(alarm.getDeviceNo());
        dto.setAlarmType(alarm.getAlarmType());
        dto.setAlarmLevel(alarm.getAlarmLevel());
        dto.setAlarmContent(alarm.getAlarmContent());
        dto.setThresholdValue(alarm.getThresholdValue());
        dto.setCurrentValue(alarm.getCurrentValue());
        dto.setStatus(alarm.getStatus());
        dto.setProcessResult(alarm.getProcessResult());
        dto.setAlarmTime(alarm.getAlarmTime());
        dto.setProcessTime(alarm.getProcessTime());
        return dto;
    }
}
