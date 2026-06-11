package com.saltdamage.transport.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.clickhouse.url:jdbc:clickhouse://localhost:8123/salt_damage}")
    private String clickhouseUrl;

    @Value("${spring.datasource.clickhouse.username:default}")
    private String clickhouseUsername;

    @Value("${spring.datasource.clickhouse.password:}")
    private String clickhousePassword;

    @Value("${spring.datasource.mysql.url:jdbc:mysql://localhost:3306/salt_damage?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai}")
    private String mysqlUrl;

    @Value("${spring.datasource.mysql.username:root}")
    private String mysqlUsername;

    @Value("${spring.datasource.mysql.password:root}")
    private String mysqlPassword;

    @Value("${spring.datasource.mysql.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String mysqlDriver;

    @Bean
    @Qualifier("clickhouseDataSource")
    public DataSource clickhouseDataSource() {
        Properties properties = new Properties();
        properties.setProperty("user", clickhouseUsername);
        properties.setProperty("password", clickhousePassword);
        properties.setProperty("socket_timeout", "30000");
        properties.setProperty("connection_timeout", "5000");
        ClickHouseDataSource dataSource = new ClickHouseDataSource(clickhouseUrl, properties);
        return dataSource;
    }

    @Bean
    @Qualifier("clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource clickhouseDataSource) {
        return new JdbcTemplate(clickhouseDataSource);
    }

    @Bean
    @Qualifier("mysqlDataSource")
    public DataSource mysqlDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(mysqlUrl);
        dataSource.setUsername(mysqlUsername);
        dataSource.setPassword(mysqlPassword);
        dataSource.setDriverClassName(mysqlDriver);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setIdleTimeout(30000);
        dataSource.setConnectionTimeout(10000);
        return dataSource;
    }

    @Bean
    @Qualifier("mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }
}
