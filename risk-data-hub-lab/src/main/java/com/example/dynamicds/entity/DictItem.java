package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("dict_item")
public class DictItem {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dictType;
    private String dictCode;
    private String dictName;
    private String dictDesc;
}
