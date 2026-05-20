package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DualDataSourceTxService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(DualDataSourceTxService.class);

    private final DynamicDataSourceManager manager;
    private final LeafSegmentService leafSegmentService;

    public DualDataSourceTxService(DynamicDataSourceManager manager, LeafSegmentService leafSegmentService) {
        this.manager = manager;
        this.leafSegmentService = leafSegmentService;
    }

    public Map<String, Object> runCoordinatedWrite(String sourceSystem, boolean simulateFailure) throws InterruptedException {
        log.info("[跨库事务] 开始执行双线程协调事务 sourceSystem={}, simulateFailure={}", sourceSystem, simulateFailure);
        ensureSourceTable(sourceSystem);

        Semaphore preparedSemaphore = new Semaphore(0);
        Semaphore decisionSemaphore = new Semaphore(0);
        AtomicBoolean commit = new AtomicBoolean(true);
        List<String> timeline = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        Thread sourceThread = new Thread(() -> localPrepare(
                sourceSystem,
                "insert into tx_audit_log(audit_id, detail, created_at) values (?,?,?)",
                "source prepare for " + sourceSystem,
                preparedSemaphore,
                decisionSemaphore,
                commit,
                timeline,
                errors
        ), "tx-source-" + sourceSystem);

        Thread warehouseThread = new Thread(() -> localPrepare(
                PlatformBootstrapService.DS_HUB,
                "insert into tx_coordination_log(id, source_system, phase, detail, created_at) values (?,?,?,?,?)",
                sourceSystem,
                preparedSemaphore,
                decisionSemaphore,
                commit,
                timeline,
                errors
        ), "tx-warehouse");

        sourceThread.start();
        warehouseThread.start();

        preparedSemaphore.acquire(2);
        timeline.add("coordinator: both local tx prepared");
        log.info("[跨库事务] 两个本地事务都已经 prepare 完成，等待总控做最终决策");

        if (!errors.isEmpty() || simulateFailure) {
            commit.set(false);
            timeline.add("coordinator: rollback decision");
            log.warn("[跨库事务] 总控决策为 ROLLBACK，errors.size={}, simulateFailure={}", errors.size(), simulateFailure);
        } else {
            timeline.add("coordinator: commit decision");
            log.info("[跨库事务] 总控决策为 COMMIT");
        }

        decisionSemaphore.release(2);
        sourceThread.join();
        warehouseThread.join();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceSystem", sourceSystem);
        result.put("simulateFailure", simulateFailure);
        result.put("decision", commit.get() ? "COMMIT" : "ROLLBACK");
        result.put("timeline", timeline);
        result.put("errorCount", errors.size());
        log.info("[跨库事务] 执行结束，decision={}, errorCount={}", result.get("decision"), errors.size());
        return result;
    }

    private void localPrepare(String dataSourceKey,
                              String sql,
                              String detailOrSource,
                              Semaphore preparedSemaphore,
                              Semaphore decisionSemaphore,
                              AtomicBoolean commit,
                              List<String> timeline,
                              List<Throwable> errors) {
        DataSource dataSource = manager.getDataSource(dataSourceKey);
        long auditId = leafSegmentService.nextId("tx_audit");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // 每个线程都持有自己那一侧数据源的本地连接。
            // 这也是为什么普通 @Transactional 覆盖不了这个场景：它天然只管当前数据源的本地事务。
            if (PlatformBootstrapService.DS_HUB.equals(dataSourceKey)) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, auditId);
                    ps.setString(2, detailOrSource);
                    ps.setString(3, "PREPARED");
                    ps.setString(4, "双线程协调，等待总控放行");
                    ps.setString(5, LocalDateTime.now().format(FORMATTER));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, auditId);
                    ps.setString(2, detailOrSource);
                    ps.setString(3, LocalDateTime.now().format(FORMATTER));
                    ps.executeUpdate();
                }
            }

            synchronized (timeline) {
                timeline.add(Thread.currentThread().getName() + ": prepared");
            }
            log.info("[跨库事务] {} 已完成 prepare，挂起等待总控放行", Thread.currentThread().getName());
            preparedSemaphore.release();
            decisionSemaphore.acquire();

            if (commit.get()) {
                connection.commit();
                log.info("[跨库事务] {} 收到 COMMIT 指令，提交本地事务", Thread.currentThread().getName());
                synchronized (timeline) {
                    timeline.add(Thread.currentThread().getName() + ": committed");
                }
            } else {
                connection.rollback();
                log.info("[跨库事务] {} 收到 ROLLBACK 指令，回滚本地事务", Thread.currentThread().getName());
                synchronized (timeline) {
                    timeline.add(Thread.currentThread().getName() + ": rolled back");
                }
            }
        } catch (Exception e) {
            log.error("[跨库事务] {} 执行失败: {}", Thread.currentThread().getName(), e.getMessage(), e);
            synchronized (errors) {
                errors.add(e);
            }
            synchronized (timeline) {
                timeline.add(Thread.currentThread().getName() + ": failed -> " + e.getMessage());
            }
            preparedSemaphore.release();
        }
    }

    private void ensureSourceTable(String sourceSystem) {
        DataSource dataSource = manager.getDataSource(sourceSystem);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "create table if not exists tx_audit_log (" +
                             "audit_id bigint primary key," +
                             "detail varchar(255) not null," +
                             "created_at varchar(32) not null)")) {
            ps.execute();
        } catch (Exception e) {
            throw new RuntimeException("初始化源系统事务日志表失败: " + e.getMessage(), e);
        }
    }
}
