import time
import random

n = 16  # 2的幂
hashes = [random.randint(0, 10**9) for _ in range(10**8)]

# 模运算
start = time.perf_counter()
for h in hashes:
    _ = h % n
mod_time = time.perf_counter() - start

# 位运算
start = time.perf_counter()
for h in hashes:
    _ = h & (n - 1)
and_time = time.perf_counter() - start

print(f"模运算时间: {mod_time:.4f}s")
print(f"位运算时间: {and_time:.4f}s")
print(f"加速比: {mod_time / and_time:.2f}x")