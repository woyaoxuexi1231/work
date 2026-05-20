package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ID请求基类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdRequest {
    private Long id;
}
