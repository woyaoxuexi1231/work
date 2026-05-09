# Day 13 面试打分报告：ThreadLocal 与 CompletableFuture

> **面试官点评**：你对 `ThreadLocal` 的理解终于从“听说过”进阶到了“见过猪跑”，但离“吃过猪肉”还有一段距离。回答过于简略，像是在应付考试，而不是在展示你的深度。尤其是 `CompletableFuture` 部分，简直是在敷衍我。

---

## 一、总体评分表

### 面试题部分（满分 60 分）

| 题号 | 原题摘要 | 得分 | 面试官内心反应 |
|------|----------|------|--------------------------------------|
| 1    | ThreadLocal 内存泄漏与弱引用 | 12/20 | 讲到了点子上，但对“为什么 Value 会泄露”没说透 |
| 2    | 线程池环境下的风险与解决 | 14/20 | 知道要 remove，但没解释为什么线程池会放大这个风险 |
| 3    | CompletableFuture 异步任务组合 | 5/20 | 这也叫回答？你就报了两个方法名？ |
| **面试题总分** | | **31/60** | **不及格。深度完全不够，CompletableFuture 部分零分预警。** |

### 编程题部分（满分 100 分）

| 评分维度 | 满分 | 得分 | 面试官真实评价 |
|----------|------|------|------------------------------|
| 正确性 | 40 | 35 | 逻辑正确，基本的 set/get/remove 都有 |
| 复杂度分析 | 20 | 10 | 没写复杂度分析，默认扣一半 |
| 代码风格 | 20 | 15 | `@SneakyThrows` 用得挺溜，但 CompletableFuture 部分没写完 |
| 鲁棒性 | 20 | 10 | 线程池环境、异常环境完全没考虑 |
| **编程题总分** | **100** | **70/100** | **勉强及格，代码太薄，没有展示出对复杂场景的处理。** |

### 最终综合总分
- **面试题得分**：31/60 (折合 52/100)
- **编程题得分**：70/100
- **综合总分**：**122/200**
- **一句话定生死**：**ThreadLocal 基础算过了，但异步编排（CompletableFuture）完全是空白，建议滚回去重学这一块。**

---

## 二、逐题血淋淋复盘

### 面试题 1：ThreadLocal 内存泄漏与弱引用
- **你说的**：Key 弱引用是为了 ThreadLocal 为 null 时能回收，Value 强引用会导致泄露。
- **得分**：12/20
- **哪里对了**：准确识别了 Key 是弱引用、Value 是强引用这一核心矛盾。
- **哪里错了 / 哪里不够**：
    - **深度缺失**：你没提到 `ThreadLocalMap` 的清理机制（启发式清理）。面试官想听的是：即便 Key 被回收了，如果该线程后续没有调用 `set/get/remove`，那个 Value 依然会一直占着坑。
    - **逻辑断层**：你没说清楚“谁”持有 Value 的强引用。正确表述是：`CurrentThread -> ThreadLocalMap -> Entry -> Value`。
- **你该补什么**：
    - 去查：`ThreadLocalMap.expungeStaleEntry` 方法的源码。
    - 去想：如果 Key 改成强引用，会发生什么？（我在 [ThreadLocalMasterTest.java](file:///d:/project/demo/demo-java/work/src/main/java/work/N1javabasic/day13/ThreadLocalMasterTest.java) 里已经给你写了，去读！）

### 面试题 2：线程池环境下的风险
- **你说的**：线程复用导致引用无法回收，需要 remove。
- **得分**：14/20
- **哪里对了**：抓住了“线程复用”这个核心痛点。
- **哪里错了 / 哪里不够**：
    - **漏了脏数据**：内存泄漏只是其一，更可怕的是**数据污染**。上一个用户的 UserID 留在了线程里，下一个用户进来直接拿到了别人的数据。这在生产环境是安全事故！
- **你该补什么**：
    - 去看：我在测试类里写的 `testThreadPoolLeak` 方法，亲手运行一下感受一下“数据污染”。

### 面试题 3：CompletableFuture 异步任务组合
- **你说的**：combined allOf 都可以完成。
- **得分**：5/20
- **哪里错了**：
    - **极度厌恶**：面试官问你“如何实现”，你回两个单词？你是在跟我玩猜谜吗？
    - **缺少差异对比**：`thenCombine` 是两个任务的结果合并，`allOf` 是等待所有任务完成。它们的应用场景完全不同。
    - **缺少链式编排**：完全没提 `thenApply`, `thenCompose`, `exceptionally` 等核心编排方法。
- **你该补什么**：
    - 去练：手写一个复杂的编排场景。比如：先根据 ID 查用户信息（异步），再根据用户信息查订单（异步且依赖前一步），同时查用户的积分（异步且不依赖订单），最后把订单和积分合并返回。

---

## 三、编程题：代码审判级点评

**你的代码**：[Test.java](file:///d:/project/demo/demo-java/work/src/main/java/work/N1javabasic/day13/Test.java)

#### 1. 正确性检查
- **ThreadLocal 部分**：
    - 第 22 行：`threadLocal.remove();` 写得不错，知道要手动清理。
    - **Bug 风险**：你的 `threadLocal` 定义在 `Test` 类里，虽然现在是单一线程，但在并发环境下，如果多个任务复用同一个线程，由于你没在 `finally` 块里 remove，万一业务逻辑抛异常了，这个 remove 就执行不到了！
- **CompletableFuture 部分**：
    - 第 33-34 行：定义了 `combined` 居然不去打印结果？你是在写草稿吗？
    - 第 38 行：`CompletableFuture.allOf(future3, future4);` 这一行没有任何意义，因为它不阻塞。你少写了一个 `.join()` 或 `.get()`。这说明你根本没懂 `allOf` 是干嘛的。

#### 2. 代码风格
- **半途而废**：代码写到一半就停了，注释写着 `CompletableFuture`，后面逻辑极其凌乱。
- **缺乏实战感**：既然要学 `ThreadLocal`，为什么不模拟一个 `UserContext` 拦截器的场景？

#### 3. 正确的写法（核心提示）
```java
// ThreadLocal 必须配合 try-finally
try {
    threadLocal.set("data");
    // business logic
} finally {
    threadLocal.remove(); // 哪怕报错也要清理，这是底线！
}

// CompletableFuture 组合示例
CompletableFuture.supplyAsync(() -> "Task 1")
    .thenCombine(CompletableFuture.supplyAsync(() -> "Task 2"), (r1, r2) -> r1 + " & " + r2)
    .thenAccept(System.out::println)
    .join(); // 阻塞等待完成
```

---

**总结**：
ThreadLocal 部分通过我的 [ThreadLocalMasterTest.java](file:///d:/project/demo/demo-java/work/src/main/java/work/N1javabasic/day13/ThreadLocalMasterTest.java) 应该能补回来。但 **CompletableFuture** 你必须重新去写！明天我会专门考你这个。
