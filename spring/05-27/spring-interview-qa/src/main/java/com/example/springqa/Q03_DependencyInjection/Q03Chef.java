package com.example.springqa.Q03_DependencyInjection;

public class Q03Chef {
    private final String name;
    public Q03Chef(String name) { this.name = name; }
    @Override public String toString() { return "Chef{" + name + "}"; }
}
