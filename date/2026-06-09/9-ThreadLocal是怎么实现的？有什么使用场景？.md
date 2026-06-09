ThreadLocal 的实现核心可以概括为：**每个线程内部都持有一个专属的哈希表，表的键是 ThreadLocal 对象的弱引用，值就是线程独有的变量副本。** 通过 `set`/`get` 操作，实际就是在操作当前线程自己这张表。

下面我分实现原理、使用场景和注意事项三部分展开说。

---

### 一、ThreadLocal 实现原理

#### 1. 数据存储结构
- **入口在 Thread 类**  
  每个 `Thread` 对象都有一个 `threadLocals` 字段（类型 `ThreadLocal.ThreadLocalMap`），它是真正存放数据的地方。
- **ThreadLocalMap 是定制哈希表**  
  它不是 `java.util.Map`，而是用**开放地址法**解决冲突的数组，数组元素是 `Entry`。
- **Entry 的 key 是弱引用**  
  `Entry` 继承自 `WeakReference<ThreadLocal<?>>`，key 就是那个 `ThreadLocal` 实例的弱引用，value 是强引用保存的实际数据。

#### 2. set / get / remove 流程

**set(T value)**
1. 拿到当前线程 `Thread.currentThread()`。
2. 获取该线程的 `ThreadLocalMap`，如果没有就调用 `createMap()` 初始化。
3. 以**当前 ThreadLocal 对象的 hashcode** 定位数组槽位，用开放地址法解决冲突，找到合适位置后放入 `Entry`。

**get()**
1. 同样拿到当前线程，取它的 `ThreadLocalMap`。
2. 如果 map 为空，调用 `initialValue()` 获得初始值（通常是 null），然后创建 map 并将该值存入后返回。
3. 如果 map 存在，根据当前 ThreadLocal 的 hashcode 查找 Entry，找到则返回其 value；否则也走 `initialValue()` 流程。

**remove()**  
直接从当前线程的 map 中移除该 ThreadLocal 对应的 Entry，同时会做一定的**过期清除**（清理 key 为 null 的脏 Entry），防止内存泄漏。

#### 3. 弱引用与内存泄漏
- 如果 ThreadLocal 实例在外部的强引用断开，它只会被 Entry 的弱引用 key 所指，因此 **ThreadLocal 对象会被 GC 回收**。
- 但是 Entry 的 value 是强引用，只要线程还存活（比如线程池中的线程），`Thread → ThreadLocalMap → Entry → value` 这条引用链就一直存在，**value 无法被回收**，造成内存泄漏。
- 这也是官方文档建议**每次使用完 ThreadLocal 都调用 remove()** 的原因。在 `get/set/remove` 过程中，ThreadLocalMap 也会尝试清理 key 为 null 的过期 Entry，但这种“启发式”清理并不完全可靠。

---

### 二、使用场景

只要需要“**线程级别的隔离**”，并且不想显式传递参数时，都可以考虑 ThreadLocal。

1. **不安全的工具类副本**  
   经典例子是 `SimpleDateFormat`，它不是线程安全的。给每个线程分配一个独立的实例：
   ```java
   private static final ThreadLocal<SimpleDateFormat> dateFormat =
       ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
   ```
   避免同步开销，同时保证线程安全。

2. **数据库连接管理 / 事务管理**  
   Spring 的事务管理器会将数据库连接（Connection）绑定到当前线程的 ThreadLocal 中，保证同一个事务中所有 DAO 操作使用的是同一个连接，从而能提交或回滚。

3. **Web 请求上下文**  
   Spring 的 `RequestContextHolder` 利用 ThreadLocal 将当前请求的 `HttpServletRequest` 与线程绑定，使得在 Controller、Service、DAO 各层都能随时获取请求信息，而不用把 request 对象层层传递。

4. **链路追踪与日志上下文**  
   像 MDC（Mapped Diagnostic Context）存放 `traceId` / `userId` 等，就是基于 ThreadLocal，确保同一线程下所有日志都自动带上这些信息。

5. **避免参数穿透**  
   当一些通用数据（如租户 ID、用户 Session）需要在调用链最深层使用，又不适合加在方法参数上时，可以暂存到 ThreadLocal 中，用完清理。

---

### 三、必须注意的两个坑（面试常追问）

1. **内存泄漏**
    - **原因**：线程池复用线程，value 一直保持强引用。
    - **解决**：强制养成在 `finally` 中调用 `ThreadLocal.remove()` 的习惯。

2. **父子线程传递问题**
    - 普通 ThreadLocal 在主线程 set 的值，子线程拿不到。
    - 可以用 `InheritableThreadLocal` 在创建子线程时“拷贝”一次值，但它对线程池无效（线程是复用的，不会再触发拷贝）。
    - 更复杂的场景可使用阿里的 `TransmittableThreadLocal`，能解决线程池下的上下文传递。

---

**总结一句话**：  
ThreadLocal 通过把变量存进**线程私有的 ThreadLocalMap** 实现了线程隔离，主要用于保存线程不安全的工具实例、传递上下文等，但必须注意**用完即删**，避免内存泄漏。