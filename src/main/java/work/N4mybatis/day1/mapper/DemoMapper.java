package work.N4mybatis.day1.mapper;

import org.hulei.entity.mybatisplus.domain.DemoStockQuote;

import java.util.List;

/**
 * @author hulei
 * @since 2026/4/26 20:37
 */


public interface DemoMapper {

    List<DemoStockQuote> getAll();
}
