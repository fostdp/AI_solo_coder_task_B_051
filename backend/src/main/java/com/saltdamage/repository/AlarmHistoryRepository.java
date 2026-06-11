package com.saltdamage.repository;

import com.saltdamage.dto.AlarmVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AlarmHistoryRepository {

    @Autowired
    @Qualifier("clickhouseJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public void save(AlarmVO alarm) {
        String sql = "INSERT INTO alarm_history (id, device_id, device_code, device_name, chamber_id, chamber_name, " +
                "alarm_type, alarm_name, severity, alarm_message, alarm_data, status, alarm_time, handle_time, " +
                "handle_user, handle_remark, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                alarm.getId(),
                alarm.getDeviceId(),
                alarm.getDeviceCode(),
                alarm.getDeviceName(),
                alarm.getChamberId(),
                alarm.getChamberName(),
                alarm.getAlarmType(),
                alarm.getAlarmName(),
                alarm.getSeverity(),
                alarm.getAlarmMessage(),
                alarm.getAlarmData(),
                alarm.getStatus(),
                alarm.getAlarmTime(),
                alarm.getHandleTime(),
                alarm.getHandleUser(),
                alarm.getHandleRemark(),
                alarm.getCreateTime()
        );
    }

    public List<AlarmVO> findByChamberIdAndTimeRange(Long chamberId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM alarm_history WHERE chamber_id = ? AND alarm_time >= ? AND alarm_time <= ? ORDER BY alarm_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), chamberId, startTime, endTime);
    }

    public List<AlarmVO> findByDeviceIdAndTimeRange(Long deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM alarm_history WHERE device_id = ? AND alarm_time >= ? AND alarm_time <= ? ORDER BY alarm_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), deviceId, startTime, endTime);
    }

    public List<AlarmVO> findByStatus(String status) {
        String sql = "SELECT * FROM alarm_history WHERE status = ? ORDER BY alarm_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), status);
    }

    public List<AlarmVO> findBySeverity(String severity) {
        String sql = "SELECT * FROM alarm_history WHERE severity = ? ORDER BY alarm_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), severity);
    }

    public List<AlarmVO> findByStatusAndSeverity(String status, String severity) {
        String sql = "SELECT * FROM alarm_history WHERE status = ? AND severity = ? ORDER BY alarm_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), status, severity);
    }

    public List<AlarmVO> findLatestList(int limit) {
        String sql = "SELECT * FROM alarm_history ORDER BY alarm_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AlarmVO.class), limit);
    }

    public long countByStatus(String status) {
        String sql = "SELECT count(*) FROM alarm_history WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, status);
        return count != null ? count : 0;
    }
}
