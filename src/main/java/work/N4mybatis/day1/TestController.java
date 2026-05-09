package work.N4mybatis.day1;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hulei.entity.mybatisplus.domain.DemoStockQuote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import work.N4mybatis.day1.mapper.DemoMapper;

import java.io.InputStream;
import java.util.List;

/**
 * @author hulei
 * @since 2026/4/26 20:31
 */

@RequestMapping("/mybatis/day1")
@RestController
public class TestController {

    @GetMapping("/test")
    public void test() {
        try {
            // 加载 mybatis-config.xml（包含数据源和 mapper 扫描配置）
            String resource = "day1/mybatis-config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            
            // 构建 SqlSessionFactory
            SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
            
            // 执行查询
            try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
                DemoMapper mapper = sqlSession.getMapper(DemoMapper.class);
                List<DemoStockQuote> all = mapper.getAll();
                all.forEach(System.out::println);
                
                System.out.println("✅ 查询成功，共 " + all.size() + " 条记录");
            }
        } catch (Exception e) {
            System.err.println("❌ 执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
