package work.N1javabasic.v1.all;

import java.util.*;

public class TopKProblem {
    
    // ✅ 找出最大的 K 个元素（小顶堆）
    public static List<Integer> findTopK(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);
        
        for (int num : nums) {
            if (minHeap.size() < k) {
                minHeap.offer(num);
            } else if (num > minHeap.peek()) {
                minHeap.poll();  // 移除最小的
                minHeap.offer(num);
            }
        }
        
        return new ArrayList<>(minHeap);
    }
    
    // ✅ 找出最小的 K 个元素（大顶堆）
    public static List<Integer> findSmallestK(int[] nums, int k) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        
        for (int num : nums) {
            if (maxHeap.size() < k) {
                maxHeap.offer(num);
            } else if (num < maxHeap.peek()) {
                maxHeap.poll();
                maxHeap.offer(num);
            }
        }
        
        return new ArrayList<>(maxHeap);
    }
    
    public static void main(String[] args) {
        int[] nums = {3, 2, 1, 5, 6, 4};
        
        System.out.println("最大的3个: " + findTopK(nums, 3));    // [4, 5, 6]
        System.out.println("最小的3个: " + findSmallestK(nums, 3)); // [1, 2, 3]
    }
}
