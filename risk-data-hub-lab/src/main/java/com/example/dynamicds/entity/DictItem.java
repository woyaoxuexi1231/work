package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("dict_item")
@Data
public class DictItem {
    @TableId
    private Long id;
    private String dictType;
    private String dictCode;
    private String dictName;
    private String dictDesc;
}
