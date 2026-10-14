package com.vulpine.core;

public interface Kv {
    String get(String k);

    void put(String k, String v);

    void remove(String k);

    java.util.List<String> keys(String prefix);
}
