
=====================================
2026-05-11 15:00:37 139835350828800 INNODB MONITOR OUTPUT
=====================================
Per second averages calculated from the last 13 seconds
-----------------
BACKGROUND THREAD
-----------------
srv_master_thread loops: 29192 srv_active, 0 srv_shutdown, 312416 srv_idle
srv_master_thread log flush and writes: 0
----------
SEMAPHORES
----------
OS WAIT ARRAY INFO: reservation count 9827
OS WAIT ARRAY INFO: signal count 9467
RW-shared spins 0, rounds 0, OS waits 0
RW-excl spins 0, rounds 0, OS waits 0
RW-sx spins 0, rounds 0, OS waits 0
Spin rounds per wait: 0.00 RW-shared, 0.00 RW-excl, 0.00 RW-sx
------------
TRANSACTIONS
------------
Trx id counter 2041494
Purge done for trx's n:o < 2041362 undo n:o < 0 state: running but idle
History list length 2
LIST OF TRANSACTIONS FOR EACH SESSION:
---TRANSACTION 421311892175224, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
---TRANSACTION 421311892171992, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
---TRANSACTION 421311892174416, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
---TRANSACTION 421311892173608, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
---TRANSACTION 421311892171184, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
---TRANSACTION 421311892170376, not started
0 lock struct(s), heap size 1128, 0 row lock(s)
--------
FILE I/O
--------
I/O thread 0 state: waiting for completed aio requests ((null))
I/O thread 1 state: waiting for completed aio requests (insert buffer thread)
I/O thread 2 state: waiting for completed aio requests (read thread)
I/O thread 3 state: waiting for completed aio requests (read thread)
I/O thread 4 state: waiting for completed aio requests (read thread)
I/O thread 5 state: waiting for completed aio requests (read thread)
I/O thread 6 state: waiting for completed aio requests (write thread)
I/O thread 7 state: waiting for completed aio requests (write thread)
I/O thread 8 state: waiting for completed aio requests (write thread)
Pending normal aio reads: [0, 0, 0, 0] , aio writes: [0, 0, 0, 0] ,
ibuf aio reads:
Pending flushes (fsync) log: 0; buffer pool: 0
4563 OS file reads, 4404808 OS file writes, 2439830 OS fsyncs
0.00 reads/s, 0 avg bytes/read, 0.00 writes/s, 0.00 fsyncs/s
-------------------------------------
INSERT BUFFER AND ADAPTIVE HASH INDEX
-------------------------------------
Ibuf: size 1, free list len 0, seg size 2, 15 merges
merged operations:
insert 25, delete mark 0, delete 0
discarded operations:
insert 0, delete mark 0, delete 0
Hash table size 34679, node heap has 2 buffer(s)
Hash table size 34679, node heap has 1476 buffer(s)
Hash table size 34679, node heap has 2 buffer(s)
Hash table size 34679, node heap has 4 buffer(s)
Hash table size 34679, node heap has 2 buffer(s)
Hash table size 34679, node heap has 78 buffer(s)
Hash table size 34679, node heap has 1 buffer(s)
Hash table size 34679, node heap has 1 buffer(s)
0.00 hash searches/s, 0.54 non-hash searches/s
---
LOG
---
Log sequence number          21830018572
Log buffer assigned up to    21830018572
Log buffer completed up to   21830018572
Log written up to            21830018572
Log flushed up to            21830018572
Added dirty pages up to      21830018572
Pages flushed up to          21830018572
Last checkpoint at           21830018572
Log minimum file id is       205
Log maximum file id is       216
4131733 log i/o's done, 0.00 log i/o's/second
----------------------
BUFFER POOL AND MEMORY
----------------------
Total large memory allocated 0
Dictionary memory allocated 936692
Buffer pool size   8192
Free buffers       1024
Database pages     5602
Old database pages 2047
Modified db pages  0
Pending reads      0
Pending writes: LRU 0, flush list 0, single page 0
Pages made young 228896, not young 117294
0.00 youngs/s, 0.00 non-youngs/s
Pages read 4467, created 19638, written 210739
0.00 reads/s, 0.00 creates/s, 0.00 writes/s
Buffer pool hit rate 1000 / 1000, young-making rate 0 / 1000 not 0 / 1000
Pages read ahead 0.00/s, evicted without access 0.00/s, Random read ahead 0.00/s
LRU len: 5602, unzip_LRU len: 0
I/O sum[11]:cur[0], unzip sum[0]:cur[0]
--------------
ROW OPERATIONS
--------------
0 queries inside InnoDB, 0 queries in queue
1 read views open inside InnoDB
Process ID=1, Main thread ID=139836431341312 , state=sleeping
Number of rows inserted 2003985, updated 490, deleted 212, read 12227388
0.00 inserts/s, 0.00 updates/s, 0.00 deletes/s, 0.54 reads/s
Number of system rows inserted 1131, updated 779, deleted 805, read 416856
0.00 inserts/s, 0.00 updates/s, 0.00 deletes/s, 0.00 reads/s
----------------------------
END OF INNODB MONITOR OUTPUT
============================
