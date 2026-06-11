package com.saltdamage.alert.service;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson2.JSON;
import com.saltdamage.alert.repository.AlarmConfigRepository;
import com.saltdamage.entity.Alarm;
import com.saltdamage.entity.AlarmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkService {

    private final AlarmConfigRepository alarmConfigRepository;

    @Value("${dingtalk.webhook:}")
    private String defaultWebhook;

    @Value("${dingtalk.secret:}")
    private String defaultSecret;

    public void sendAlarmMessage(Alarm alarm) {
        log.info("发送钉钉告警消息, alarmId: {}", alarm.getId());

        AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc().orElse(null);
        String webhook = config != null && config.getDingTalkWebhook() != null
                ? config.getDingTalkWebhook() : defaultWebhook;
        String secret = config != null && config.getDingTalkSecret() != null
                ? config.getDingTalkSecret() : defaultSecret;

        if (webhook == null || webhook.isEmpty()) {
            log.warn("钉钉Webhook未配置，跳过发送");
            return;
        }

        try {
            String signUrl = buildSignedUrl(webhook, secret);
            Map<String, Object> message = buildAlarmMessage(alarm);
            String messageJson = JSON.toJSONString(message);

            HttpResponse response = HttpRequest.post(signUrl)
                    .header("Content-Type", "application/json")
                    .body(messageJson)
                    .execute();

            String responseBody = response.body();
            log.info("钉钉消息发送响应: {}", responseBody);

            Map<String, Object> responseMap = JSON.parseObject(responseBody, Map.class);
            Integer errcode = (Integer) responseMap.get("errcode");
            if (errcode == null || errcode != 0) {
                throw new RuntimeException("钉钉消息发送失败: " + responseBody);
            }

        } catch (Exception e) {
            log.error("发送钉钉告警消息失败", e);
            throw new RuntimeException("发送钉钉告警消息失败: " + e.getMessage(), e);
        }
    }

    public void sendCustomMessage(String title, String content, String[] atMobiles) {
        log.info("发送钉钉自定义消息, title: {}", title);

        AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc().orElse(null);
        String webhook = config != null && config.getDingTalkWebhook() != null
                ? config.getDingTalkWebhook() : defaultWebhook;
        String secret = config != null && config.getDingTalkSecret() != null
                ? config.getDingTalkSecret() : defaultSecret;

        if (webhook == null || webhook.isEmpty()) {
            log.warn("钉钉Webhook未配置，跳过发送");
            return;
        }

        try {
            String signUrl = buildSignedUrl(webhook, secret);
            Map<String, Object> message = buildMarkdownMessage(title, content, atMobiles);
            String messageJson = JSON.toJSONString(message);

            HttpResponse response = HttpRequest.post(signUrl)
                    .header("Content-Type", "application/json")
                    .body(messageJson)
                    .execute();

            String responseBody = response.body();
            log.info("钉钉自定义消息发送响应: {}", responseBody);

        } catch (Exception e) {
            log.error("发送钉钉自定义消息失败", e);
            throw new RuntimeException("发送钉钉自定义消息失败: " + e.getMessage(), e);
        }
    }

    private String buildSignedUrl(String webhook, String secret) throws Exception {
        if (secret == null || secret.isEmpty()) {
            return webhook;
        }

        Long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.encode(signData), StandardCharsets.UTF_8);
        return webhook + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    private Map<String, Object> buildAlarmMessage(Alarm alarm) {
        String levelText = switch (alarm.getAlarmLevel()) {
            case "high" -> "🔴 高危";
            case "medium" -> "🟡 中危";
            case "low" -> "🟢 低危";
            default -> "⚪ 未知";
        };

        String typeText = switch (alarm.getAlarmType()) {
            case "SALT_EXCEEDED" -> "盐离子超标";
            case "HUMIDITY_EXCEEDED" -> "湿度超标";
            case "TEMPERATURE_EXCEEDED" -> "温度超标";
            case "CRYSTALLIZATION_PRESSURE" -> "结晶压力超标";
            default -> alarm.getAlarmType();
        };

        StringBuilder content = new StringBuilder();
        content.append("## 【").append(levelText).append("】盐害监测系统告警\n\n");
        content.append("**告警类型**: ").append(typeText).append("\n\n");
        content.append("**设备编号**: ").append(alarm.getDeviceNo()).append("\n\n");
        content.append("**告警时间**: ").append(alarm.getAlarmTime()).append("\n\n");
        content.append("**告警内容**: ").append(alarm.getAlarmContent()).append("\n\n");
        content.append("**当前值**: ").append(alarm.getCurrentValue()).append("\n\n");
        content.append("**阈值**: ").append(alarm.getThresholdValue()).append("\n\n");
        content.append("> 请及时处理该告警！");

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", "盐害监测系统告警");
        markdown.put("text", content.toString());

        Map<String, Object> at = new HashMap<>();
        at.put("isAtAll", false);

        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        message.put("markdown", markdown);
        message.put("at", at);

        return message;
    }

    private Map<String, Object> buildMarkdownMessage(String title, String content, String[] atMobiles) {
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", content);

        Map<String, Object> at = new HashMap<>();
        at.put("isAtAll", false);
        if (atMobiles != null && atMobiles.length > 0) {
            at.put("atMobiles", atMobiles);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        message.put("markdown", markdown);
        message.put("at", at);

        return message;
    }

    public boolean verifySignature(String timestamp, String sign, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String expectedSign = Base64.encode(signData);
            return expectedSign.equals(sign);
        } catch (Exception e) {
            log.error("签名验证失败", e);
            return false;
        }
    }
}
