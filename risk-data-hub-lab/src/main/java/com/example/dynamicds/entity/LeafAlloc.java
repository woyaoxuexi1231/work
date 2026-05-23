package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@TableName("leaf_alloc")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LeafAlloc {
    @TableId(type = IdType.INPUT)
    private String bizTag;
    private Long maxId;
    private Integer step;
    private String description;
}
