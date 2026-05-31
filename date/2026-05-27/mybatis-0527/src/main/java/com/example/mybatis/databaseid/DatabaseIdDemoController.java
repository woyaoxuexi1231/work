package com.example.mybatis.databaseid;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mybatis.databaseid.entity.Article;
import com.example.mybatis.databaseid.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * DatabaseId演示Controller
 *
 * 演示MyBatis如何处理不同数据库的分页语法差异
 */
@Slf4j
@RestController
@RequestMapping("/databaseid")
@RequiredArgsConstructor
public class DatabaseIdDemoController {

    private final ArticleMapper articleMapper;
    private final DatabaseIdProvider databaseIdProvider;
    private final DataSource dataSource;

    /**
     * 添加文章
     */
    @PostMapping("/article")
    public String addArticle(@RequestBody Article article) {
        articleMapper.insert(article);
        return "success";
    }

    /**
     * 查询所有文章
     */
    @GetMapping("/articles")
    public List<Article> listArticles() {
        return articleMapper.selectList(null);
    }

    /**
     * 【演示1】使用databaseId进行分页查询
     *
     * MyBatis会根据当前数据库类型自动选择对应的SQL
     * 例如：
     * - MySQL会使用LIMIT语法
     * - Oracle会使用ROWNUM语法
     * - PostgreSQL会使用LIMIT...OFFSET语法
     */
    @GetMapping("/page/databaseid")
    public List<Article> pageByDatabaseId(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        int offset = (pageNum - 1) * pageSize;

        log.info("【databaseId分页】页码: {}, 每页大小: {}, 偏移量: {}", pageNum, pageSize, offset);

        // 调用使用databaseId的Mapper方法
        // MyBatis会自动选择当前数据库对应的SQL版本
        return articleMapper.selectByPageWithDatabaseId(offset, pageSize);
    }

    /**
     * 【演示2】使用MyBatis-Plus分页插件（推荐）
     *
     * MyBatis-Plus的分页插件会自动处理不同数据库的分页语法差异
     * 无需手动编写分页SQL
     */
    @GetMapping("/page/plugin")
    public IPage<Article> pageByPlugin(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        log.info("【分页插件】页码: {}, 每页大小: {}", pageNum, pageSize);

        // 创建分页对象
        Page<Article> page = new Page<>(pageNum, pageSize);

        // 执行分页查询（自动处理分页语法）
        return articleMapper.selectPage(page, null);
    }

    /**
     * 【演示3】使用动态SQL进行分页查询
     *
     * 通过传入数据库类型参数，动态选择不同的分页SQL
     */
    @GetMapping("/page/dynamic")
    public List<Article> pageByDynamicSql(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "mysql") String dbType) {

        int offset = (pageNum - 1) * pageSize;

        log.info("【动态SQL分页】页码: {}, 每页大小: {}, 数据库类型: {}", pageNum, pageSize, dbType);

        return articleMapper.selectByPageWithDynamicSql(dbType, offset, pageSize);
    }

    /**
     * 查看当前数据库信息
     */
    @GetMapping("/info")
    public String getDatabaseInfo() {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();

            String productName = metaData.getDatabaseProductName();
            String productVersion = metaData.getDatabaseProductVersion();
            String driverName = metaData.getDriverName();

            // 获取databaseId
            String databaseId = databaseIdProvider.getDatabaseId(dataSource);

            return String.format(
                    "数据库产品: %s\n" +
                    "数据库版本: %s\n" +
                    "驱动名称: %s\n" +
                    "DatabaseId: %s\n\n" +
                    "MyBatis会根据DatabaseId自动选择对应的SQL",
                    productName, productVersion, driverName, databaseId
            );
        } catch (SQLException e) {
            return "获取数据库信息失败: " + e.getMessage();
        }
    }

    /**
     * 【说明】各数据库的分页语法
     *
     * MySQL:
     *   SELECT * FROM table LIMIT offset, limit
     *   例如: SELECT * FROM t_article LIMIT 0, 10
     *
     * Oracle:
     *   SELECT * FROM (
     *     SELECT t.*, ROWNUM rn FROM (
     *       SELECT * FROM table ORDER BY id
     *     ) t WHERE ROWNUM <= offset + limit
     *   ) WHERE rn > offset
     *
     * PostgreSQL:
     *   SELECT * FROM table LIMIT limit OFFSET offset
     *   例如: SELECT * FROM t_article LIMIT 10 OFFSET 0
     *
     * SQL Server:
     *   SELECT * FROM table
     *   ORDER BY id
     *   OFFSET offset ROWS
     *   FETCH NEXT limit ROWS ONLY
     *
     * DB2:
     *   SELECT * FROM table
     *   ORDER BY id
     *   FETCH FIRST limit ROWS ONLY
     *
     * 达梦数据库:
     *   与Oracle类似，支持ROWNUM
     *
     * 人大金仓:
     *   与PostgreSQL类似，支持LIMIT...OFFSET
     */
    @GetMapping("/syntax")
    public String getPaginationSyntax() {
        return "请查看 MyBatis-多数据库分页处理方案.md 文档";
    }
}
