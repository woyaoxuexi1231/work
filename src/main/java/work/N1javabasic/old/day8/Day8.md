#### 什么是指令重排序？`volatile` 是如何禁止重排序的？

指令重排序是jvm编译时提升性能，把运行结果与顺序无关的指令进行重新排序

被volatile修饰的变量，可以保证其上下指令的顺序，而不会被jvm重新排序



重新回答：

![img](https://i-blog.csdnimg.cn/blog_migrate/b9b9fd0fb4a97ccbc129458b17eb1b18.png)



指令重排序是指 jvm 编译时为了提升性能，对编译后的指令进行重新排序，只要保证最终的运行结果与单线程运行结果一直即可。

volatile引入内存屏障，

在volatile写之前加入StoreStore，volatile之前的所有写操作先于volatile发生

在volatile写之后加入StoreLoad，volatile之后的所有读操作能看到读之前的所有写操作的最新数据



在volatile读操作后加入 LoadLoad操作，确保所有volatile之后的读取操作晚于volatile读操作

在volatile读操作后加入LoadStore操作，确保后续所有的store操作都能够读取到最新的数据







#### `volatile` 能保证原子性吗？为什么 `count++` 不是原子操作？

vlolatile只能保证内存可见性，不能保证原子性。

被 vlolatile修饰的变量在修改或访问时都是直接与主内存进行交互

count++不是原子操作，编译后为 count  = count + 1



重新回答：

写在代码里了。



#### 谈谈 `Happens-Before` 的理解，为什么它比直接看源码更重要？



重新回答：

**程序次序规则**  在一个线程内部，按照书写代码，前面的操作 happens-before 后面的操作，即一个线程内部的读写操作，是按照代码的自然书写顺序来的，先写的变量值对于后续的读操作一定是可见的。这并不禁止指令重排（只要不改变单线程的执行结果），只是从语义上保证顺序。

**管程锁定规则** 对一个锁的释放操作一定 happens-before 对同一个锁的加锁操作。即线程T1释放锁之前的所有对共享变量的操作一定在T2拿到锁之后对于T2来说是可见的。

**volatile变量规则** 对一个volatile的写操作一定 happeds-before之后对该变量的读操作。写volatile变量会将该线程的本地内存的变量刷新到主内存，读volatile变量会从主内存中读取。并且会防止指定重排序，写之前的代码不能放到写之后，读之前的代码不能重排到读之后。





