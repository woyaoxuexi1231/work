# Risk Data Hub Lab 剥洋葱式 Java 架构面试题

> 代码行号基于当前工作区快照。面试时不要让候选人泛泛讲“高并发”“分布式”“解耦”，要逼他回到这个项目的类、方法、SQL、线程状态和故障路径。

## 题 1: 这个同步引擎为什么要搞成“三层调度 + 业务内双线程”？你把线程模型、背压、退出和异常传播完整讲一遍

主问题：

这个项目从 `/api-hub-sync` 提交同步任务，到四类业务并发拉取、转换、落库，整体线程模型是怎么跑起来的？为什么不是一个大循环从上游查一页、转换一页、写一页就完事？请结合线程池配置、`SyncOrchestrator` 和模板基类，把吞吐、内存、失败传播讲清楚。

### 追问 1: 你刚才提到了三层调度，具体在这个项目里是怎么落地的？线程数峰值是多少？

完美答案：

入口在 `SyncController.sync()`，接口是 `POST /api-hub-sync`，代码把请求交给 `SyncTaskService.startTask()`，见 `src/main/java/com/riskdatahub/sync/SyncController.java:41` 和 `src/main/java/com/riskdatahub/task/SyncTaskService.java:115`。任务不是同步执行，而是先进入 `QUEUED`，`SyncTaskService.scanAndExecute()` 每 3 秒扫描一次，见 `SyncTaskService.java:267` 到 `SyncTaskService.java:321`。

线程池分三层：`syncTaskExecutor` 是 1 个线程，保证同一时刻只有一个同步任务在跑，见 `src/main/java/com/riskdatahub/config/SyncThreadPoolConfig.java:36` 到 `SyncThreadPoolConfig.java:42`；`syncBusinessExecutor` 是 4 个线程，四类业务 STOCK、TRADE、POSITION、ASSET 并发，见 `SyncThreadPoolConfig.java:50` 到 `SyncThreadPoolConfig.java:56`；每个业务还有一个 pairExecutor，固定 2 个线程，拉取线程和落库线程分离，见 `SyncThreadPoolConfig.java:64` 到 `SyncThreadPoolConfig.java:110`。

真正派发四类业务在 `SyncOrchestrator.submitBusinessTemplates()`，它对 Spring 注入的 `List<BusinessSyncTemplate>` 做 `CompletableFuture.supplyAsync(..., syncBusinessExecutor)`，见 `src/main/java/com/riskdatahub/sync/SyncOrchestrator.java:158` 到 `SyncOrchestrator.java:162`。所以峰值不是一句“四线程”这么糊弄人，而是 1 个任务线程 + 4 个业务线程 + 4 组业务内部 2 线程，合计约 13 条同步相关线程。为什么这么搞：业务间隔离，业务内做 I/O pipeline。上游查询和下游写库都是阻塞 I/O，单循环会把网络等待、MySQL 执行等待串死，双线程能让上游和下游重叠工作。

坑是线程池队列不是无限大：三个池都用了 `LinkedBlockingQueue` 和 `AbortPolicy`，见 `SyncThreadPoolConfig.java:40`、`SyncThreadPoolConfig.java:54`、`SyncThreadPoolConfig.java:108` 和 `SyncThreadPoolConfig.java:42`、`SyncThreadPoolConfig.java:56`、`SyncThreadPoolConfig.java:110`。生产上如果把拒绝策略换成默认吞异常或者无限队列，任务会在内存里堆到 JVM 老年代，最后 Full GC 把系统拖死。

### 追问 2: 你说业务内是生产者-消费者，为什么这里用 `ArrayBlockingQueue(4)`？这个 4 是在解决什么问题？

完美答案：

队列模式在 `AbstractBusinessSyncTemplate.execute()` 里创建：`BlockingQueue<PageChunk<S>> queue = new ArrayBlockingQueue<>(4)`，见 `src/main/java/com/riskdatahub/sync/template/AbstractBusinessSyncTemplate.java:63` 到 `AbstractBusinessSyncTemplate.java:64`。拉取线程在 `runFetchThread()` 里查一页后 `queue.put(PageChunk.data(...))`，见 `AbstractBusinessSyncTemplate.java:99` 和 `AbstractBusinessSyncTemplate.java:121`；落库线程在 `runInsertThread()` 里 `queue.take()`，见 `AbstractBusinessSyncTemplate.java:143` 和 `AbstractBusinessSyncTemplate.java:149`。

这是典型有界队列背压：上游快、下游慢时，`put()` 在队列满时进入 WAITING/TIMED_WAITING 相关阻塞路径，不继续把 page 数据堆进堆内存。容量 4 的本质不是神奇数字，而是把“最多缓存 4 页数据”变成硬上限。如果 `pageSize=100000`，一页对象已经很重，4 页还能估算内存；如果用无界队列，上游 MySQL 查询很快、下游 hub 库写入变慢时，堆里会堆一堆 `List<S>`，JVM 不是业务背压器，最后只能用 GC 替你背锅。

底层上，`ArrayBlockingQueue` 内部用一把 `ReentrantLock` 和两个 Condition 管理 notFull/notEmpty。它的好处是容量固定、数组结构缓存局部性好；代价是生产和消费共用一把锁，高并发多生产者多消费者时锁竞争明显。但这个项目每个业务只有一个拉取线程和一个落库线程，锁竞争不是主要矛盾，内存上限才是。

如果业务页非常大，比如每页百万行，就应该考虑信号量交接模式或调小 pageSize，而不是盲目加队列容量。这里的取舍是吞吐和内存之间折中：队列版允许最多 4 页并行缓冲，吞吐好于严格交替，内存又不会无限增长。

### 追问 3: 收尾怎么做？如果拉取线程结束了，落库线程怎么知道别再等了？如果一边失败，会不会死锁？

完美答案：

正常结束靠哨兵对象 `PageChunk.end()`。`PageChunk.end()` 定义在 `src/main/java/com/riskdatahub/sync/model/SyncSupport.java:61` 到 `SyncSupport.java:62`，拉取线程 finally 中调用 `publishEnd(queue)`，最终 `queue.put(PageChunk.end())`，见 `AbstractBusinessSyncTemplate.java:134` 和 `AbstractBusinessSyncTemplate.java:189` 到 `AbstractBusinessSyncTemplate.java:191`。落库线程 `queue.take()` 后判断 `chunk.isEnd()` 就 break，见 `AbstractBusinessSyncTemplate.java:149` 到 `AbstractBusinessSyncTemplate.java:151`。

异常路径靠 `AtomicReference<RuntimeException> failure` 记录根因。`recordFailure()` 用 `compareAndSet(null, exception)` 保证第一个失败的线程留下根因，然后 `queue.clear()` 再 `queue.offer(PageChunk.end())`，见 `AbstractBusinessSyncTemplate.java:178` 到 `AbstractBusinessSyncTemplate.java:184`。这一步很关键：如果落库线程失败但拉取线程正阻塞在队列满的 `put()`，只记录异常不唤醒对端，另一个线程可能永远卡住，`fetchFuture.get()` 或 `insertFuture.get()` 就把业务线程池卡死。

这个设计还有一个不完美点：`recordFailure()` 用的是 `offer(PageChunk.end())`，不是阻塞 `put()`。它前面先 `queue.clear()`，通常能腾出空间，但如果极端并发下队列状态变化，`offer` 失败就可能没有哨兵。这里因为只有一生产一消费且 `clear()` 在同一方法里，概率很低，但生产代码里我更愿意显式处理中断和 offer 返回值。

方案取舍上，哨兵比 Thread.interrupt 更可控，因为 MyBatis/JDBC 阻塞调用对 interrupt 的响应不稳定；但哨兵只能唤醒阻塞在队列上的线程，不能取消正在执行的 SQL。生产系统要补超时、连接池 queryTimeout 和任务取消机制。

### 追问 4: README 提到了信号量模式，你告诉我这个项目到底有没有用？它和队列模式的本质差别是什么？

完美答案：

源码里四个业务模板 `StockBusinessSyncTemplate`、`TradeBusinessSyncTemplate`、`PositionBusinessSyncTemplate`、`AssetBusinessSyncTemplate` 都继承 `AbstractBusinessSyncTemplate`，也就是队列版，分别见 `src/main/java/com/riskdatahub/sync/template/StockBusinessSyncTemplate.java:47`、`TradeBusinessSyncTemplate.java:45`、`PositionBusinessSyncTemplate.java:42`、`AssetBusinessSyncTemplate.java:42`。`AbstractSemaphoreSyncTemplate` 当前是备用抽象骨架，没有具体业务继承它。候选人如果说“项目已经用信号量跑业务”，那就是没看代码。

信号量版的核心在 `AbstractSemaphoreSyncTemplate.execute()`：`fetchPermit = new Semaphore(1)`，`insertPermit = new Semaphore(0)`，共享页 `sharedPage = new ArrayList<>()`，见 `src/main/java/com/riskdatahub/sync/template/AbstractSemaphoreSyncTemplate.java:63` 到 `AbstractSemaphoreSyncTemplate.java:69`。拉取线程先 `fetchPermit.acquire()`，写入 `sharedPage` 后 `insertPermit.release()`，见 `AbstractSemaphoreSyncTemplate.java:118`、`AbstractSemaphoreSyncTemplate.java:140` 到 `AbstractSemaphoreSyncTemplate.java:147`；落库线程 `insertPermit.acquire()` 后复制并清空共享页，再 `fetchPermit.release()`，见 `AbstractSemaphoreSyncTemplate.java:177` 到 `AbstractSemaphoreSyncTemplate.java:186`。

差别不是“一个高级一个低级”。队列版是有界缓冲，允许最多 4 页积压，吞吐更好；信号量版是单页交接，内存更稳。信号量版里最后一页不足 pageSize 时要特别小心：代码在 `rows.size() < context.getPageSize()` 时设置 `noMoreData` 并额外 `insertPermit.release()`，见 `AbstractSemaphoreSyncTemplate.java:150` 到 `AbstractSemaphoreSyncTemplate.java:151`。少这一次 release，消费者处理完最后一页后下一轮会永远阻塞在 `insertPermit.acquire()`。

底层原理上，`Semaphore.release/acquire` 通过 AQS 建立 happens-before，释放许可前对 `sharedPage` 的写入对获取许可后的消费者可见。这个可见性不是靠 `ArrayList` 线程安全，而是靠严格的信号量交接协议。取舍是清楚的：内存敏感、单页很大，用信号量；追求吞吐、单页可控，用有界队列。

### 追问 5: 如果上游突然变慢、hub 库写入变慢、连接池满了，这套设计还能撑住吗？

完美答案：

上游慢时，队列会空，落库线程阻塞在 `queue.take()`，见 `AbstractBusinessSyncTemplate.java:149`。这不浪费 CPU，但吞吐下降。hub 库慢时，落库线程卡在 `saveBatch()`，拉取线程最多提前放 4 页，之后阻塞在 `queue.put()`，见 `AbstractBusinessSyncTemplate.java:121`。这就是背压能撑住内存的地方。

真正要命的是连接池。中台主库 `application.yml` 里 Hikari `maximum-pool-size: 8`，见 `src/main/resources/application.yml:10`；两个上游动态数据源默认 `max-pool-size: 8`，见 `application.yml:47` 和 `application.yml:55`。同步峰值下，四个业务同时查上游，同时写 hub，还会有进度事件、字典查询、Leaf 发号、outbox 写入抢 hub 连接。连接池满时，Hikari 会让线程等连接，超过 connectionTimeout 抛异常。异常会被模板捕捉并记录到 `failure`，最终 `SyncOrchestrator.executeTemplate()` 把业务标 FAILED，见 `SyncOrchestrator.java:168` 到 `SyncOrchestrator.java:179`。

坑是 `TradeBusinessSyncTemplate.transform()` 每行调用 `dictionaryService.translate()`，见 `TradeBusinessSyncTemplate.java:117` 到 `TradeBusinessSyncTemplate.java:123`，而 `DictionaryService.translate()` 每次都查 hub 库，见 `src/main/java/com/riskdatahub/dictionary/DictionaryService.java:92` 到 `DictionaryService.java:100`。如果 pageSize 很大，这会把 hub 连接池打穿。正确做法是按批次预加载字典到内存 Map，或者本地缓存加版本号失效，而不是每行查库。

所以这套设计能挡住“速度不匹配”，但挡不住“共享资源被内部 N+1 查询打爆”。生产上要看 Hikari active/idle/pending、MySQL 慢 SQL、JVM 堆占用和线程 dump，不能只看业务线程数。

## 题 2: 断点续传和强制刷新到底可靠吗？你把游标、幂等、原子性和崩溃恢复讲透

主问题：

这个项目没有用上游 `sync_flag` 来标记已同步，而是用目标清洗表里的 `MAX(source_row_id)` 做断点游标。你评价一下这个方案在正确性、性能、崩溃恢复、强制刷新下的可靠性。注意，不许只说“能断点续传”，要讲什么时候能，什么时候不能。

### 追问 1: 你刚才说游标来自目标表，代码在哪？为什么不读 `sync_business_record.last_row_id`？

完美答案：

游标生成在 `SyncTaskService.runTask()`。代码先创建 `Map<String, Long> initialCursors`，然后按业务类型分别查清洗表 `MAX(source_row_id)`：`cleanStockMapper.selectObjs(... select("MAX(source_row_id)"))` 在 `src/main/java/com/riskdatahub/task/SyncTaskService.java:386` 到 `SyncTaskService.java:388`，交易、持仓、资产分别在 `SyncTaskService.java:391` 到 `SyncTaskService.java:403`。最后把游标传给 `syncOrchestrator.syncByDataSource(...)`，见 `SyncTaskService.java:411` 到 `SyncTaskService.java:417`。

不用 `sync_business_record.last_row_id` 的理由很实际：任务表是控制面，清洗表才是事实面。`SyncOrchestrator.updateRecordStatus()` 成功时才写 pageCount、pulled、saved、lastRowId，见 `src/main/java/com/riskdatahub/sync/SyncOrchestrator.java:185` 到 `SyncOrchestrator.java:205`。如果业务已经落库成功但状态记录更新失败，读 `last_row_id` 会回退；如果状态先写了但落库失败，读它会跳过数据。目标表 `MAX(source_row_id)` 至少反映“已经真实落到 hub 的最大源行”。

底层上，这依赖上游表 `id BIGINT PRIMARY KEY` 严格递增，OMS 和 Broker 的 DDL 都是 `id BIGINT PRIMARY KEY`，见 `sql/upstream/oms-schema.sql:6`、`sql/upstream/broker-schema.sql:6` 等。MySQL InnoDB 主键是聚簇 B+ 树，`.gt(id, lastId).orderByAsc(id).limit pageSize` 这种游标分页走主键范围扫描，避免 offset 深分页越翻越慢。

坑是 `MAX(source_row_id)` 只保存一个高水位，不保存缺口。如果源系统允许补历史数据，插入一个 id 小于当前 max 的新行，这套方案永远扫不到。适用前提是源 ID 单调追加，不能倒灌。如果要支持倒灌，得改成更新时间窗口 + 去重表，或者 CDC/binlog。

### 追问 2: 这个游标分页在业务模板里怎么推进？怎么防止死循环？

完美答案：

以交易为例，`TradeBusinessSyncTemplate.fetchPage()` 按数据源类型走不同 Mapper，但共同点是 `.gt(id, lastId).orderByAsc(id).last("limit " + pageSize)`，见 `src/main/java/com/riskdatahub/sync/template/TradeBusinessSyncTemplate.java:79` 到 `TradeBusinessSyncTemplate.java:96`。股票模板同样如此，见 `src/main/java/com/riskdatahub/sync/template/StockBusinessSyncTemplate.java:78` 到 `StockBusinessSyncTemplate.java:96`。

游标推进在抽象模板里统一做。拉取线程拿最后一行的源 ID：`long nextCursor = sourceRowId(rows.get(rows.size() - 1))`，见 `AbstractBusinessSyncTemplate.java:111`。如果 `nextCursor <= cursor`，直接抛异常，见 `AbstractBusinessSyncTemplate.java:111` 到 `AbstractBusinessSyncTemplate.java:116`。这就是防死循环保护：一旦 Mapper 排序错了、sourceRowId 取错了、上游数据异常导致游标不增长，宁可失败，不要无限拉同一页。

为什么不用 offset：offset 在 InnoDB 里不是“跳过去”，它要扫描并丢弃前 N 行，N 越大越慢，还可能因为并发插入导致翻页漂移。游标分页用主键范围条件，在 B+ 树上从 lastId 定位后顺序读，性能稳定。

坑是业务注释里说“sync_flag = 0”，但代码没查 `sync_flag`。比如 `TradeBusinessSyncTemplate.fetchPage()` 只按 id 大于 lastId 查询，没 `.eq(sync_flag, 0)`，见 `TradeBusinessSyncTemplate.java:84` 到 `TradeBusinessSyncTemplate.java:96`。候选人如果把 sync_flag 当真实机制讲，那就是被注释骗了。

### 追问 3: 幂等落库怎么保证？如果任务重跑，会不会重复插入？

完美答案：

目标表都有 `(source_system, source_row_id)` 唯一键：`clean_stock` 是 `uk_stock_source`，见 `sql/hub/hub-schema.sql:38`；`clean_trade` 是 `uk_trade_source`，见 `hub-schema.sql:56`；持仓和资产分别见 `hub-schema.sql:73` 和 `hub-schema.sql:89`。这是数据库层的最后防线。

应用层在每个模板的 `saveBatch()` 里先查 Redis/DB 中已有的 source_row_id。比如交易模板在 `TradeBusinessSyncTemplate.saveBatch()` 构造 cacheKey 后调用 `syncCacheHelper.getExistingIds(...)`，见 `TradeBusinessSyncTemplate.java:128` 到 `TradeBusinessSyncTemplate.java:132`；然后分 `toInsert` 和 `toUpdate`，插入在 `TradeBusinessSyncTemplate.java:138` 到 `TradeBusinessSyncTemplate.java:149`，更新在 `TradeBusinessSyncTemplate.java:150` 到 `TradeBusinessSyncTemplate.java:165`。股票、持仓、资产的结构一样，分别见 `StockBusinessSyncTemplate.java:127` 到 `StockBusinessSyncTemplate.java:164`、`PositionBusinessSyncTemplate.java:120` 到 `PositionBusinessSyncTemplate.java:157`、`AssetBusinessSyncTemplate.java:119` 到 `AssetBusinessSyncTemplate.java:156`。

为什么要应用层判重加数据库唯一键双保险：Redis/DB 预判可以减少唯一键冲突异常，唯一键保证并发或缓存失效时不会产生脏重复。Redis 只是优化，不是正确性来源。`SyncCacheHelper.getExistingIds()` 读 Redis 失败会降级 DB，见 `src/main/java/com/riskdatahub/sync/cache/SyncCacheHelper.java:30` 到 `SyncCacheHelper.java:45`；写 Redis 失败也只是打日志跳过，见 `SyncCacheHelper.java:47` 到 `SyncCacheHelper.java:58`。

坑要说狠一点：四个 Mapper 都只是继承 MyBatis-Plus 的 `BaseMapper`，见 `src/main/java/com/riskdatahub/sync/mapper/CleanTradeMapper.java:11` 等，而模板里调用了 `cleanTradeMapper.insert(toInsert)`、`cleanTradeMapper.updateById(toUpdate)`，见 `TradeBusinessSyncTemplate.java:149` 和 `TradeBusinessSyncTemplate.java:165`。标准 `BaseMapper` 通常是单实体 `insert(T)`、`updateById(T)`，不是 List 批量接口。除非项目里额外扩展了批量方法，否则这是非常可疑的点。生产上应改为 MyBatis-Plus `IService.saveBatch/updateBatchById`，或者写自定义批量 SQL，配合 JDBC URL 的 `rewriteBatchedStatements=true`，该配置在 `application.yml:5`、`application.yml:44`、`application.yml:52`。

### 追问 4: 强制刷新是不是原子的？如果清了一半服务挂了，下一次会怎么样？

完美答案：

强制刷新入口是 `SyncTaskService.forceRefresh()`，只把任务 message 置为 `"强制刷新-清除数据中"`，见 `SyncTaskService.java:130` 到 `SyncTaskService.java:135`。真正清数据在后台 `runTask()` 里先执行 `doForceCleanIfNeeded(id)`，见 `SyncTaskService.java:334` 到 `SyncTaskService.java:344`。

清理逻辑在 `doForceCleanIfNeeded()`：判断 task.message 是否以强制刷新开头，然后依次删除四张清洗表，再清 Redis `sync:existing:*`，见 `SyncTaskService.java:510` 到 `SyncTaskService.java:523`。最后把 message 改为 `"强制刷新-同步执行中"` 并设为 RUNNING，见 `SyncTaskService.java:525` 到 `SyncTaskService.java:528`。

这不是严格原子。虽然四个 delete 包在一次 `routingMybatisExecutor.run(HubConstants.DS_HUB, () -> { ... })` 里，但方法上没有 `@Transactional`，`RoutingMybatisExecutor` 只切 ThreadLocal 数据源，不开启事务，见 `src/main/java/com/riskdatahub/datasource/RoutingMybatisExecutor.java:39` 到 `RoutingMybatisExecutor.java:48`。如果删除了两张表服务挂了，可能留下半清状态。下一次如果 message 还没改成“同步执行中”，会再次清；如果 message 已经改了但同步没完成，就可能在半清状态上继续跑。

方案取舍上，演示项目选择了“后台清理 + 重跑”换响应速度。生产上要把强制刷新做成状态机：`CLEANING`、`CLEANED`、`SYNCING`，清理和同步分事务阶段，或者用影子表全量导入后原子 rename/swap。否则用户看到“强制刷新已提交”，其实数据库可能处于中间态。

### 追问 5: 崩溃恢复靠什么？Redisson 锁和 DB 状态谁说了算？

完美答案：

调度时 `doScanAndExecute()` 先拿 `RLock lock = redissonClient.getLock(LOCK_KEY)`，然后用 `lock.isLocked()` 判断锁是否存在，见 `SyncTaskService.java:275` 到 `SyncTaskService.java:277`。它查询 DB 里所有 RUNNING 任务，如果 DB 有 RUNNING 但 Redis 锁没了，就把任务标为 FAILED，并写 `Process crashed or watchdog expired`，见 `SyncTaskService.java:291` 到 `SyncTaskService.java:294`。

真正执行任务时 `runTask()` 用 `lock.tryLock(0, 30, TimeUnit.MINUTES)` 非阻塞拿锁，见 `SyncTaskService.java:334` 到 `SyncTaskService.java:338`；finally 里只在当前线程持有锁时 `unlock()`，见 `SyncTaskService.java:468`。这说明 Redis 锁是运行中互斥，DB 状态是可观测事实，两者要互相校验。

底层上，Redisson `tryLock(waitTime, leaseTime)` 这里给了 30 分钟 leaseTime，通常不会走 watchdog 自动续期语义。任务超过 30 分钟可能锁过期，调度线程看到 DB RUNNING 但锁状态可能变化，另一个任务有机会进来。这是生产坑。要么不传 leaseTime 依赖 watchdog，要么把 leaseTime 设置为严格大于任务最长时间并监控，或者任务拆小。

另一个坑是 `lock.isLocked()` 只能说明锁 key 当前存在，不说明是不是本实例、本任务持有。这里因为同一系统只允许一个同步任务，勉强够用；多租户、多数据源并行任务时，应该把 lock key 细化到 dataSourceKey 或 taskId，并在 DB 上做乐观状态流转，比如 `update ... where status='QUEUED'` 防并发抢占。

## 题 3: 进度事件为什么会“终态丢失”？跨线程计数为什么必须谈 JMM？

主问题：

这个项目把拉取数、落库数通过 Spring 事件写入 `sync_task` 和 `sync_business_record`。请你讲清楚进度事件从哪里发、谁接、为什么要节流、终态为什么不能被节流、跨线程计数为什么要考虑可见性。

### 追问 1: 进度事件从哪里发布，哪里消费？它是同步还是异步？

完美答案：

发布点在 `AbstractBaseSyncTemplate.publishProgress()`，它用 `eventPublisher.publishEvent(new SyncProgressEvent(...))`，见 `src/main/java/com/riskdatahub/sync/template/AbstractBaseSyncTemplate.java:71` 到 `AbstractBaseSyncTemplate.java:73`。队列模板在拉取一页后发布一次，见 `AbstractBusinessSyncTemplate.java:121` 到 `AbstractBusinessSyncTemplate.java:124`；落库一页后也发布一次，见 `AbstractBusinessSyncTemplate.java:162` 到 `AbstractBusinessSyncTemplate.java:165`。

消费点是 `SyncProgressEventListener.handleProgress()`，使用 `@EventListener`，见 `src/main/java/com/riskdatahub/task/SyncProgressEventListener.java:47`。它更新 `sync_task.message` 和 `sync_business_record` 的 pulled/saved，见 `SyncProgressEventListener.java:60` 到 `SyncProgressEventListener.java:72`。

这里要小心：项目里没有看到 `@Async` 或自定义 `ApplicationEventMulticaster`。所以默认 Spring 事件是同步调用，不是异步队列。注释说“异步消费”容易误导。同步事件的好处是简单、可见性顺着调用栈走；坏处是每次进度写库会拖慢拉取/落库线程，尤其 hub 库慢的时候。

生产取舍：如果进度写库不是强一致需求，应该放入异步事件线程池或内存队列，但要确保终态事件可靠落库。否则同步业务线程被进度 DB 写拖住，吞吐会被控制面拖垮。

### 追问 2: 为什么要 1 秒节流？终态为什么必须强制写？

完美答案：

节流状态存在 `ConcurrentHashMap<Long, Long> lastDbWriteTimes`，见 `SyncProgressEventListener.java:37`。处理事件时先算 `isFinal = event.getPulledCount() == event.getSavedCount()`，见 `SyncProgressEventListener.java:50`；如果不是终态并且距离上次写入不足 1 秒，直接 return，见 `SyncProgressEventListener.java:52` 到 `SyncProgressEventListener.java:53`；通过后更新写入时间，见 `SyncProgressEventListener.java:56`。

为什么节流：每页拉取、每页落库都会发事件。四个业务并发、pageSize 小时，事件会非常密集。如果每个事件都 update DB，hub 库会被控制面写放大打爆，连接池也会被占满。

为什么终态不能被节流：最后一页可能在 1 秒窗口内完成，如果终态事件被跳过，DB 里会长期显示“已拉取 > 已落库”，前端看起来像卡住。这个项目用 `pulledCount == savedCount` 作为终态条件绕过节流，见 `SyncProgressEventListener.java:50` 到 `SyncProgressEventListener.java:53`。这个修法朴素但有效。

坑是这个终态判断只适用于“无过滤、拉多少存多少”的同步。如果未来 transform 里过滤脏数据，或者 saveBatch 做去重导致 saved 小于 pulled，那么 `pulledCount == savedCount` 不再代表终态。更稳的方案是事件里带 `phase` 或 `finished` 标志，而不是用两个计数相等推断。

### 追问 3: `SyncCounter` 为什么要 `volatile`？只在各自线程自增，不加行不行？

完美答案：

`SyncCounter` 里 `pulledCount` 和 `savedCount` 都是 `volatile int`，见 `src/main/java/com/riskdatahub/sync/model/SyncSupport.java:101` 到 `SyncSupport.java:105`。拉取线程调用 `addPulledCount()`，落库线程调用 `incrementSavedCount()`，见 `SyncSupport.java:110` 到 `SyncSupport.java:112`。两个线程还会互相读取对方的值用于 `publishProgress()`，队列模板里拉取线程发布时读 saved，落库线程发布时读 pulled，见 `AbstractBusinessSyncTemplate.java:124` 和 `AbstractBusinessSyncTemplate.java:165`。

JMM 下，普通 int 的写入对另一个线程不保证及时可见。线程可能一直读到工作内存里的旧值，进度显示就会乱。`volatile` 保证写入释放、读取获取，建立可见性和禁止相关重排序。这里每个字段的复合自增只由一个线程写，所以不需要 `AtomicInteger` 的 CAS 来解决多写竞争。

坑是 `volatile` 不等于原子累加。`this.pulledCount += pulledCount` 不是原子操作，只是因为项目约束了只有拉取线程写 pulled，才成立。如果以后多个拉取线程并发拉分片，必须换 `LongAdder` 或 `AtomicInteger`，否则丢增量。

另一个细节：`pageCount` 和 `lastRowId` 没有 volatile，见 `SyncSupport.java:101` 到 `SyncSupport.java:107`。它们主要在结果汇总前通过 `Future.get()` 读取。`Future.get()` 对任务内写入有 happens-before，模板在 `fetchFuture.get()`、`insertFuture.get()` 后再返回结果，见 `AbstractBusinessSyncTemplate.java:77` 到 `AbstractBusinessSyncTemplate.java:78`，所以最终结果可见性没问题。

### 追问 4: `lastDbWriteTimes` 用 ConcurrentHashMap 就完全线程安全吗？有没有节流误伤？

完美答案：

`ConcurrentHashMap` 保证 map 结构线程安全，但这里的逻辑是 get 后判断再 put，见 `SyncProgressEventListener.java:52` 到 `SyncProgressEventListener.java:56`，不是原子节流。两个业务事件同一毫秒进来，可能都看到旧 lastWrite，然后都写 DB。因为它是降频优化，不是正确性机制，所以可以接受。

更隐蔽的问题是节流 key 只有 taskId，没有 businessCode。`lastDbWriteTimes` 的 key 是 Long taskId，见 `SyncProgressEventListener.java:37`。四类业务共享一个任务的 1 秒窗口，STOCK 刚写完，TRADE 的中间进度可能被节流。前端明细 `sync_business_record` 会出现某些业务进度更新不均匀。

方案取舍：如果只想降低主任务 message 写入频率，按 taskId 节流够用；如果要每个业务明细都顺滑，应该按 `taskId + businessCode` 节流，或者主任务节流、业务明细不节流但批量合并写。

生产坑是这个 map 没有清理。任务 ID 每天复用或新增很多时，`lastDbWriteTimes` 会增长。当前项目每天复用一条任务，风险不大；如果改成高频任务系统，要在任务完成后 remove，或者用带 TTL 的缓存。

### 追问 5: 如果某个业务失败，进度和最终状态会不会互相覆盖？

完美答案：

单业务状态由 `SyncOrchestrator.executeTemplate()` 控制。模板成功后调用 `updateRecordStatus(..., "SUCCESS", result)`，失败后调用 `updateRecordStatus(..., "FAILED", null)`，见 `src/main/java/com/riskdatahub/sync/SyncOrchestrator.java:168` 到 `SyncOrchestrator.java:179`。更新字段在 `updateRecordStatus()`，成功时写 pageCount、pulled、saved、lastRowId，失败时写 errorMessage，见 `SyncOrchestrator.java:185` 到 `SyncOrchestrator.java:205`。

总任务状态在 `SyncTaskService.runTask()` 的 try/catch 里更新。全部业务汇总成功后写 SUCCESS、progress=100、running=false，见 `SyncTaskService.java:431` 到 `SyncTaskService.java:442`；任何异常进入 catch 后写 FAILED，见 `SyncTaskService.java:454` 到 `SyncTaskService.java:459`。

会不会覆盖？进度事件仍可能在业务快完成时写 `sync_task.message`，因为 listener 只更新 message 和计数字段，见 `SyncProgressEventListener.java:60` 到 `SyncProgressEventListener.java:72`。总任务成功后又写 message 为“同步任务完成”，见 `SyncTaskService.java:431` 到 `SyncTaskService.java:442`。默认同步事件下顺序较可控；如果未来改异步事件，旧进度事件可能在成功状态之后到达，把 message 改回“正在同步”。这就是典型终态被旧事件覆盖。

生产方案是事件带版本或阶段，DB 更新加条件，比如只允许 RUNNING 状态接收进度事件，SUCCESS/FAILED 后拒绝旧进度写入。否则异步化以后，时序问题一定会冒出来。

## 题 4: 动态数据源路由和连接池隔离怎么保证？ThreadLocal、Hikari、下线排空都给我讲明白

主问题：

这个项目支持运行时注册多个上游数据源，并让 MyBatis 在不同数据源之间切换。请你说明它怎么路由、怎么创建和移除连接池、怎么防止 ThreadLocal 串库，以及连接池满或数据源下线时会发生什么。

### 追问 1: 具体怎么路由到不同数据源？为什么用显式 executor 而不是注解 AOP？

完美答案：

路由核心是 `DynamicRoutingDataSource` 继承 `AbstractRoutingDataSource`。当前线程的数据源 key 放在 `ThreadLocal<String> CONTEXT_HOLDER`，见 `src/main/java/com/riskdatahub/datasource/DynamicRoutingDataSource.java:30` 到 `DynamicRoutingDataSource.java:31`；MyBatis 获取连接时会调用 `determineCurrentLookupKey()` 返回当前 key，见 `DynamicRoutingDataSource.java:85` 到 `DynamicRoutingDataSource.java:86`。

业务层不直接碰 ThreadLocal，而是用 `RoutingMybatisExecutor.query(dataSourceKey, action)`。它先保存 previousKey，再 `setDataSourceKey(dataSourceKey)`，finally 里恢复 previousKey 或 clear，见 `src/main/java/com/riskdatahub/datasource/RoutingMybatisExecutor.java:39` 到 `RoutingMybatisExecutor.java:48`。这就是防串库关键：线程池线程会复用，如果不在 finally 清理 ThreadLocal，下一个任务可能带着上一次的上游 key 去查 hub，事故会很难看。

为什么不用注解 AOP：这个项目的数据源 key 是运行时上下文，业务模板里根据 `context.getDataSourceKey()` 和 datasourceType 路由，比如 `TradeBusinessSyncTemplate.fetchPage()` 里 `routingMybatisExecutor.query(context.getDataSourceKey(), ...)`，见 `TradeBusinessSyncTemplate.java:79` 到 `TradeBusinessSyncTemplate.java:96`。显式调用比 AOP 注解更直观，也避免 self-invocation 导致 AOP 失效。

代价是每个数据库操作都要包 executor，漏包就会走默认 hub 数据源。生产上可以用测试或拦截器检查上游 Mapper 是否在正确数据源上下文里执行。

### 追问 2: 注册新数据源时怎么验证和隔离？Hikari 参数从哪来？

完美答案：

运行时注册入口是 `DataSourceController.register()`，先查 `dataSourceManager.exists(config.getKey())`，再调用 `dataSourceManager.register(config)`，见 `src/main/java/com/riskdatahub/datasource/DataSourceController.java:62` 到 `DataSourceController.java:68`。

`DataSourceManager.register()` 是 synchronized，先检查 key 是否存在，然后 `createHikariDataSource(config)` 创建独立 Hikari 连接池，见 `src/main/java/com/riskdatahub/datasource/DataSourceManager.java:81` 到 `DataSourceManager.java:87`。它通过 `ds.getConnection().close()` 测试连接，失败就 `ds.close()` 并抛异常，见 `DataSourceManager.java:90` 到 `DataSourceManager.java:93`。成功后放入 `dataSources`、`dataSourceConfigs`，再刷新路由表，见 `DataSourceManager.java:98` 到 `DataSourceManager.java:100`。

配置来源有两处：启动时 `DataSourceAutoRegistrar.autoRegister()` 从 `hub.datasource.items` 自动注册，见 `src/main/java/com/riskdatahub/config/DataSourceAutoRegistrar.java:34` 到 `DataSourceAutoRegistrar.java:57`；运行时 API 传入 `DataSourceConfigDTO`，默认 maxPoolSize=10、minIdle=2，见 `src/main/java/com/riskdatahub/datasource/dto/DataSourceConfigDTO.java`。yml 里两个上游都配置 max-pool-size 8，见 `src/main/resources/application.yml:47` 和 `application.yml:55`。

隔离来自“每个数据源一个 HikariDataSource”。一个上游库慢，只会耗尽自己的 Hikari 池；但 hub 库是所有任务共享的，进度、Leaf、字典、清洗落库都会抢 hub 池，所以 hub 才是更容易被打穿的点。

### 追问 3: 删除数据源怎么做到优雅？正在跑的 SQL 会怎样？

完美答案：

删除入口是 `DataSourceController.remove()`，见 `DataSourceController.java:81` 到 `DataSourceController.java:87`。`DataSourceManager.remove()` 同样 synchronized，先从 `dataSources` 和 `dataSourceConfigs` 移除，再调用 `routingDataSource.remove(...)` 刷新路由，见 `DataSourceManager.java:111` 到 `DataSourceManager.java:120`。这一步让新请求不再路由到该数据源。

然后它轮询 Hikari 的 active connections，最多等 `DRAIN_TIMEOUT_MS = 30_000` 毫秒，见 `DataSourceManager.java:48` 和 `DataSourceManager.java:125` 到 `DataSourceManager.java:133`。最后如果还有 active 连接，会打 warn 并 `ds.close()`，见 `DataSourceManager.java:141` 到 `DataSourceManager.java:145`。active 连接数来自 Hikari MXBean，见 `DataSourceManager.java:258` 到 `DataSourceManager.java:261`。

优雅下线的真实语义是：不接新流量，尽量等旧 SQL 自然结束，到时强关连接池。它不能保证业务层事务完整，也不能取消已经发出去的 SQL。生产上还需要“数据源正在同步中禁止删除”或“下线前 drain 业务任务”，否则同步任务可能中途失败。

底层连接池原理是连接借出后不在 idle 池里，Hikari close 会关闭池并影响后续 borrow；已借出的连接可能在归还时被关闭。对业务来说就是 SQL 异常。这里异常会沿模板 failure 传播，最终任务 FAILED。

### 追问 4: `DynamicRoutingDataSource` 注释说读写锁保护查询和注册，代码真做到了吗？

完美答案：

注释说使用 `ReadWriteLock` 保护路由表切换，见 `DynamicRoutingDataSource.java:18` 到 `DynamicRoutingDataSource.java:20`。代码里确实有 `ReadWriteLock lock`，见 `DynamicRoutingDataSource.java:34`，但只在 `register()` 和 `remove()` 里拿 `writeLock()`，见 `DynamicRoutingDataSource.java:106` 到 `DynamicRoutingDataSource.java:130`。`determineCurrentLookupKey()` 没有拿读锁，见 `DynamicRoutingDataSource.java:85` 到 `DynamicRoutingDataSource.java:86`。

这不一定立刻错，因为 `AbstractRoutingDataSource.afterPropertiesSet()` 会解析目标数据源，内部引用替换通常是可见的；而注册/删除由 synchronized 的 `DataSourceManager` 串行化。但注释和实现不一致，面试时要指出来。别把注释当事实。

生产风险在热更新路由表时的内存可见性和瞬时路由缺失。如果刚删除某 key，旧线程 ThreadLocal 还拿着这个 key，`determineCurrentLookupKey()` 返回它，但路由表已经没有对应 DataSource，就可能 fallback 或抛异常，取决于 `AbstractRoutingDataSource` 配置。这里没有设置 lenientFallback，默认行为需要明确验证。

取舍上，我更倾向于不宣称“读写锁保护查询”，要么真的在 `determineTargetDataSource` 周围加读锁，要么删掉误导注释。工程里注释骗过你一次，线上就会让你加班一次。

### 追问 5: 如果连接池满了，这套系统是隔离失败还是级联失败？

完美答案：

上游数据源每个 key 一个 Hikari 池，所以 trade_oms 满了，不会直接耗尽 trade_broker 的连接池。`DataSourceManager` 用 `ConcurrentHashMap<String, HikariDataSource>` 保存数据源池，见 `DataSourceManager.java:67`。这是故障隔离的基础。

但 hub 库是默认数据源，所有清洗表写入、进度写入、Leaf 发号、字典查询、outbox 都打它。`application.yml` 里 hub `maximum-pool-size: 8`，见 `application.yml:10`。四个业务并发落库时，如果交易模板每行还查字典，`DictionaryService.translate()` 每次 `selectOne`，见 `DictionaryService.java:92` 到 `DictionaryService.java:100`，hub 池很容易成为全局瓶颈。

连接池满时线程会等连接，超过 Hikari connectionTimeout 抛异常。同步模板会捕获异常并 `recordFailure()`，队列版见 `AbstractBusinessSyncTemplate.java:130` 到 `AbstractBusinessSyncTemplate.java:184`。业务失败后 `SyncOrchestrator.executeTemplate()` 标记对应业务 FAILED，见 `SyncOrchestrator.java:168` 到 `SyncOrchestrator.java:179`。

生产方案是拆隔离面：hub 写库池、控制面进度池、字典缓存、Leaf 发号池或者至少把字典预加载，不要所有东西共用 8 个连接。否则一个看似无害的进度刷新或字典查询，会把主链路拖死。

## 题 5: Leaf 号段发号器为什么要双缓冲？它如何保证多线程、多实例不重复？

主问题：

这个项目自己实现了一个简化版 Leaf Segment 发号器。请你讲清楚本地内存发号、DB 号段推进、双缓冲预加载、水位线、并发安全和失败场景。别只说“高性能 ID”，我要听到锁粒度和事务边界。

### 追问 1: 单 JVM 内怎么保证同一个 tag 不重复？不同 tag 会不会互相阻塞？

完美答案：

`LeafSegmentService` 用 `ConcurrentHashMap<String, SegmentBuffer> buffers` 做每个 tag 一个 buffer，见 `src/main/java/com/riskdatahub/id/LeafSegmentService.java:44` 到 `LeafSegmentService.java:45`。`nextId(tag)` 里 `computeIfAbsent` 拿 buffer，然后 `synchronized (buffer)`，见 `LeafSegmentService.java:62` 到 `LeafSegmentService.java:66`。发号就是 `long id = buffer.current.nextId++`，见 `LeafSegmentService.java:72`。

锁粒度是 tag 级别，不是整个 service。`clean_trade` 和 `clean_stock` 使用不同 SegmentBuffer，不会互相阻塞。这个比全局 synchronized 好得多，因为清洗四张表会频繁调用 `leafSegmentService.nextId("clean_xxx")`，见例如 `TradeBusinessSyncTemplate.cleanRecordContext()` 的 `leafSegmentService.nextId("clean_trade")`，`src/main/java/com/riskdatahub/sync/template/TradeBusinessSyncTemplate.java:194`。

底层上，`synchronized` 既保证互斥，也保证进入/退出监视器的 happens-before。`Segment.nextId` 不是 AtomicLong，但被同一个 buffer monitor 保护，所以同 tag 不会并发读到同一个 nextId。

坑是这个锁包住了 `switchSegment()`，如果当前段耗尽且 next 没准备好，就会在 synchronized 里回源 DB，见 `LeafSegmentService.java:69` 到 `LeafSegmentService.java:74` 和 `LeafSegmentService.java:137` 到 `LeafSegmentService.java:148`。这时同 tag 所有发号线程都会等。双缓冲就是为减少这个等待。

### 追问 2: 多实例部署时怎么保证号段不重叠？

完美答案：

DB 表是 `leaf_alloc`，`biz_tag` 是主键，记录 `max_id` 和 `step`，见 `sql/hub/hub-schema.sql:15` 到 `hub-schema.sql:18`。申请新号段在 `fetchSegment(tag)`，它用 `TransactionTemplate` 开事务，见 `LeafSegmentService.java:189` 到 `LeafSegmentService.java:190`，然后执行 `leafAllocMapper.selectForUpdate(tag)`，见 `LeafSegmentService.java:195`。

SQL 明确是 `select ... from leaf_alloc where biz_tag = #{bizTag} for update`，见 `src/main/java/com/riskdatahub/id/mapper/LeafAllocMapper.java:21`。InnoDB 会对对应主键记录加排他锁，同一 tag 的多个实例申请号段会串行。拿到 oldMax 后计算 `newMax = oldMax + step`，更新 DB 的 max_id，然后返回 `[oldMax + 1, newMax]`，见 `LeafSegmentService.java:199` 到 `LeafSegmentService.java:208`。

这就是“DB 分配号段，本地消耗号段”。多实例不重复靠 DB 悲观锁和事务提交，不靠 Redis、不靠系统时钟。相比雪花算法，它没有时钟回拨问题；相比 UUID，它是递增 bigint，对 InnoDB 聚簇索引友好，不会像随机 UUID 那样频繁页分裂。

代价是 DB 是号段申请的中心点。如果 step 太小，高并发下频繁 `select for update` 会把 leaf_alloc 行打成热点。项目 seed 里清洗表 step 是 20，见 `sql/hub/hub-seed.sql:19` 到 `hub-seed.sql:23`，演示够用，生产明显太小。

### 追问 3: 双缓冲和 20% 水位线怎么工作？为什么不每次耗尽再申请？

完美答案：

每次发号后都会 `triggerPreloadIfNeeded(tag, buffer)`，见 `LeafSegmentService.java:72` 到 `LeafSegmentService.java:74`。它计算 remaining 和 total，当剩余比例小于等于 20% 时触发预加载，见 `LeafSegmentService.java:160` 到 `LeafSegmentService.java:163`。如果不满足低水位、已经加载中、或者 next 已经 ready，就直接返回，见 `LeafSegmentService.java:165`。

触发后设置 `buffer.loadingNext = true`，提交到单线程 `preloadExecutor` 异步执行 `fetchSegment(tag)`，然后 synchronized 写入 `buffer.next` 并把 loadingNext=false，见 `LeafSegmentService.java:169` 到 `LeafSegmentService.java:175`。

耗尽切换在 `switchSegment()`。如果 `buffer.next.ready`，直接 `buffer.current = buffer.next.copy()`，然后清空 next，见 `LeafSegmentService.java:137` 到 `LeafSegmentService.java:142`。这是纯内存指针切换，不等 DB。如果 next 没准备好，才同步 `fetchSegment(tag)`，见 `LeafSegmentService.java:147`。

为什么不耗尽再申请：耗尽时所有同 tag 发号线程都在 synchronized 里等 DB，P99 延迟会尖刺。20% 水位线让申请下一段的网络和 DB 锁等待隐藏在当前段消耗过程中。代价是预加载的号段如果进程崩溃，会浪费但不重复。ID 生成系统宁可有空洞，不能重复。

### 追问 4: 这里有什么隐藏 bug 或生产坑？

完美答案：

第一坑：预加载任务没有 try/finally。如果 `fetchSegment(tag)` 抛异常，`buffer.loadingNext` 不会被置回 false。代码在 `preloadExecutor.submit(() -> { Segment next = fetchSegment(tag); synchronized(buffer) { ... loadingNext=false; } })`，见 `LeafSegmentService.java:171` 到 `LeafSegmentService.java:175`。一旦 DB 短暂不可用，loadingNext 可能永远 true，后续不会再预加载，只能等 current 耗尽后同步申请。

第二坑：`preloadExecutor` 是全局单线程，见 `LeafSegmentService.java:52`。如果很多 tag 同时低水位，预加载串行，某些 tag 可能来不及准备 next。生产上可以按 tag 分片线程池，或者把 step 调大。

第三坑：`fetchSegment()` 在 `routingMybatisExecutor.query(HubConstants.DS_HUB, () -> transactionTemplate.execute(...))` 里切数据源，见 `LeafSegmentService.java:189` 到 `LeafSegmentService.java:208`。事务管理器绑定的是当前线程连接，数据源路由必须在事务取连接前设置。这里顺序是先 set ThreadLocal 再 execute，方向是对的；如果反过来，事务先拿了默认连接，再切 ThreadLocal 就晚了。

第四坑：step 太小。seed 里 `clean_trade` step=20，见 `hub-seed.sql:20`。同步一页 10000 行时要申请 500 次号段，`leaf_alloc` 行锁会非常热。生产 step 应按峰值 QPS 和容忍浪费量调大，比如几千到几十万。

### 追问 5: 为什么不用数据库自增、UUID 或雪花算法？

完美答案：

数据库自增最简单，但这个项目有多张清洗表、多数据源同步，还要在 transform 阶段先构造 `CleanRecordContext`，比如交易模板先 `leafSegmentService.nextId("clean_trade")` 再 `CleanTrade.create(...)`，见 `TradeBusinessSyncTemplate.java:117` 到 `TradeBusinessSyncTemplate.java:123` 和 `TradeBusinessSyncTemplate.java:194`。如果靠 DB 自增，批量写之前拿不到 global_id，不利于构造事件和关联。

UUID 不依赖中心，但随机、长、对 B+ 树不友好。清洗表主键是 `global_id BIGINT PRIMARY KEY`，例如 `clean_trade` 见 `sql/hub/hub-schema.sql:41` 到 `hub-schema.sql:56`。有序 bigint 插入 InnoDB 聚簇索引，大部分是右侧追加；随机 UUID 会导致页分裂、缓存命中下降、索引膨胀。

雪花算法吞吐高，但依赖机器号和时钟。金融数据同步系统里，容器漂移、时钟回拨、机器号重复都是坑。Leaf 号段把唯一性收敛到 DB 行锁，牺牲少量中心化依赖，换来简单可验证。

最终取舍：这个项目是 ETL 中台，不是每秒千万级发号服务。Leaf Segment 足够快、ID 有序、全局唯一、实现可控。真正生产化要补监控：当前段剩余、nextReady、loadingNext、fetchSegment 延迟和失败率。`state(tag)` 已经暴露 current/next/loading 字段，见 `LeafSegmentService.java:107` 到 `LeafSegmentService.java:117`，但总览层读取了不存在的 step/mode/description，见 `src/main/java/com/riskdatahub/overview/OverviewInfoSupplier.java:61` 到 `OverviewInfoSupplier.java:67`，这里还要修。

## 题 6: 清洗模板、缓存判重和消息 outbox 怎么组合？哪些地方是真可靠，哪些只是看起来可靠？

主问题：

四类业务模板用统一抽象类做 ETL，Redis 缓存做判重，完成后还写 outbox 和发 RabbitMQ。请你从模板方法、策略模式、幂等、缓存降级、消息可靠性这几个角度，把真实可靠边界讲清楚。

### 追问 1: 模板方法和策略模式分别落在哪？新增一种业务要改哪里？

完美答案：

策略模式落在 `BusinessSyncTemplate` 接口和 Spring 注入的 `List<BusinessSyncTemplate>`。接口定义 `businessCode()` 和 `execute(context)`，见 `src/main/java/com/riskdatahub/sync/template/BusinessSyncTemplate.java`；`SyncOrchestrator` 持有 `businessSyncTemplates`，见 `SyncOrchestrator.java:62`，并在 `submitBusinessTemplates()` 里遍历提交，见 `SyncOrchestrator.java:158` 到 `SyncOrchestrator.java:162`。

模板方法落在 `AbstractBusinessSyncTemplate.execute()`，它是 `final`，固定了队列、双线程、等待 Future、检查 failure、发布业务完成事件、返回结果的流程，见 `AbstractBusinessSyncTemplate.java:63` 到 `AbstractBusinessSyncTemplate.java:92`。子类只实现 `fetchPage()`、`sourceRowId()`、`transform()`、`saveBatch()` 这些变化点，抽象方法在 `AbstractBaseSyncTemplate.java`。

新增业务时，理论上只要新增一个 `@Service` 实现 `BusinessSyncTemplate` 或继承抽象模板，Spring 会自动注入到 `SyncOrchestrator`。但是现实没这么轻松：`SyncTaskService.runTask()` 里按 businessCode 硬编码查询 `MAX(source_row_id)` 的 switch，见 `SyncTaskService.java:380` 到 `SyncTaskService.java:403`；强制刷新也硬编码删除四张表，见 `SyncTaskService.java:519` 到 `SyncTaskService.java:523`。所以策略模式只覆盖执行层，没有完全消灭控制面的分支。

生产取舍：当前项目为了可读性保留硬编码，适合实验室。生产上应该让模板暴露 target table、cursor supplier、clean action，把断点和清理能力也下沉到策略里，否则新增业务会漏改。

### 追问 2: Redis 判重缓存靠不靠谱？Redis 挂了会不会丢数据？

完美答案：

Redis 只是性能优化，不是正确性来源。`SyncCacheHelper.getExistingIds()` 先读 Redisson `RSet`，存在就 `readAll()`，见 `src/main/java/com/riskdatahub/sync/cache/SyncCacheHelper.java:30` 到 `SyncCacheHelper.java:34`；读失败 catch 后降级执行 `dbLoader.get()`，见 `SyncCacheHelper.java:36` 到 `SyncCacheHelper.java:40`；DB 加载后尝试写回 Redis 并设置 1 小时 TTL，见 `SyncCacheHelper.java:43` 到 `SyncCacheHelper.java:45`。

插入新数据后，模板会 `syncCacheHelper.addNewIds(...)` 把新 source_row_id 加到缓存，例如交易模板见 `TradeBusinessSyncTemplate.java:149` 到 `TradeBusinessSyncTemplate.java:152`。写 Redis 失败只打日志，不影响主流程，见 `SyncCacheHelper.java:53` 到 `SyncCacheHelper.java:58`。

可靠性来自数据库唯一键 `(source_system, source_row_id)`，不是 Redis。唯一键见 `sql/hub/hub-schema.sql:38`、`hub-schema.sql:56`、`hub-schema.sql:73`、`hub-schema.sql:89`。Redis 挂了最多多查 DB，或者因为缓存不准多走 update/insert 判断，但不能让重复数据落库。

坑是缓存粒度是整张表某来源的全部 source_row_id，一旦数据量千万级，`readAll()` 会把一个大集合拉到 JVM 内存。生产上应该按分片、布隆过滤器、或者直接依赖数据库 `insert on duplicate key update`，别把 Redis Set 当无限内存索引。

### 追问 3: 交易状态字典翻译有什么性能问题？你会怎么改？

完美答案：

交易模板在 transform 阶段调用 `resolveTradeStatus()`，见 `TradeBusinessSyncTemplate.java:117` 到 `TradeBusinessSyncTemplate.java:123`；`resolveTradeStatus()` 内部调用 `dictionaryService.translate(...)`，见 `TradeBusinessSyncTemplate.java:182` 到 `TradeBusinessSyncTemplate.java:186`。`DictionaryService.translate()` 每次都查 hub 库 `dict_item`，见 `src/main/java/com/riskdatahub/dictionary/DictionaryService.java:92` 到 `DictionaryService.java:100`。`dict_item` 有唯一键 `(dict_type, dict_code)`，见 `sql/hub/hub-schema.sql:12`，单次查询是快的，但每行一次就是 N+1。

如果 pageSize=10000，每页交易就可能打 10000 次字典查询。四个业务并发时，hub 连接池会被字典查询抢走，落库反而等连接。这个坑生产很常见：单条 SQL 都很快，但调用次数把系统打死。

改法：在 `TradeBusinessSyncTemplate.execute()` 入口按 datasourceType 预加载 `trade_status_oms` 或 `trade_status_broker` 到 Map，transform 只做内存查；或者 `DictionaryService` 做本地缓存，按更新时间或管理端变更主动失效。字典这种低基数数据，不该在每行转换时访问数据库。

取舍上，本地缓存要处理更新一致性；但交易状态字典不是强实时配置，允许秒级或分钟级失效。吞吐收益远大于一致性成本。

### 追问 4: Outbox 和 RabbitMQ 在这个项目里各自是什么语义？RabbitMQ 失败会不会让任务失败？

完美答案：

业务级 outbox 在模板完成后发布。队列模板成功后调用 `publishBusinessSummaryEvent(context, counter)`，见 `AbstractBusinessSyncTemplate.java:83`；具体方法在 `AbstractBaseSyncTemplate.publishBusinessSummaryEvent()`，它构造 payload 后调用 `messageOutboxService.publish(...)`，见 `AbstractBaseSyncTemplate.java:80` 到 `AbstractBaseSyncTemplate.java:90`。

`MessageOutboxService.publish()` 用 Leaf 生成 `event_message` ID，设置 topic、bizKey、payload、status=`NEW`，然后插入 `event_message` 表，见 `src/main/java/com/riskdatahub/message/MessageOutboxService.java:43` 到 `MessageOutboxService.java:54`。表结构在 `sql/hub/hub-schema.sql:92` 到 `hub-schema.sql:97`。这是真正可恢复的事件记录。

整任务完成后，`SyncTaskService.runTask()` 还调用 `rabbitMqSender.sendSyncCompleted(...)`，见 `SyncTaskService.java:448` 到 `SyncTaskService.java:451`。`RabbitMqSender` 直接 `rabbitTemplate.convertAndSend(...)`，失败只 catch 并打日志，见 `src/main/java/com/riskdatahub/message/RabbitMqSender.java:35` 到 `RabbitMqSender.java:51`。所以 RabbitMQ 通知失败不会让同步任务失败。

这两个语义不同：outbox 是可靠事件的落库端，但项目没有实现 outbox poller 投递；RabbitMQ 是即时通知，失败不回滚。生产上不能说“用了 outbox 所以消息可靠”，这里只做了一半。完整 outbox 还要有扫描 NEW、投递 MQ、确认后标 PROCESSED、失败重试、幂等消费。

### 追问 5: 动态 SQL 统计接口有没有 SQL 注入风险？为什么现在没炸？

完美答案：

`DynamicSqlMapper` 使用 `${}` 直接拼接 SQL：`countTable` 是 `select count(1) from ${tableName}`，见 `src/main/java/com/riskdatahub/mapper/DynamicSqlMapper.java:32` 到 `DynamicSqlMapper.java:33`；`maxValue` 也拼 `${columnName}` 和 `${tableName}`，见 `DynamicSqlMapper.java:42` 到 `DynamicSqlMapper.java:43`。`${}` 不是 `#{}`，不会走预编译参数绑定，传入恶意表名就有 SQL 注入风险。

为什么现在没炸：调用方 `PlatformInfoService.currentBusinessTableStats()` 和 `currentHubTableStats()` 传的是代码里写死的表名白名单，见 `src/main/java/com/riskdatahub/overview/PlatformInfoService.java:60` 到 `PlatformInfoService.java:83`；真正循环调用在 `countTables()`，见 `PlatformInfoService.java:91` 到 `PlatformInfoService.java:97`。只要这些表名不来自用户输入，风险可控。

坑是这个 Mapper 是公共能力，未来别人可能直接把前端传来的 tableName 喂进去。工程上应该把 `DynamicSqlMapper` 限制在 package-private 风格的内部组件，或者在 Service 层做严格白名单校验，甚至不用动态 SQL，改成枚举表名到固定 Mapper。

取舍：动态 SQL 对运维统计很方便，但它是锋利工具。面试里候选人如果只说“用了 MyBatis 所以防注入”，直接判出局。MyBatis 的 `#{}` 防值注入，`${}` 是文本替换。
