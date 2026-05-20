package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Episode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 剧集 Mapper — 基础 CRUD + 乐观锁状态更新（int 编码）
 */
@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {

    /** 乐观锁更新状态（CAS based on expected status） */
    @Update("UPDATE episode SET status = #{newStatus}, step_status = #{stepStatus} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") int expectedStatus,
                     @Param("newStatus") int newStatus,
                     @Param("stepStatus") int stepStatus);

    /** 标记失败 */
    @Update("UPDATE episode SET step_status = -1, error_msg = #{errorMsg} WHERE id = #{id}")
    int markStepFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);
}
