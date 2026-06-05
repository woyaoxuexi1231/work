package com.example.dynamicds.bootstrap;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import com.example.dynamicds.service.LeafSegmentService;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 中台库初始化 — 从 SQL 文件执行 DDL + 种子数据，全部幂等可重复执行。
 */
@Slf4j
@Service
public class HubInitializer {

    private static final String SQL_HUB_SCHEMA = "sql/bootstrap/hub-schema.sql";
    private static final String SQL_HUB_SEED   = "sql/bootstrap/hub-seed.sql";

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final DynamicSqlMapper dynamicSqlMapper;
    private final String hubUrl;

    @Value("${app.ddl.enabled:true}")
    private boolean ddlEnabled;

    public HubInitializer(DynamicDataSourceManager manager,
                          RoutingMybatisExecutor routingMybatisExecutor,
                          LeafSegmentService leafSegmentService,
                          DynamicSqlMapper dynamicSqlMapper,
                          @Value("${spring.datasource.url}") String hubUrl) {
        this.manager = manager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.leafSegmentService = leafSegmentService;
        this.dynamicSqlMapper = dynamicSqlMapper;
        this.hubUrl = hubUrl;
    }

    @PostConstruct
    public void init() {
        manager.putHubConfig(HubConstants.DS_HUB, "中台库", hubUrl);
        if (ddlEnabled) {
            executeSqlFile(SQL_HUB_SCHEMA);
        }
        executeSqlFile(SQL_HUB_SEED);
        leafSegmentService.clearLocalCache();
        log.info("[HubInitializer] 中台库初始化完成, ddl.enabled={}", ddlEnabled);
    }

    /** 从 classpath 读取 SQL 文件，按分号分割逐条执行 */
    private void executeSqlFile(String classpath) {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            try {
                ClassPathResource resource = new ClassPathResource(classpath);
                String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                int count = 0;
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    dynamicSqlMapper.executeSql(trimmed);
                    count++;
                }
                log.info("[HubInitializer] 执行 SQL 文件完成: {} ({} 条语句)", classpath, count);
            } catch (Exception e) {
                throw new IllegalStateException("执行 SQL 文件失败: " + classpath, e);
            }
        });
    }
}
