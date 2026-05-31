package com.example.springqa.Q16_MvcRequestFlow.filter;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RepeatableRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public RepeatableRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 在构造时就读取并缓存整个请求体
        this.body = getBodyBytes(request);
    }

    private byte[] getBodyBytes(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream inputStream = request.getInputStream();
        byte[] data = new byte[8192]; // 8KB的缓冲区，比单字节读取更高效
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 不需要实现
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    // 额外提供方法，方便直接获取缓存的请求体内容
    public byte[] getRequestBody() {
        return body;
    }
}