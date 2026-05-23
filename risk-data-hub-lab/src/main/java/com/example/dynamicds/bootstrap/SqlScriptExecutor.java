package com.example.dynamicds.bootstrap;

import com.example.dynamicds.mapper.DynamicSqlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SqlScriptExecutor {

    private final ResourceLoader resourceLoader;
    private final DynamicSqlMapper dynamicSqlMapper;

    public void executeClasspathScript(String classpathLocation) {
        for (String statement : splitStatements(readClasspathScript(classpathLocation))) {
            try {
                dynamicSqlMapper.executeSql(statement);
            } catch (Exception e) {
                if (isIgnorableAlterException(statement, e)) {
                    continue;
                }
                throw e;
            }
        }
    }

    private String readClasspathScript(String classpathLocation) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathLocation);
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read sql script failed: " + classpathLocation, e);
        }
    }

    private List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
                continue;
            }
            current.append(line).append(' ');
            if (line.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1).trim());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private boolean isIgnorableAlterException(String statement, Exception e) {
        String normalized = statement.trim().toUpperCase();
        if (!normalized.startsWith("ALTER TABLE") || !normalized.contains("ADD COLUMN")) {
            return false;
        }
        String message = e.getMessage();
        return message != null && message.contains("Duplicate column name");
    }
}
