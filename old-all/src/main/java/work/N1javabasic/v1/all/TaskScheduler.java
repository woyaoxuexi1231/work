package work.N1javabasic.v1.all;

import java.util.*;

class Task {
    String name;
    int priority;  // 数字越小优先级越高
    
    Task(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }
    
    @Override
    public String toString() {
        return name + "(优先级:" + priority + ")";
    }
}

public class TaskScheduler {
    
    public static void main(String[] args) {
        // 小顶堆：优先级数字小的先执行
        PriorityQueue<Task> taskQueue = new PriorityQueue<>(
            Comparator.comparingInt(t -> t.priority)
        );
        
        // 添加任务
        taskQueue.offer(new Task("邮件通知", 3));
        taskQueue.offer(new Task("系统备份", 1));
        taskQueue.offer(new Task("用户登录", 2));
        taskQueue.offer(new Task("数据同步", 1));
        taskQueue.offer(new Task("日志清理", 4));
        
        System.out.println("📋 按优先级执行任务：");
        while (!taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            System.out.println("  → 执行: " + task);
        }
    }
}
