package com.example.mybatis.cursor;

import com.example.mybatis.cursor.entity.Product;
import com.example.mybatis.cursor.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游标查询演示Service
 *
 * 【什么是游标查询？】
 * 游标查询（Cursor Query）是一种数据库查询方式，它不会一次性将所有结果加载到内存，
 * 而是通过游标逐行读取数据，类似于数据库中的游标概念。
 *
 * 【与普通查询的对比】
 *
 * +------------------+------------------------+------------------------+
 * |     特性          |      普通查询           |      游标查询           |
 * +------------------+------------------------+------------------------+
 * | 数据加载方式       | 一次性加载所有数据到内存  | 逐行读取，按需加载       |
 * +------------------+------------------------+------------------------+
 * | 内存占用          | 数据量大时内存占用高      | 内存占用恒定，与数据量无关 |
 * +------------------+------------------------+------------------------+
 * | 首条数据响应时间   | 需要等待所有数据加载完成   | 快速返回第一行数据       |
 * +------------------+------------------------+------------------------+
 * | 适用场景          | 数据量小、需要随机访问    | 数据量大、流式处理       |
 * +------------------+------------------------+------------------------+
 * | 处理方式          | 返回List，可多次遍历     | 单次遍历，不可回退       |
 * +------------------+------------------------+------------------------+
 *
 * 【游标查询的优势】
 *
 * 1. 内存效率高
 *    - 普通查询：查询100万条数据，假设每条1KB，需要约1GB内存
 *    - 游标查询：无论查询多少条数据，内存占用恒定（通常几MB）
 *
 * 2. 适合大数据量场景
 *    - 数据导出：将数据库数据导出为CSV、Excel等文件
 *    - 数据迁移：从一个表迁移到另一个表
 *    - 批量处理：对大量数据进行逐行处理
 *    - 流式计算：实时处理数据流
 *
 * 3. 快速响应
 *    - 第一行数据可以在数据库还在查询时就开始处理
 *    - 适用于需要快速响应的场景
 *
 * 【游标查询的限制】
 *
 * 1. 只能向前遍历，不能回退
 * 2. 需要保持数据库连接打开状态
 * 3. 不能进行随机访问
 * 4. 某些数据库驱动需要特殊配置
 *
 * 【MySQL配置说明】
 * 要在MySQL中实现真正的流式读取，需要：
 * 1. 在连接URL中添加 useCursorFetch=true
 * 2. 设置 fetchSize = Integer.MIN_VALUE
 * 3. 使用 FORWARD_ONLY 类型的结果集
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CursorDemoService {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    /**
     * 【演示1】使用Cursor对象遍历数据
     *
     * Cursor实现了Iterable接口，可以使用for-each循环遍历
     * 使用完毕后必须关闭Cursor，释放数据库连接
     */
    public void demo1_cursorObject() {
        log.info("========== 演示1：使用Cursor对象遍历 ==========");

        // 使用try-with-resources确保Cursor被关闭
        try (Cursor<Product> cursor = productMapper.selectProductsByCursor(1)) {
            AtomicInteger count = new AtomicInteger(0);

            // Cursor实现了Iterable，可以直接for-each
            for (Product product : cursor) {
                int currentCount = count.incrementAndGet();
                log.info("处理第{}条数据: id={}, name={}, price={}",
                        currentCount, product.getId(), product.getName(), product.getPrice());

                // 模拟处理耗时
                if (currentCount % 100 == 0) {
                    log.info("已处理{}条数据...", currentCount);
                }
            }

            log.info("演示1完成，共处理{}条数据", count.get());
        } catch (Exception e) {
            log.error("游标查询异常", e);
        }
    }

    /**
     * 【演示2】使用ResultHandler逐行处理
     *
     * ResultHandler方式更加灵活，可以在处理每行数据时执行自定义逻辑
     * 适用于需要对每行数据进行复杂处理的场景
     */
    public void demo2_resultHandler() {
        log.info("========== 演示2：使用ResultHandler逐行处理 ==========");

        AtomicInteger count = new AtomicInteger(0);

        // 使用ResultHandler处理每一行数据
        productMapper.selectProductsByHandler(new BigDecimal("100"), new ResultHandler<Product>() {
            @Override
            public void handleResult(ResultContext<? extends Product> context) {
                Product product = context.getResultObject();
                int currentCount = count.incrementAndGet();

                log.info("ResultHandler处理: id={}, name={}, price={}",
                        product.getId(), product.getName(), product.getPrice());

                // 可以在这里执行任意逻辑：
                // 1. 写入文件
                // 2. 发送到消息队列
                // 3. 调用其他服务
                // 4. 累加统计
            }
        });

        log.info("演示2完成，共处理{}条数据", count.get());
    }

    /**
     * 【演示3】大数据量导出到文件
     *
     * 这是游标查询最典型的使用场景之一
     * 使用游标查询可以导出海量数据而不会OOM
     */
    public void demo3_exportToFile(String filePath) {
        log.info("========== 演示3：导出数据到文件 ==========");
        log.info("导出文件路径: {}", filePath);

        AtomicInteger count = new AtomicInteger(0);

        try (Cursor<Product> cursor = productMapper.selectAllProductsByCursor();
             BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

            // 写入CSV表头
            writer.write("id,name,category,price,stock,status");
            writer.newLine();

            // 逐行写入数据
            for (Product product : cursor) {
                String line = String.format("%d,%s,%s,%s,%d,%d",
                        product.getId(),
                        product.getName(),
                        product.getCategory(),
                        product.getPrice(),
                        product.getStock(),
                        product.getStatus());
                writer.write(line);
                writer.newLine();

                int currentCount = count.incrementAndGet();
                if (currentCount % 1000 == 0) {
                    log.info("已导出{}条数据...", currentCount);
                }
            }

            log.info("演示3完成，共导出{}条数据到{}", count.get(), filePath);
        } catch (IOException e) {
            log.error("导出文件异常", e);
        }
    }

    /**
     * 【演示4】使用SqlSession手动管理游标查询
     *
     * 在某些场景下，可能需要手动管理SqlSession的生命周期
     * 例如：需要在同一个SqlSession中执行多次查询
     */
    public void demo4_manualSqlSession() {
        log.info("========== 演示4：手动管理SqlSession ==========");

        // 手动获取SqlSession
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            ProductMapper mapper = sqlSession.getMapper(ProductMapper.class);
            Cursor<Product> cursor = mapper.selectProductsByCursor(1);

            AtomicInteger count = new AtomicInteger(0);
            for (Product product : cursor) {
                count.incrementAndGet();
                // 处理数据...
            }

            log.info("演示4完成，共处理{}条数据", count.get());
        } catch (Exception e) {
            log.error("手动SqlSession异常", e);
        }
    }

    /**
     * 【演示5】对比普通查询和游标查询的内存占用
     */
    public void demo5_memoryComparison() throws IOException {
        log.info("========== 演示5：内存占用对比 ==========");

        // 获取当前内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        log.info("查询前内存占用: {} MB", beforeMemory / 1024 / 1024);

        // 普通查询（会加载所有数据到内存）
        // List<Product> allProducts = productMapper.selectList(null);
        // long afterListMemory = runtime.totalMemory() - runtime.freeMemory();
        // log.info("普通查询后内存占用: {} MB", afterListMemory / 1024 / 1024);
        // log.info("普通查询内存增量: {} MB", (afterListMemory - beforeMemory) / 1024 / 1024);

        // 游标查询（逐行处理，内存占用恒定）
        AtomicInteger count = new AtomicInteger(0);
        try (Cursor<Product> cursor = productMapper.selectAllProductsByCursor()) {
            for (Product product : cursor) {
                count.incrementAndGet();
                // 只处理，不保存到集合
            }
        }

        long afterCursorMemory = runtime.totalMemory() - runtime.freeMemory();
        log.info("游标查询后内存占用: {} MB", afterCursorMemory / 1024 / 1024);
        log.info("游标查询内存增量: {} MB", (afterCursorMemory - beforeMemory) / 1024 / 1024);
        log.info("游标查询处理数据量: {} 条", count.get());
    }
}
