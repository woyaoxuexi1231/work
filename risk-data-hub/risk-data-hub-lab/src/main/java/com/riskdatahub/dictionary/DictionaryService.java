package com.riskdatahub.dictionary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.dictionary.entity.DictItem;
import com.riskdatahub.dictionary.mapper.DictItemMapper;
import com.riskdatahub.id.LeafSegmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务 — 管理状态码与中文名称的映射。
 * <p>
 * 两个上游系统的交易状态码不同（OMS: NEW/DONE/CANCEL vs Broker: A/S/X），
 * 在 ETL 清洗时通过本服务的 {@link #translate(String, String)} 方法统一转换为中文名称。
 * 同时提供标准 CRUD 接口供管理后台使用。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryService {

    private static final String TAG_DICT_ITEM = "dict_item";

    private final RoutingMybatisExecutor routingMybatisExecutor;

    private final LeafSegmentService leafSegmentService;

    private final DictItemMapper dictItemMapper;

    /**
     * 查询全部字典项，按字典类型和编码排序。
     *
     * @return 字典项列表
     */
    public List<DictItem> listAll() {
        log.info("[字典模块] 查询全部字典项");
        return routingMybatisExecutor.query(HubConstants.DS_HUB,
                () -> dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                        .orderByAsc(DictItem::getDictType, DictItem::getDictCode)));
    }

    /**
     * 保存或更新字典项（按 dictType + dictCode 唯一键）。
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @param dictName 字典名称（中文）
     * @param dictDesc 字典描述
     */
    public void save(String dictType, String dictCode, String dictName, String dictDesc) {
        log.info("[字典模块] 保存字典 dictType={}, dictCode={}, dictName={}", dictType, dictCode, dictName);
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
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
     * <p>
     * 例如：OMS 的 'NEW' → '待确认'，Broker 的 'S' → '已成交'。
     * 如果找不到映射，回退返回原始 code 并记录告警日志。
     * </p>
     *
     * @param dictType 字典类型（如 trade_status_oms）
     * @param dictCode 原始状态码
     * @return 中文名称，找不到时返回原始 code
     */
    public String translate(String dictType, String dictCode) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () -> {
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
