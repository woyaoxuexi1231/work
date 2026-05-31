package com.example.mybatis.cursor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mybatis.cursor.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;

import java.math.BigDecimal;

/**
 * 商品Mapper接口 - 演示游标查询
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 【游标查询示例1】使用Cursor返回游标对象
     *
     * 关键配置：
     * 1. @Options(resultSetType = ResultSetType.FORWARD_ONLY) - 设置结果集类型为只向前滚动
     * 2. @Options(fetchSize = Integer.MIN_VALUE) - 设置fetchSize为Integer.MIN_VALUE
     *    这是MySQL驱动的特殊配置，表示逐行读取，不会一次性加载所有数据到内存
     *
     * 注意：在MySQL中，要实现真正的流式读取，必须：
     * 1. 使用 Statement.executeQuery() 而不是 PreparedStatement
     * 2. 或者在创建PreparedStatement时设置 fetchSize = Integer.MIN_VALUE
     * 3. 并且在连接URL中添加 useCursorFetch=true
     */
    @Select("SELECT * FROM t_product WHERE status = #{status}")
    @Options(resultSetType = ResultSetType.FORWARD_ONLY, fetchSize = Integer.MIN_VALUE)
    Cursor<Product> selectProductsByCursor(@org.apache.ibatis.annotations.Param("status") Integer status);

    /**
     * 【游标查询示例2】使用ResultHandler逐行处理
     *
     * 这种方式更灵活，可以在处理每一行数据时执行自定义逻辑，
     * 而不需要等所有数据都加载到内存。
     *
     * 适用场景：
     * - 数据量大，无法一次性加载到内存
     * - 需要逐行处理并输出
     * - 流式写入文件或发送到消息队列
     */
    @Select("SELECT * FROM t_product WHERE price > #{minPrice}")
    @Options(resultSetType = ResultSetType.FORWARD_ONLY, fetchSize = Integer.MIN_VALUE)
    void selectProductsByHandler(@org.apache.ibatis.annotations.Param("minPrice") BigDecimal minPrice,
                                  ResultHandler<Product> handler);

    /**
     * 【游标查询示例3】通过XML配置的游标查询
     * 对应的XML配置在 resources/mapper/cursor/ProductMapper.xml
     */
    Cursor<Product> selectAllProductsByCursor();
}
