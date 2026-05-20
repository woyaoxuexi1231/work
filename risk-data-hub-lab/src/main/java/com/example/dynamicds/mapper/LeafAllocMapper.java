package com.example.dynamicds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dynamicds.entity.LeafAlloc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LeafAllocMapper extends BaseMapper<LeafAlloc> {

    @Select("select biz_tag, max_id, step, description from leaf_alloc where biz_tag = #{bizTag} for update")
    LeafAlloc selectForUpdate(@Param("bizTag") String bizTag);
}
