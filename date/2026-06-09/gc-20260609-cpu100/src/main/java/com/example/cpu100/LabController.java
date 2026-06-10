package com.example.cpu100;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/lab", produces = MediaType.APPLICATION_JSON_VALUE)
public class LabController {

    private final Map<String, SpinGroup> spinGroups = new ConcurrentHashMap<>();
    private final AtomicBoolean deadlockStarted = new AtomicBoolean(false);
    private final AtomicBoolean blockedStarted = new AtomicBoolean(false);
    private final AtomicLong blackhole = new AtomicLong(0);

    @GetMapping("/pid")
    public Map<String, Object> pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        String pid = name;
        int idx = name.indexOf('@');
        if (idx > 0) {
            pid = name.substring(0, idx);
        }
        return Collections.<String, Object>singletonMap("pid", pid);
    }

    @PostMapping("/cpu/spin")
    public Map<String, Object> cpuSpin(
            @RequestParam(name = "threads", defaultValue = "1") int threads,
            @RequestParam(name = "durationSeconds", defaultValue = "120") int durationSeconds,
            @RequestParam(name = "group", defaultValue = "default") String group
    ) {
        if (threads < 1) {
            threads = 1;
        }
        if (threads > 256) {
            threads = 256;
        }
        if (durationSeconds < 1) {
            durationSeconds = 1;
        }
        if (durationSeconds > 3600) {
            durationSeconds = 3600;
        }

        SpinGroup existing = spinGroups.get(group);
        if (existing != null && existing.running.get()) {
            return Collections.<String, Object>singletonMap("message", "group already running: " + group);
        }

        SpinGroup spinGroup = new SpinGroup(group, threads, durationSeconds, blackhole);
        spinGroups.put(group, spinGroup);
        spinGroup.start();

        return Collections.<String, Object>singletonMap("message",
                "started cpu spin, group=" + group + ", threads=" + threads + ", durationSeconds=" + durationSeconds);
    }

    @PostMapping("/cpu/stop")
    public Map<String, Object> cpuStop(@RequestParam(name = "group", defaultValue = "default") String group) {
        SpinGroup spinGroup = spinGroups.get(group);
        if (spinGroup == null) {
            return Collections.<String, Object>singletonMap("message", "group not found: " + group);
        }
        spinGroup.stop();
        return Collections.<String, Object>singletonMap("message", "stop requested, group=" + group);
    }

    @PostMapping("/deadlock/start")
    public Map<String, Object> deadlockStart() {
        if (!deadlockStarted.compareAndSet(false, true)) {
            return Collections.<String, Object>singletonMap("message", "deadlock already started");
        }

        final Object lockA = new Object();
        final Object lockB = new Object();

        Thread t1 = new Thread(new DeadlockWorker(lockA, lockB), "deadlock-1");
        Thread t2 = new Thread(new DeadlockWorker(lockB, lockA), "deadlock-2");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        return Collections.<String, Object>singletonMap("message", "deadlock started");
    }

    @PostMapping("/blocked/start")
    public Map<String, Object> blockedStart(@RequestParam(name = "holdSeconds", defaultValue = "600") int holdSeconds) {
        if (!blockedStarted.compareAndSet(false, true)) {
            return Collections.<String, Object>singletonMap("message", "blocked scenario already started");
        }

        if (holdSeconds < 1) {
            holdSeconds = 1;
        }
        if (holdSeconds > 3600) {
            holdSeconds = 3600;
        }

        final Object monitor = new Object();

        Thread holder = new Thread(new MonitorHolder(monitor, holdSeconds), "monitor-holder");
        Thread blocker = new Thread(new MonitorBlocker(monitor), "monitor-blocker");

        holder.setDaemon(true);
        blocker.setDaemon(true);
        holder.start();
        sleepQuietly(200);
        blocker.start();

        return Collections.<String, Object>singletonMap("message", "blocked scenario started, holdSeconds=" + holdSeconds);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> groups = new ArrayList<Map<String, Object>>();
        for (SpinGroup g : spinGroups.values()) {
            groups.add(g.status());
        }
        return new java.util.LinkedHashMap<String, Object>() {{
            put("spinGroups", groups);
            put("deadlockStarted", deadlockStarted.get());
            put("blockedStarted", blockedStarted.get());
        }};
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DeadlockWorker implements Runnable {
        private final Object first;
        private final Object second;

        private DeadlockWorker(Object first, Object second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void run() {
            while (true) {
                synchronized (first) {
                    sleepQuietly(50);
                    synchronized (second) {
                        sleepQuietly(50);
                    }
                }
            }
        }
    }

    private static final class MonitorHolder implements Runnable {
        private final Object monitor;
        private final int holdSeconds;

        private MonitorHolder(Object monitor, int holdSeconds) {
            this.monitor = monitor;
            this.holdSeconds = holdSeconds;
        }

        @Override
        public void run() {
            synchronized (monitor) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(holdSeconds);
                while (System.nanoTime() < deadline) {
                    sleepQuietly(200);
                }
            }
        }
    }

    private static final class MonitorBlocker implements Runnable {
        private final Object monitor;

        private MonitorBlocker(Object monitor) {
            this.monitor = monitor;
        }

        @Override
        public void run() {
            while (true) {
                synchronized (monitor) {
                    sleepQuietly(1000);
                }
            }
        }
    }

    private static final class SpinGroup {
        private final String group;
        private final int threads;
        private final int durationSeconds;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicLong blackhole;
        private final List<Thread> workers = new ArrayList<Thread>();

        private SpinGroup(String group, int threads, int durationSeconds, AtomicLong blackhole) {
            this.group = group;
            this.threads = threads;
            this.durationSeconds = durationSeconds;
            this.blackhole = blackhole;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(new SpinWorker(running, deadline, blackhole), "cpu-spin-" + group + "-" + i);
                t.setDaemon(true);
                workers.add(t);
                t.start();
            }
        }

        private void stop() {
            running.set(false);
        }

        private Map<String, Object> status() {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("group", group);
            m.put("threads", threads);
            m.put("durationSeconds", durationSeconds);
            m.put("running", running.get());
            m.put("workerCount", workers.size());
            return m;
        }
    }

    private static final class SpinWorker implements Runnable {
        private final AtomicBoolean running;
        private final long deadlineNanos;
        private final AtomicLong blackhole;

        private SpinWorker(AtomicBoolean running, long deadlineNanos, AtomicLong blackhole) {
            this.running = running;
            this.deadlineNanos = deadlineNanos;
            this.blackhole = blackhole;
        }

        @Override
        public void run() {
            long x = 0;
            while (running.get() && System.nanoTime() < deadlineNanos) {
                x ^= System.nanoTime();
                x += (x << 1) + 0x9e3779b97f4a7c15L;
                x = Long.rotateLeft(x, 13);
                if ((x & 1023) == 0) {
                    blackhole.set(x);
                }
            }
            blackhole.set(x);
            running.set(false);
        }
    }
}
