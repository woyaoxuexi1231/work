package com.riskdatahub.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 动态 SQL 执行器 — 提供运行时动态 SQL 执行能力。
 * <p>
 * 用于需要动态拼接表名或 SQL 语句的场景，如数据统计、DDL 操作等。
 * 注意：传入的 SQL 参数会被直接拼接到语句中，<b>调用方必须自行防范 SQL 注入</b>。
 * </p>
 *
 * @author risk-data-hub
 */
public interface DynamicSqlMapper {

    /**
     * 执行动态 SQL（DDL / DML）。
     *
     * @param sql 要执行的 SQL 语句
     */
    @Update("${sql}")
    void executeSql(@Param("sql") String sql);

    /**
     * 统计指定表的记录数。
     *
     * @param tableName 表名
     * @return 记录数
     */
    @Select("select count(1) from ${tableName}")
    Integer countTable(@Param("tableName") String tableName);

    /**
     * 获取指定表中某列的最大值。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @return 最大值，表为空时返回 0
     */
    @Select("select coalesce(max(${columnName}), 0) from ${tableName}")
    Long maxValue(@Param("tableName") String tableName, @Param("columnName") String columnName);
}
