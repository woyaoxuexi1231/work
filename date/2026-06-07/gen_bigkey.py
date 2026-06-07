import redis
import time

r = redis.Redis(host='localhost', port=6379, password="123456")
KEY = "test:bigkey:hash"
COUNT = 10_000_000

print(f"🚀 Generating {COUNT} fields...")
start = time.time()

# 使用 pipeline 批量写入，速度更快
pipe = r.pipeline()
for i in range(COUNT):
    pipe.hset(KEY, f"field:{i}", f"value_{i}" * 10)
    if i % 10000 == 0 and i > 0:
        pipe.execute()
        print(f"  Progress: {i:,}")

pipe.execute()
print(f"✅ Done in {time.time()-start:.2f}s")
print(f"📏 Size: {r.memory_usage(KEY)} bytes")