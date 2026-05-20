package work.N1javabasic.v1.all;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author hulei
 * @since 2026/5/20 18:45
 */

public class GamePointRankTest {
    public static void main(String[] args) {
        Rank rank = new Rank();
        // 先打印此次总体排名
        rank.map.forEach((k, v) -> System.out.println(k + ": " + v));

        // 原子地删除旧分数，加入新分数。
        rank.update("player_13", 1000);

        // 返回分数最高的 n 名玩家（从高到低）。
        Set<String> top = rank.getTop(2);
        System.out.println(top);

        // 返回该玩家的排名（从 1 开始）。
        Integer playerRank = rank.getPlayerRank("player_13");
        System.out.println("玩家 player_13 的排名：" + playerRank);

        Set<String> nearbyPlayers = rank.getNearbyPlayers("player_13", 2000);
        System.out.println("玩家 player_13 附近2000分的玩家：" + nearbyPlayers);
    }

}


class Rank {

    // 先是这么写的，遇到同分的就不行了，会被替换掉，那 String 就要改
    // ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
    // 改为 Set<String>
    ConcurrentSkipListMap<Integer, Set<String>> map = new ConcurrentSkipListMap<>();

    public Rank() {
        generateRealisticGameLeaderboard(map, new AtomicInteger(1));
    }

    private static void generateRealisticGameLeaderboard(
            ConcurrentSkipListMap<Integer, Set<String>> leaderboard,
            AtomicInteger userIdCounter
    ) {
        // 游戏真实数据分布策略：
        // 1. 高分段稀疏（顶级玩家少） 2. 中分段密集 3. 低分段大量新手
        Random random = new Random();

        // 生成100个分数点（非均匀分布）
        for (int i = 0; i < 100; i++) {
            // 分数计算：模拟真实游戏分布
            // - 前10% (高分段)：10000~8000分（稀疏分布）
            // - 中间80%：8000~2000分（密集分布）
            // - 末尾10%：2000~100分（大量新手）
            int score;
            if (i < 10) {
                score = 10000 - random.nextInt(2000); // 8000-10000
            } else if (i < 90) {
                score = 2000 + random.nextInt(6000);  // 2000-8000
            } else {
                score = 100 + random.nextInt(1900);   // 100-2000
            }

            // 该分数的玩家数量：高分段少，低分段多
            int playerCount = i < 10 ?
                    1 + random.nextInt(3) :  // 高分段：1-3人
                    5 + random.nextInt(50);  // 其他段：5-55人

            // 生成唯一用户名集合
            Set<String> players = new HashSet<>();
            for (int j = 0; j < playerCount; j++) {
                players.add("player_" + userIdCounter.getAndIncrement());
            }

            // 线程安全地存入排行榜（值设为不可变集合）
            leaderboard.put(score, players);
        }
    }

    public void update(String player, int score) {
        // 先要找到这个玩家在哪里
        Integer currentScore = null;
        for (Map.Entry<Integer, Set<String>> entry : map.entrySet()) {
            Set<String> players = entry.getValue();
            if (players.contains(player)) {
                currentScore = entry.getKey();
            }
        }
        if (Objects.isNull(currentScore)) {
            throw new RuntimeException("玩家不存在");
        }
        map.computeIfPresent(currentScore, (key, value) -> {
            value.remove(player);
            return value;
        });
        map.compute(score, (key, value) -> {
            if (Objects.isNull(value)) {
                value = new HashSet<>();
            }
            value.add(player);
            return value;
        });
    }

    public Set<String> getTop(int n) {
        return map.descendingMap() // 降序
                .entrySet() // 获取所有键值对
                .stream() // 转换为流
                .flatMap(entry -> entry.getValue().stream()) // 获取值并展开
                .limit(n) // 取前n个
                .collect(Collectors.toSet());
    }

    public Integer getPlayerRank(String playerId) {
        // 先找到在哪里
        Map.Entry<Integer, Set<String>> integerSetEntry = map.entrySet().stream()
                .filter(entry -> entry.getValue().contains(playerId))
                .findFirst()
                .orElseGet(() -> null);
        // headMap 获取小于等于指定键的元素，返回一个Map
        return integerSetEntry == null ? null : map.headMap(integerSetEntry.getKey()).size() + 1;
    }

    /**
     * 返回与玩家分数差距在 ±range 内的玩家。
     *
     * @param playerId
     * @param n
     * @return
     */
    public Set<String> getNearbyPlayers(String playerId, int n) {
        // 先找到玩家在哪里
        Map.Entry<Integer, Set<String>> integerSetEntry = map.entrySet().stream()
                .filter(entry -> entry.getValue().contains(playerId))
                .findFirst()
                .orElseGet(() -> null);
        if (integerSetEntry == null) {
            return null;
        }
        Integer playerScore = integerSetEntry.getKey();
        ConcurrentNavigableMap<Integer, Set<String>> headMap = map.headMap(playerScore + n);
        ConcurrentNavigableMap<Integer, Set<String>> tailMap = map.tailMap(playerScore - n);
        // 求交集
        return headMap.entrySet().stream()
                .filter(entry -> tailMap.containsKey(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());
    }
}