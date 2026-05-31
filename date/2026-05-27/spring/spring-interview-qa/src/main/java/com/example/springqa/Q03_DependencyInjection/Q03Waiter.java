package com.example.springqa.Q03_DependencyInjection;

public class Q03Waiter {
    private final String name;
    public Q03Waiter(String name) { this.name = name; }
    @Override public String toString() { return "Waiter{" + name + "}"; }
}
