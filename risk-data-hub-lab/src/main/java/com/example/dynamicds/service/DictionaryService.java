package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DictionaryService {

    private final RoutingJdbcExecutor jdbcExecutor;

    public DictionaryService(RoutingJdbcExecutor jdbcExecutor) {
        this.jdbcExecutor = jdbcExecutor;
    }

    public List<Map<String, Object>> listAll() {
        return jdbcExecutor.query(PlatformBootstrapService.DS_META, jdbc ->
                jdbc.queryForList("select id, dict_type, dict_code, dict_name, dict_desc from dict_item order by dict_type, dict_code"));
    }

    public void save(String dictType, String dictCode, String dictName, String dictDesc) {
        jdbcExecutor.run(PlatformBootstrapService.DS_META, jdbc -> {
            Integer count = jdbc.queryForObject(
                    "select count(1) from dict_item where dict_type = ? and dict_code = ?",
                    Integer.class,
                    dictType, dictCode);
            if (count != null && count > 0) {
                jdbc.update("update dict_item set dict_name = ?, dict_desc = ? where dict_type = ? and dict_code = ?",
                        dictName, dictDesc, dictType, dictCode);
            } else {
                jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?,?,?,?)",
                        dictType, dictCode, dictName, dictDesc);
            }
        });
    }

    public String translate(String dictType, String dictCode) {
        return jdbcExecutor.query(PlatformBootstrapService.DS_META, jdbc -> {
            List<String> values = jdbc.query(
                    "select dict_name from dict_item where dict_type = ? and dict_code = ?",
                    (rs, rowNum) -> rs.getString(1),
                    dictType, dictCode);
            return values.isEmpty() ? dictCode : values.get(0);
        });
    }
}
