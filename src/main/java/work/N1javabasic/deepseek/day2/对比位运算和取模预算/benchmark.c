#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <stdint.h>

#define ITER 1000000000  // 10亿次迭代

// 测试1：模运算（非2的幂）
void test_mod_non_power_of_two() {
    volatile uint32_t res = 0;
    srand(42);

    clock_t start = clock();
    for (size_t i = 0; i < ITER; i++) {
        uint32_t val = rand();
        res ^= val % 100;  // 100不是2的幂，无法优化
    }
    double time = (double)(clock() - start) / CLOCKS_PER_SEC;
    printf("模运算(%%100)时间: %.4f 秒\n", time);

    // 防止优化
    if (res == 0) printf("");
}

// 测试2：位运算（相当于%128）
void test_and_power_of_two() {
    volatile uint32_t res = 0;
    srand(42);

    clock_t start = clock();
    for (size_t i = 0; i < ITER; i++) {
        uint32_t val = rand();
        res ^= val & 127;  // 相当于 % 128
    }
    double time = (double)(clock() - start) / CLOCKS_PER_SEC;
    printf("位运算(&127)时间: %.4f 秒\n", time);

    if (res == 0) printf("");
}

// 测试3：模拟哈希表的常见场景
#define TABLE_SIZE 1024  // 2^10

void test_hash_table_scenario() {
    volatile uint32_t res_mod = 0;
    volatile uint32_t res_and = 0;

    uint32_t* keys = malloc(TABLE_SIZE * sizeof(uint32_t));
    for (int i = 0; i < TABLE_SIZE; i++) {
        keys[i] = rand();
    }

    // 模运算测试
    clock_t start = clock();
    for (int j = 0; j < 100000; j++) {  // 10万次查找
        for (int i = 0; i < TABLE_SIZE; i++) {
            res_mod ^= keys[i] % TABLE_SIZE;
        }
    }
    double mod_time = (double)(clock() - start) / CLOCKS_PER_SEC;

    // 位运算测试
    start = clock();
    for (int j = 0; j < 100000; j++) {  // 10万次查找
        for (int i = 0; i < TABLE_SIZE; i++) {
            res_and ^= keys[i] & (TABLE_SIZE - 1);
        }
    }
    double and_time = (double)(clock() - start) / CLOCKS_PER_SEC;

    printf("\n哈希表场景（1024个槽位）:\n");
    printf("模运算时间: %.4f 秒\n", mod_time);
    printf("位运算时间: %.4f 秒\n", and_time);
    printf("加速比: %.2f 倍\n", mod_time / and_time);

    free(keys);
}

int main() {
    printf("=== 性能对比测试 ===\n");

    // 预热CPU
    for (int i = 0; i < 1000000; i++) {
        volatile int x = i * i;
    }

    test_mod_non_power_of_two();
    test_and_power_of_two();

    test_hash_table_scenario();

    return 0;
}