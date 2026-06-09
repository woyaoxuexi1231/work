# GC 问题排查练习 — gc-20260607

本目录包含 4 个 Spring Boot(JDK 1.8) 项目，每个都存在不同的 GC 问题。

## 项目列表

| 项目 | 端口 | 
|------|------|
| gc-problem-01 | 8101 |
| gc-problem-02 | 8102 |
| gc-problem-03 | 8103 |
| gc-problem-04 | 8104 |

## 启动方式

每个项目单独启动，**必须使用以下 JVM 参数**（堆128MB + GC日志）：

```bash
# 进入项目目录，执行:
cd gc-problem-01
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms128m -Xmx128m -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:./gc.log"

# 另外三个同理，替换目录名即可
```

## 排查工具建议

```bash
# 1. 找 PID
jps -lvm | grep gc-problem

# 2. 实时监控 GC（每 5 秒刷新）
jstat -gcutil <PID> 5000

# 3. 查看对象统计
jmap -histo <PID> | head -n 30

# 4. 查看 GC 日志
tail -f ./gc.log
grep "Full GC" ./gc.log

# 5. 也可通过 API 触发 GC 和查看状态
curl http://localhost:8101/status
curl http://localhost:8101/trigger-gc
```

## 提示

- 每个项目启动后约 1-3 分钟就能观察到内存趋势变化
- 重点关注 jstat 输出中的 O(老年代使用率) 和 FGC(Full GC 次数) 两列
- 用 `curl /trigger-gc` 可以手动触发一次 GC，观察回收效果
