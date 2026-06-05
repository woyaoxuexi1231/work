package com.mlm.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.notification.entity.ReviewMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 审核消息 Mapper — 基础 CRUD + 批量已读标记
 * <p>
 * 【安全说明】
 * markAsReadBatch 使用 MyBatis-Plus 的 foreach 拼接参数，
 * 参数通过 PreparedStatement 绑定，杜绝 SQL 注入风险。
 *
 * @author mlm
 * @see com.mlm.notification.service.ReviewMessageService
 */
@Mapper
public interface ReviewMessageMapper extends BaseMapper<ReviewMessage> {

    /**
     * 批量标记消息为已读
     * <p>
     * 使用 MyBatis-Plus 动态 SQL 的 foreach 构造 IN 子句，
     * 每个 ID 通过 PreparedStatement 参数绑定，防止 SQL 注入。
     *
     * @param ids 要标记为已读的消息 ID 列表
     * @return 受影响的行数
     */
    @Update({
            "<script>",
            "UPDATE review_message SET is_read = 1 WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int markAsReadBatch(@Param("ids") List<Long> ids);
}
