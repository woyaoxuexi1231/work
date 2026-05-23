package com.mlm.model.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mlm.common.enums.ModelType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("model_config")
public class ModelConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vendor;

    private ModelType modelType;

    private String apiEndpoint;

    @JsonIgnore
    private String apiKey;

    private Integer pollInterval = 30;

    private Integer maxPollCount = 60;

    private Integer maxRetries = 3;

    private Boolean isEnabled = true;
}
