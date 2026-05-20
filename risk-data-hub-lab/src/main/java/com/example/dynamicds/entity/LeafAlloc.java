package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("leaf_alloc")
@Data
public class LeafAlloc {
    @TableId(type = IdType.INPUT)
    private String bizTag;
    private Long maxId;
    private Integer step;
    private String description;
}
