package com.saltdamage.ingest.repository;

import com.saltdamage.dto.EnvDataVO;
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
public class EnvDataRepository {

    @Autowired
    @Qualifier("clickhouseJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO env_data (id, chamber_id, chamber_name, device_id, device_code, device_name, " +
            "temperature, humidity, co2_concentration, illuminance, air_pressure, wind_speed, collect_time, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public void save(EnvDataVO envData) {
        jdbcTemplate.update(INSERT_SQL, ps -> setEnvDataParams(ps, envData));
    }

    public void batchSave(List<EnvDataVO> dataList) {
        if (dataList == null || dataList.isEmpty()) return;

        jdbcTemplate.batchUpdate(INSERT_SQL, dataList, dataList.size(),
                (ps, envData) -> setEnvDataParams(ps, envData));

        log.debug("批量写入ClickHouse环境数据: {} 条", dataList.size());
    }

    private void setEnvDataParams(java.sql.PreparedStatement ps, EnvDataVO envData) throws java.sql.SQLException {
        ps.setString(1, envData.getId());
        ps.setString(2, envData.getChamberId());
        ps.setString(3, envData.getChamberName());
        ps.setString(4, envData.getDeviceId());
        ps.setString(5, envData.getDeviceCode());
        ps.setString(6, envData.getDeviceName());
        ps.setDouble(7, envData.getTemperature());
        ps.setDouble(8, envData.getHumidity());
        ps.setDouble(9, envData.getCo2Concentration());
        ps.setDouble(10, envData.getIlluminance());
        ps.setDouble(11, envData.getAirPressure());
        ps.setDouble(12, envData.getWindSpeed());
        ps.setObject(13, envData.getCollectTime());
        ps.setObject(14, envData.getCreateTime());
    }

    public List<EnvDataVO> findByChamberIdAndTimeRange(Long chamberId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM env_data WHERE chamber_id = ? AND collect_time >= ? AND collect_time <= ? ORDER BY collect_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnvDataVO.class), chamberId, startTime, endTime);
    }

    public List<EnvDataVO> findByDeviceIdAndTimeRange(Long deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT * FROM env_data WHERE device_id = ? AND collect_time >= ? AND collect_time <= ? ORDER BY collect_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnvDataVO.class), deviceId, startTime, endTime);
    }

    public EnvDataVO findLatestByChamberId(Long chamberId) {
        String sql = "SELECT * FROM env_data WHERE chamber_id = ? ORDER BY collect_time DESC LIMIT 1";
        List<EnvDataVO> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnvDataVO.class), chamberId);
        return list.isEmpty() ? null : list.get(0);
    }

    public EnvDataVO findLatestByDeviceId(Long deviceId) {
        String sql = "SELECT * FROM env_data WHERE device_id = ? ORDER BY collect_time DESC LIMIT 1";
        List<EnvDataVO> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnvDataVO.class), deviceId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<EnvDataVO> findLatestListByChamberId(Long chamberId, int limit) {
        String sql = "SELECT * FROM env_data WHERE chamber_id = ? ORDER BY collect_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnvDataVO.class), chamberId, limit);
    }
}
