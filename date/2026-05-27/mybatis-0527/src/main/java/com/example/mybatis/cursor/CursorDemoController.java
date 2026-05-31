package com.example.mybatis.cursor;

import com.example.mybatis.cursor.entity.Product;
import com.example.mybatis.cursor.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 游标查询演示Controller
 */
@Slf4j
@RestController
@RequestMapping("/cursor")
@RequiredArgsConstructor
public class CursorDemoController {

    private final CursorDemoService cursorDemoService;
    private final ProductMapper productMapper;

    /**
     * 添加测试商品
     */
    @PostMapping("/product")
    public String addProduct(@RequestBody Product product) {
        productMapper.insert(product);
        return "success";
    }

    /**
     * 批量添加测试商品
     */
    @PostMapping("/product/batch/{count}")
    public String batchAddProducts(@PathVariable int count) {
        for (int i = 1; i <= count; i++) {
            Product product = new Product();
            product.setName("商品" + i);
            product.setCategory("分类" + (i % 10));
            product.setPrice(new BigDecimal(100 + i));
            product.setStock(1000);
            product.setStatus(1);
            productMapper.insert(product);
        }
        return "成功添加" + count + "条商品数据";
    }

    /**
     * 查询所有商品
     */
    @GetMapping("/products")
    public List<Product> listProducts() {
        return productMapper.selectList(null);
    }

    /**
     * 【演示1】使用Cursor对象遍历
     */
    @GetMapping("/demo1")
    public String demo1() {
        cursorDemoService.demo1_cursorObject();
        return "演示1完成，请查看控制台日志";
    }

    /**
     * 【演示2】使用ResultHandler处理
     */
    @GetMapping("/demo2")
    public String demo2() {
        cursorDemoService.demo2_resultHandler();
        return "演示2完成，请查看控制台日志";
    }

    /**
     * 【演示3】导出数据到文件
     */
    @GetMapping("/demo3")
    public String demo3() {
        String filePath = "D:/project/work/mybatis/product_export.csv";
        cursorDemoService.demo3_exportToFile(filePath);
        return "演示3完成，数据已导出到: " + filePath;
    }

    /**
     * 【演示4】手动管理SqlSession
     */
    @GetMapping("/demo4")
    public String demo4() {
        cursorDemoService.demo4_manualSqlSession();
        return "演示4完成，请查看控制台日志";
    }

    /**
     * 【演示5】内存占用对比
     */
    @GetMapping("/demo5")
    public String demo5() throws IOException {
        cursorDemoService.demo5_memoryComparison();
        return "演示5完成，请查看控制台日志";
    }
}
