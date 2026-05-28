package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import java.lang.management.*;
import java.util.List;

public class MonitorUtil {

    public static void printMemory(String tag) {

        Runtime rt = Runtime.getRuntime();

        long total = rt.totalMemory() / 1024 / 1024;
        long free = rt.freeMemory() / 1024 / 1024;
        long used = total - free;
        long max = rt.maxMemory() / 1024 / 1024;

        System.out.println("\n====================");
        System.out.println(tag);
        System.out.println("used memory : " + used + " MB");
        System.out.println("free memory : " + free + " MB");
        System.out.println("total memory: " + total + " MB");
        System.out.println("max memory  : " + max + " MB");

        List<GarbageCollectorMXBean> gcBeans =
                ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean bean : gcBeans) {
            System.out.println(
                    bean.getName()
                            + " count=" + bean.getCollectionCount()
                            + " time=" + bean.getCollectionTime() + "ms"
            );
        }

        System.out.println("====================\n");
    }
}