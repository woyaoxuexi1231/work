package com.example.mybatis.databaseid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mybatis.databaseid.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章Mapper接口 - 演示不同数据库的分页语法
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 【方式1】使用databaseId进行数据库方言切换
     *
     * 在同一个Mapper方法中，针对不同数据库编写不同的SQL
     * MyBatis会根据当前数据库类型自动选择对应的SQL
     */
    List<Article> selectByPageWithDatabaseId(@Param("offset") int offset,
                                             @Param("limit") int limit);

    /**
     * 【方式2】使用MyBatis-Plus的分页插件（推荐）
     *
     * MyBatis-Plus内置了分页插件，会自动处理不同数据库的分页语法差异
     * 支持：MySQL、Oracle、PostgreSQL、SQL Server等
     */
    List<Article> selectByPageWithPlugin(@Param("pageNum") int pageNum,
                                         @Param("pageSize") int pageSize);

    /**
     * 【方式3】使用动态SQL + 数据库类型判断
     *
     * 通过传入数据库类型参数，动态选择不同的SQL
     */
    List<Article> selectByPageWithDynamicSql(@Param("dbType") String dbType,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);
}
