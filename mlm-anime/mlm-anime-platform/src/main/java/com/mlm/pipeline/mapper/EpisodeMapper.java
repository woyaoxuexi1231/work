package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Episode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 剧集 Mapper — 基础 CRUD + 乐观锁状态更新
 * <p>
 * 除了继承 MyBatis-Plus 的标准 CRUD 方法外，提供：
 * <ul>
 *   <li>{@link #updateStatus(Long, int, int, int)} — 乐观锁 CAS 状态更新</li>
 *   <li>{@link #markStepFailed(Long, String)} — 步骤失败标记</li>
 * </ul>
 * <p>
 * 【CAS 乐观锁原理】
 * {@code UPDATE episode SET status = #{newStatus} WHERE id = #{id} AND status = #{expectedStatus}}
 * — 只有当数据库中的 status 等于期望值时才会更新成功。
 * 返回 0 表示被并发修改，返回 1 表示更新成功。
 *
 * @author mlm
 * @see com.mlm.pipeline.engine.PipelineEngine
 */
@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {

    /**
     * 乐观锁更新剧集状态
     * <p>
     * 基于 CAS（Compare And Swap）原理，仅在当前 status 等于
     * expectedStatus 时执行更新，防止并发请求导致状态错乱。
     *
     * @param id             剧集 ID
     * @param expectedStatus 期望的当前状态码（CAS 比较条件）
     * @param newStatus      目标新状态码
     * @param stepStatus     新的步骤子状态码
     * @return 受影响的行数（0=并发冲突，1=更新成功）
     */
    @Update("UPDATE episode SET status = #{newStatus}, step_status = #{stepStatus} "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") int expectedStatus,
                     @Param("newStatus") int newStatus,
                     @Param("stepStatus") int stepStatus);

    /**
     * 标记当前步骤为失败
     * <p>
     * 将 step_status 设为 -1（FAILED），同时记录错误信息。
     *
     * @param id       剧集 ID
     * @param errorMsg 失败原因
     * @return 受影响的行数
     */
    @Update("UPDATE episode SET step_status = -1, error_msg = #{errorMsg} WHERE id = #{id}")
    int markStepFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);
}
