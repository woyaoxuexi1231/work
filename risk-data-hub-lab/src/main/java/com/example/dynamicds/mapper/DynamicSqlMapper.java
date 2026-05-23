package com.example.dynamicds.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DynamicSqlMapper {

    @Update("${sql}")
    void executeSql(@Param("sql") String sql);

    @Select("select count(1) from ${tableName}")
    Integer countTable(@Param("tableName") String tableName);

    @Select("select coalesce(max(${columnName}), 0) from ${tableName}")
    Long maxValue(@Param("tableName") String tableName, @Param("columnName") String columnName);
}
