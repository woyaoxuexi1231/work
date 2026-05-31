package com.example.demo.converter;

import com.example.demo.model.User;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 HttpMessageConverter —— 让 API 支持 CSV 格式的请求/响应
 *
 * <h3>HttpMessageConverter 是什么？</h3>
 * <p>它是 {@code @ResponseBody} / {@code @RequestBody} 的底层执行者。
 * Spring 内置了 Jackson（JSON）、String、ByteArray 等 Converter，
 * 当 Controller 标注 {@code @ResponseBody} 时，
 * {@code RequestResponseBodyMethodProcessor} 遍历所有 Converter，
 * 找到第一个能序列化当前返回类型的。</p>
 *
 * <h3>核心方法</h3>
 * <ul>
 *   <li>{@code supports(Class)}    —— 我能写这个类型吗？</li>
 *   <li>{@code readInternal()}     —— 从 HTTP 请求体反序列化（处理 @RequestBody）</li>
 *   <li>{@code writeInternal()}    —— 序列化到 HTTP 响应体（处理 @ResponseBody）</li>
 * </ul>
 *
 * <h3>测试方法</h3>
 * <pre>
 *   # JSON（默认）
 *   curl -H "Accept: application/json" http://localhost:8080/users
 *
 *   # CSV（由本 Converter 处理）
 *   curl -H "Accept: text/csv" http://localhost:8080/users
 * </pre>
 */
public class CsvHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    private static final Logger log = LoggerFactory.getLogger(CsvHttpMessageConverter.class);

    private static final MediaType CSV_MEDIA_TYPE = MediaType.valueOf("text/csv");

    public CsvHttpMessageConverter() {
        super(StandardCharsets.UTF_8, CSV_MEDIA_TYPE);
    }

    public CsvHttpMessageConverter(MediaType... supportedMediaTypes) {
        super(StandardCharsets.UTF_8, supportedMediaTypes);
    }

    // ======================= 能力声明 =======================

    @Override
    protected boolean supports(Class<?> clazz) {
        // 支持 User 类型和 List 类型
        if (User.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (List.class.isAssignableFrom(clazz)) {
            return true;
        }
        return false;
    }

    // ======================= 写（序列化 → HTTP 响应）=======================

    /**
     * 将 Java 对象序列化为 CSV 写入 HTTP 响应体。
     *
     * <p>调用时机：Controller 方法标注了 @ResponseBody 且 Accept 头匹配 text/csv。
     * Spring 在 {@code AbstractMessageConverterMethodProcessor.writeWithMessageConverters()}
     * 中遍历 Converter 列表，找到本类后调用此方法。</p>
     */
    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {

        outputMessage.getHeaders().setContentType(CSV_MEDIA_TYPE);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            // 写表头
            csvWriter.writeNext(new String[]{"id", "name", "email"});

            // 写数据行
            List<User> users = toUserList(object);
            for (User user : users) {
                csvWriter.writeNext(new String[]{
                        String.valueOf(user.getId()),
                        user.getName(),
                        user.getEmail()
                });
            }

            log.info("✅ CsvHttpMessageConverter 序列化了 {} 条记录", users.size());
        }
    }

    // ======================= 读（反序列化 ← HTTP 请求）=======================

    /**
     * 从 HTTP 请求体中读取 CSV 并反序列化为 Java 对象。
     *
     * <p>调用时机：Controller 方法参数标注了 @RequestBody 且 Content-Type 匹配 text/csv
     */
    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {

        try (InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            List<String[]> rows;
            try {
                rows = csvReader.readAll();
            } catch (Exception e) {
                throw new HttpMessageNotReadableException("CSV 解析失败: " + e.getMessage(), e, inputMessage);
            }
            List<User> users = new ArrayList<>();

            // 跳过表头，从第二行开始读
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                User user = new User(
                        Long.parseLong(row[0]),
                        row[1],
                        row[2]
                );
                users.add(user);
            }

            log.info("✅ CsvHttpMessageConverter 反序列化了 {} 条记录", users.size());

            // 根据目标类型返回
            if (List.class.isAssignableFrom(clazz)) {
                return users;
            }
            return users.isEmpty() ? null : users.get(0);
        }
    }

    // ======================= 辅助方法 =======================

    @SuppressWarnings("unchecked")
    private List<User> toUserList(Object object) {
        if (object instanceof List) {
            return (List<User>) object;
        }
        if (object instanceof User) {
            return Collections.singletonList((User) object);
        }
        return Collections.emptyList();
    }
}
