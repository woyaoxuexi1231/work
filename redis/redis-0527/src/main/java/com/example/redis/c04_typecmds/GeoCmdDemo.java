package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 4. 各类型核心命令 —— Geo（地理位置）
 * <p>
 * Geo 底层使用 Sorted Set 存储：
 * - 经纬度被编码为 52 位的 geohash 作为 score
 * - 成员名称作为 value
 * <p>
 * 核心命令：
 * - GEOADD: 添加地理位置
 * - GEOPOS: 获取经纬度
 * - GEODIST: 计算两点距离
 * - GEORADIUS: 以经纬度为中心搜索范围内的点
 * - GEORADIUSBYMEMBER: 以已有成员为中心搜索
 * - GEOHASH: 获取 geohash 编码
 * <p>
 * 应用场景：
 * - 附近的人/店：GEORADIUS 搜索
 * - 距离计算：GEODIST
 * - 地理围栏：结合距离判断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Geo 基础操作
     * <p>
     * GEOADD key longitude latitude member
     * 注意：经度在前，纬度在后！
     * 经度范围：-180 ~ 180
     * 纬度范围：-85.05112878 ~ 85.05112878
     */
    public String basicOps() {
        var ops = redisTemplate.opsForGeo();

        // 清空
        redisTemplate.delete("geo:shops");

        // GEOADD: 添加地理位置（北京几个著名地点）
        ops.add("geo:shops", new Point(116.397128, 39.916527), "天安门");
        ops.add("geo:shops", new Point(116.403963, 39.915119), "故宫");
        ops.add("geo:shops", new Point(116.310003, 39.992504), "颐和园");
        ops.add("geo:shops", new Point(116.343056, 39.999639), "圆明园");
        ops.add("geo:shops", new Point(116.353056, 39.999639), "清华大学");

        // GEOPOS: 获取经纬度
        List<Point> positions = ops.position("geo:shops", "天安门", "故宫");
        log.info("[GEOPOS] 天安门: {}", positions.get(0));
        log.info("[GEOPOS] 故宫: {}", positions.get(1));

        // GEODIST: 计算两点距离（单位：米）
        Distance distance = ops.distance("geo:shops", "天安门", "故宫");
        log.info("[GEODIST] 天安门到故宫: {} {}", distance.getValue(), distance.getUnit());

        // GEOHASH: 获取 geohash 编码
        List<String> hashes = ops.hash("geo:shops", "天安门", "故宫");
        log.info("[GEOHASH] 天安门={}, 故宫={}", hashes.get(0), hashes.get(1));

        redisTemplate.delete("geo:shops");
        return "距离=" + distance;
    }

    /**
     * Geo 范围搜索 —— 附近的人/店
     * <p>
     * GEORADIUS key longitude latitude radius m|km|ft|mi
     * GEORADIUSBYMEMBER key member radius m|km|ft|mi
     * <p>
     * 选项：
     * - WITHDIST: 返回距离
     * - WITHCOORD: 返回经纬度
     * - ASC/DESC: 按距离排序
     * - COUNT N: 限制返回数量
     */
    public String nearbySearch() {
        var ops = redisTemplate.opsForGeo();

        redisTemplate.delete("geo:shops");

        // 添加商家位置
        ops.add("geo:shops", new Point(116.397128, 39.916527), "天安门");
        ops.add("geo:shops", new Point(116.403963, 39.915119), "故宫");
        ops.add("geo:shops", new Point(116.310003, 39.992504), "颐和园");
        ops.add("geo:shops", new Point(116.343056, 39.999639), "圆明园");
        ops.add("geo:shops", new Point(116.353056, 39.999639), "清华大学");

        // GEORADIUS: 以天安门为中心，5公里范围内的地点
        Circle circle = new Circle(
                new Point(116.397128, 39.916527),
                new Distance(5, Metrics.KILOMETERS)
        );

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = ops.radius(
                "geo:shops",
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(10)
        );

        log.info("[附近搜索] 以天安门为中心 5km 范围内:");
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            log.info("  {} - 距离: {} km",
                    result.getContent().getName(),
                    String.format("%.2f", result.getDistance().getValue()));
        }

        // GEORADIUSBYMEMBER: 以已有成员为中心
        GeoResults<RedisGeoCommands.GeoLocation<String>> results2 = ops.radius(
                "geo:shops",
                "故宫",
                new Distance(3, Metrics.KILOMETERS),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusCommandArgs()
                        .includeDistance()
                        .sortAscending()
        );

        log.info("[附近搜索] 以故宫为中心 3km 范围内:");
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results2) {
            log.info("  {} - 距离: {} km",
                    result.getContent().getName(),
                    String.format("%.2f", result.getDistance().getValue()));
        }

        // GEOSEARCH (Redis 6.2+): 更灵活的搜索
        // 支持 FROMLONLAT 和 FROMMEMBER，支持 BYRADIUS 和 BYBOX

        redisTemplate.delete("geo:shops");
        return "搜索完成, 找到 " + results.getContent().size() + " 个地点";
    }
}
