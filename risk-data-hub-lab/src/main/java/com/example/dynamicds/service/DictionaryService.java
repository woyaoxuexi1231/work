package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.DictItem;
import com.example.dynamicds.mapper.DictItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务 — 管理状态码映射。
 * 两个上游系统的交易状态码不同（OMS: NEW/DONE/CANCEL vs Broker: A/S/X），
 * 在 ETL 清洗时通过本服务的 translate() 方法统一转换为中文名称。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DictionaryService {

    private static final String TAG_DICT_ITEM = "dict_item";

    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final DictItemMapper dictItemMapper;

    /**
     * 字典既给管理后台用，也在 ETL 清洗时做状态码和券商编码映射。
     * 这种“标准 CRUD + 条件查询”的场景最适合切到 MyBatis-Plus。
     */
    public List<DictItem> listAll() {
        log.info("[字典模块] 查询全部字典项");
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB,
                () -> dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                        .orderByAsc(DictItem::getDictType, DictItem::getDictCode)));
    }

    public void save(String dictType, String dictCode, String dictName, String dictDesc) {
        log.info("[字典模块] 保存字典 dictType={}, dictCode={}, dictName={}", dictType, dictCode, dictName);
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            DictItem exist = dictItemMapper.selectOne(new LambdaQueryWrapper<DictItem>()
                    .eq(DictItem::getDictType, dictType)
                    .eq(DictItem::getDictCode, dictCode)
                    .last("limit 1"));
            if (exist == null) {
                DictItem item = new DictItem();
                item.setId(leafSegmentService.nextId(TAG_DICT_ITEM));
                item.setDictType(dictType);
                item.setDictCode(dictCode);
                item.setDictName(dictName);
                item.setDictDesc(dictDesc);
                dictItemMapper.insert(item);
                return;
            }
            exist.setDictName(dictName);
            exist.setDictDesc(dictDesc);
            dictItemMapper.updateById(exist);
        });
    }

    /**
     * 将上游系统的原始状态码翻译为中文名称。
     * 例如：OMS 的 'NEW' → '待确认'，Broker 的 'S' → '已成交'
     * 如果找不到映射，回退返回原始 code 并记录告警。
     */
    public String translate(String dictType, String dictCode) {
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () -> {
            DictItem item = dictItemMapper.selectOne(new LambdaQueryWrapper<DictItem>()
                    .eq(DictItem::getDictType, dictType)
                    .eq(DictItem::getDictCode, dictCode)
                    .last("limit 1"));
            if (item == null) {
                log.warn("[字典模块] 未找到映射 dictType={}, dictCode={}，回退为原始 code", dictType, dictCode);
                return dictCode;
            }
            return item.getDictName();
        });
    }
}
