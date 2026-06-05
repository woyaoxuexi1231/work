package com.riskdatahub.id.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riskdatahub.id.entity.LeafAlloc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Leaf 发号器分配记录 Mapper。
 *
 * @author risk-data-hub
 */
public interface LeafAllocMapper extends BaseMapper<LeafAlloc> {

    /**
     * 悲观锁查询：SELECT ... FOR UPDATE 防止多实例同时申请号段导致 ID 重复。
     *
     * @param bizTag 业务标签
     * @return 发号器分配记录
     */
    @Select("select biz_tag, max_id, step, description from leaf_alloc where biz_tag = #{bizTag} for update")
    LeafAlloc selectForUpdate(@Param("bizTag") String bizTag);
}
