package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Episode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 剧集 Mapper — 基础 CRUD + 乐观锁状态更新
 *
 * @see com.mlm.pipeline.entity.Episode
 */
@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {

    /**
     * 乐观锁方式更新剧集主状态和步骤子状态
     *
     * @return 更新行数（0=未生效，1=成功）
     */
    @Update("UPDATE episode SET status = #{newStatus}, step_status = #{stepStatus} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("newStatus") String newStatus,
                     @Param("stepStatus") String stepStatus);

    /** 标记剧集当前步骤失败并记录错误信息 */
    @Update("UPDATE episode SET step_status = 'FAILED', error_msg = #{errorMsg} WHERE id = #{id}")
    int markStepFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);
}
