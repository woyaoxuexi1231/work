package com.example.redis.c19_modules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 19. 模块与扩展（Redis Stack）
 * <p>
 * Redis Stack 将多个模块打包，扩展 Redis 的能力。
 * <p>
 * 核心模块：
 * <p>
 * 1. RedisJSON:
 *    - 原生 JSON 存储与操作
 *    - 支持 JSONPath 查询
 *    - 命令: JSON.SET、JSON.GET、JSON.DEL、JSON.TYPE
 * <p>
 * 2. RediSearch:
 *    - 全文检索引擎
 *    - 二级索引
 *    - 支持模糊查询、聚合、排序
 *    - 命令: FT.CREATE、FT.SEARCH、FT.AGGREGATE
 * <p>
 * 3. RedisTimeSeries:
 *    - 时序数据存储
 *    - 自动下采样、聚合
 *    - 命令: TS.ADD、TS.RANGE、TS.INFO
 * <p>
 * 4. RedisBloom:
 *    - 布隆过滤器（Bloom Filter）
 *    - 布谷鸟过滤器（Cuckoo Filter）
 *    - Count-Min Sketch
 *    - Top-K
 *    - 命令: BF.ADD、BF.EXISTS、CF.ADD、CMS.INCRBY
 * <p>
 * 5. RedisGraph:
 *    - 图数据库
 *    - Cypher 查询语言
 *    - 命令: GRAPH.QUERY
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModulesDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * RedisJSON 演示
     * <p>
     * RedisJSON 允许在 Redis 中存储和操作 JSON 文档。
     * 支持 JSONPath 查询，可以读写 JSON 的任意字段。
     */
    public String redisJson() {
        String commands = """
                RedisJSON 命令：

                # 存储 JSON
                JSON.SET user:1001 $ '{"name":"张三","age":28,"address":{"city":"北京","street":"长安街"}}'

                # 读取整个 JSON
                JSON.GET user:1001

                # 读取指定字段（JSONPath）
                JSON.GET user:1001 $.name
                JSON.GET user:1001 $.address.city

                # 修改字段
                JSON.SET user:1001 $.age 29
                JSON.SET user:1001 $.email "zhangsan@example.com"

                # 删除字段
                JSON.DEL user:1001 $.email

                # 数组操作
                JSON.SET user:1001 $.hobbies '["读书","游泳"]'
                JSON.ARRAPPEND user:1001 $.hobbies "跑步"

                # 类型检查
                JSON.TYPE user:1001 $.name
                JSON.TYPE user:1001 $.hobbies
                """;

        log.info("[RedisJSON]\n{}", commands);
        return "RedisJSON 命令已输出";
    }

    /**
     * RediSearch 演示
     * <p>
     * RediSearch 提供全文检索和二级索引能力。
     * 支持文本搜索、数值过滤、地理过滤、聚合等。
     */
    public String redisSearch() {
        String commands = """
                RediSearch 命令：

                # 创建索引
                FT.CREATE idx:users
                  ON HASH
                  PREFIX 1 user:
                  SCHEMA
                    name TEXT SORTABLE
                    age NUMERIC SORTABLE
                    city TAG
                    location GEO

                # 添加数据（使用普通 Hash 命令）
                HSET user:1001 name "张三" age 28 city "北京"
                HSET user:1002 name "李四" age 32 city "上海"
                HSET user:1003 name "张伟" age 25 city "北京"

                # 全文搜索
                FT.SEARCH idx:users "张"

                # 条件过滤
                FT.SEARCH idx:users "@age:[25 30] @city:{北京}"

                # 排序
                FT.SEARCH idx:users "*" SORTBY age DESC

                # 聚合
                FT.AGGREGATE idx:users "*" GROUPBY 1 @city REDUCE COUNT 0 AS count

                # 自动补全
                FT.SUGADD autocomplete "张三" 1
                FT.SUGGET autocomplete "张"
                """;

        log.info("[RediSearch]\n{}", commands);
        return "RediSearch 命令已输出";
    }

    /**
     * RedisBloom 演示
     * <p>
     * 布隆过滤器用于判断元素是否"可能存在"。
     * 特点：空间效率极高，但有假阳性（不存在的元素可能被误判为存在）。
     * 绝对不会有假阴性（存在的元素一定会被识别）。
     * <p>
     * 应用：
     * - 缓存穿透防护：判断 key 是否可能存在
     * - 去重：URL 去重、用户 ID 去重
     * - 黑名单：快速判断 IP/用户是否在黑名单中
     */
    public String redisBloom() {
        String commands = """
                RedisBloom 命令：

                # 布隆过滤器
                # 创建（自动调整参数）
                BF.RESERVE myfilter 0.001 1000000
                # 0.001 = 误判率 0.1%
                # 1000000 = 预期元素数量

                # 添加元素
                BF.ADD myfilter "user:1001"
                BF.ADD myfilter "user:1002"

                # 检查元素
                BF.EXISTS myfilter "user:1001"  # 返回 1（可能存在）
                BF.EXISTS myfilter "user:9999"  # 返回 0（一定不存在）

                # 批量操作
                BF.MADD myfilter "user:1003" "user:1004" "user:1005"
                BF.MEXISTS myfilter "user:1001" "user:9999"

                # 布谷鸟过滤器（支持删除）
                CF.RESERVE mycuckoo 1000000
                CF.ADD mycuckoo "item:1"
                CF.DEL mycuckoo "item:1"
                CF.EXISTS mycuckoo "item:1"

                # Count-Min Sketch（频率统计）
                CMS.INITBYDIM mycms 1000 5
                CMS.INCRBY mycms "item:1" 1
                CMS.QUERY mycms "item:1"

                # Top-K（热门元素）
                TOPK.RESERVE mytopk 10 50 3 0.9
                TOPK.ADD mytopk "item:1" "item:2" "item:3"
                TOPK.LIST mytopk
                """;

        log.info("[RedisBloom]\n{}", commands);
        return "RedisBloom 命令已输出";
    }

    /**
     * RedisTimeSeries 演示
     * <p>
     * RedisTimeSeries 专门用于存储和查询时序数据。
     * 支持自动下采样、聚合查询、标签过滤。
     */
    public String redisTimeSeries() {
        String commands = """
                RedisTimeSeries 命令：

                # 创建时间序列（带标签）
                TS.CREATE sensor:temp:1 LABELS room "office" type "temperature"
                TS.CREATE sensor:temp:2 LABELS room "meeting" type "temperature"

                # 添加数据点
                TS.ADD sensor:temp:1 * 25.3
                TS.ADD sensor:temp:1 * 25.5
                TS.ADD sensor:temp:1 * 25.1

                # 指定时间戳添加
                TS.ADD sensor:temp:1 1638307200000 25.3

                # 范围查询
                TS.RANGE sensor:temp:1 - + COUNT 10
                TS.RANGE sensor:temp:1 1638307200000 + FILTER_BY_VALUE 24 26

                # 聚合查询（每 5 分钟平均值）
                TS.RANGE sensor:temp:1 - + AGGREGATION avg 300000

                # 多序列查询（按标签过滤）
                TS.MRANGE - + FILTER room=office
                TS.MRANGE - + FILTER type=temperature GROUPBY room REDUCE avg

                # 自动下采样
                TS.CREATE sensor:temp:1:5min
                TS.CREATERULE sensor:temp:1 sensor:temp:1:5min AGGREGATION avg 300000
                """;

        log.info("[RedisTimeSeries]\n{}", commands);
        return "RedisTimeSeries 命令已输出";
    }
}
