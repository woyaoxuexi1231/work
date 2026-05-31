package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户领域对象
 *
 * <p>Spring MVC 中，这个对象会被 Jackson 的 MappingJackson2HttpMessageConverter
 * 自动序列化为 JSON、反序列化从 JSON 还原。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String name;
    private String email;
}
