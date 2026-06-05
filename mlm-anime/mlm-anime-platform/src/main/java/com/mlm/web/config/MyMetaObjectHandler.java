package com.mlm.web.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * <p>
 * 【职责】
 * 自动为实体中的 {@code createdAt} 和 {@code updatedAt} 字段填充当前时间，
 * 避免在每个 insert/update 操作中手动设置时间字段。
 * <p>
 * 【使用方式】
 * 实体类中对应字段需标注 {@code @TableField(fill = FieldFill.INSERT)} 等注解。
 * <p>
 * 【填充规则】
 * <ul>
 *   <li>INSERT 操作：同时填充 createdAt 和 updatedAt</li>
 *   <li>UPDATE 操作：仅填充 updatedAt</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.pipeline.entity.Project
 * @see com.mlm.pipeline.entity.Task
 * @see com.mlm.pipeline.entity.Episode
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入操作时自动填充
     * <p>
     * 同时填充 createdAt 和 updatedAt 为当前时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新操作时自动填充
     * <p>
     * 仅填充 updatedAt 为当前时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
