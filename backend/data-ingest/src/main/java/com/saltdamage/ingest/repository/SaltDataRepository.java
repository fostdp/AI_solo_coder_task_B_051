package com.saltdamage.ingest.repository;

import com.saltdamage.dto.SaltDataVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
public class SaltDataRepository {

    @Autowired
    @Qualifier("clickhouseJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO salt_data (id, chamber_id, chamber_name, device_id, device_code, device_name, " +
            "salt_concentration, conductivity, ph_value, temperature, humidity, collect_time, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public void save(SaltDataVO saltData) {
        jdbcTemplate.update(INSERT_SQL, ps -> setSaltDataParams(ps, saltData));
    }

    public void batchSave(List<SaltDataVO> dataList) {
        if (dataList == null || dataList.isEmpty()) return;

        jdbcTemplate.batchUpdate(INSERT_SQL, dataList, dataList.size(),
                (ps, saltData) -> setSaltDataParams(ps, saltData));

        log.debug("批量写入ClickHouse盐离子数据: {} 条", dataList.size());
    }

    private void setSaltDataParams(java.sql.PreparedStatement ps, SaltDataVO saltData) throws java.sql.SQLException {
        ps.setString(1, saltData.getId());
        ps.setString(2, saltData.getChamberId());
        ps.setString(3, saltData.getChamberName());
        ps.setString(4, saltData.getDeviceId());
        ps.setString(5, saltData.getDeviceCode());
        ps.setString(6, saltData.getDeviceName());
        ps.setDouble(7, saltData.getSaltConcentration());
        ps.setDouble(8, saltData.getConductivity());
        ps.setDouble(9, saltData.getPhValue());
        ps.setDouble(10, saltData.getTemperature());
        ps.setDouble(11, saltData.getHumidity());
        ps.setObject(12, saltData.getCollectTime());
        ps.setObject(13, saltData.getCreateTime());
    }

    public List<SaltDataVO> findByChamberIdAndTimeRange(Long chamberId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM salt_data WHERE chamber_id = ? AND collect_time >= ? AND collect_time <= ? ORDER BY collect_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SaltDataVO.class), chamberId, startTime, endTime);
    }

    public List<SaltDataVO> findByDeviceIdAndTimeRange(Long deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM salt_data WHERE device_id = ? AND collect_time >= ? AND collect_time <= ? ORDER BY collect_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SaltDataVO.class), deviceId, startTime, endTime);
    }

    public SaltDataVO findLatestByChamberId(Long chamberId) {
        String sql = "SELECT * FROM salt_data WHERE chamber_id = ? ORDER BY collect_time DESC LIMIT 1";
        List<SaltDataVO> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SaltDataVO.class), chamberId);
        return list.isEmpty() ? null : list.get(0);
    }

    public SaltDataVO findLatestByDeviceId(Long deviceId) {
        String sql = "SELECT * FROM salt_data WHERE device_id = ? ORDER BY collect_time DESC LIMIT 1";
        List<SaltDataVO> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SaltDataVO.class), deviceId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SaltDataVO> findLatestListByChamberId(Long chamberId, int limit) {
        String sql = "SELECT * FROM salt_data WHERE chamber_id = ? ORDER BY collect_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SaltDataVO.class), chamberId, limit);
    }
}
