package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
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

    private final DynamicDataSourceManager manager;
    private final LeafSegmentService leafSegmentService;

    public DualDataSourceTxService(DynamicDataSourceManager manager, LeafSegmentService leafSegmentService) {
        this.manager = manager;
        this.leafSegmentService = leafSegmentService;
    }

    public Map<String, Object> runCoordinatedWrite(String sourceSystem, boolean simulateFailure) throws InterruptedException {
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
                PlatformBootstrapService.DS_WAREHOUSE,
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

        if (!errors.isEmpty() || simulateFailure) {
            commit.set(false);
            timeline.add("coordinator: rollback decision");
        } else {
            timeline.add("coordinator: commit decision");
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
            if (PlatformBootstrapService.DS_WAREHOUSE.equals(dataSourceKey)) {
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
            preparedSemaphore.release();
            decisionSemaphore.acquire();

            if (commit.get()) {
                connection.commit();
                synchronized (timeline) {
                    timeline.add(Thread.currentThread().getName() + ": committed");
                }
            } else {
                connection.rollback();
                synchronized (timeline) {
                    timeline.add(Thread.currentThread().getName() + ": rolled back");
                }
            }
        } catch (Exception e) {
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
