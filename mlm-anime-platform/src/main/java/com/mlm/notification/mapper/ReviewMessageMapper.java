package com.mlm.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.notification.entity.ReviewMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 审核消息 Mapper
 */
@Mapper
public interface ReviewMessageMapper extends BaseMapper<ReviewMessage> {

    /** 批量标记已读 */
    @Update("UPDATE review_message SET is_read = 1 WHERE id IN (${ids})")
    int markAsRead(@Param("ids") String ids);
}
