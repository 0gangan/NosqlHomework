package com.example.Nosql_Homework.crawler.util;

import java.util.Map;

/**
 * 编程语言名称归一化工具
 * <p>
 * 确保语言名称在入库和查询时统一大小写格式。
 * 例如: "java" → "Java", "JAVASCRIPT" → "JavaScript", "c++" → "C++"
 */
public final class LanguageNormalizer {

    private LanguageNormalizer() {}

    /**
     * 已知的特殊大小写 + 常见缩写映射 → 标准名
     * key 统一小写，value 为 GitHub 标准语言名
     */
    private static final Map<String, String> SPECIAL_CASES = Map.ofEntries(
            // === 大小写特殊 ===
            Map.entry("javascript", "JavaScript"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("c++", "C++"),
            Map.entry("c#", "C#"),
            Map.entry("f#", "F#"),
            Map.entry("objective-c", "Objective-C"),
            Map.entry("scala", "Scala"),
            Map.entry("kotlin", "Kotlin"),
            Map.entry("go", "Go"),
            Map.entry("rust", "Rust"),
            Map.entry("swift", "Swift"),
            Map.entry("ruby", "Ruby"),
            Map.entry("php", "PHP"),
            Map.entry("r", "R"),
            Map.entry("css", "CSS"),
            Map.entry("html", "HTML"),
            Map.entry("sql", "SQL"),
            Map.entry("shell", "Shell"),
            Map.entry("dart", "Dart"),
            Map.entry("lua", "Lua"),
            Map.entry("haskell", "Haskell"),
            Map.entry("elixir", "Elixir"),
            Map.entry("clojure", "Clojure"),
            Map.entry("erlang", "Erlang"),
            Map.entry("julia", "Julia"),
            Map.entry("java", "Java"),
            Map.entry("python", "Python"),
            Map.entry("vue", "Vue"),
            // === 常见缩写 ===
            Map.entry("js", "JavaScript"),
            Map.entry("ts", "TypeScript"),
            Map.entry("py", "Python"),
            Map.entry("rb", "Ruby"),
            Map.entry("rs", "Rust"),
            Map.entry("kt", "Kotlin"),
            Map.entry("sw", "Swift"),
            Map.entry("sh", "Shell"),
            Map.entry("hs", "Haskell"),
            Map.entry("ex", "Elixir"),
            Map.entry("exs", "Elixir"),
            Map.entry("clj", "Clojure"),
            Map.entry("erl", "Erlang"),
            Map.entry("jl", "Julia"),
            Map.entry("cpp", "C++"),
            Map.entry("cs", "C#"),
            Map.entry("fs", "F#"),
            Map.entry("objc", "Objective-C"),
            Map.entry("obj-c", "Objective-C")
    );

    /**
     * 归一化语言名称
     * <ul>
     *   <li>已知特殊名称 → 查表返回标准名</li>
     *   <li>未知名称 → 首字母大写, 其余小写</li>
     *   <li>null 或 blank → 返回原值</li>
     * </ul>
     */
    public static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return language;
        }
        String lower = language.trim().toLowerCase();
        String special = SPECIAL_CASES.get(lower);
        if (special != null) {
            return special;
        }
        // 兜底: 首字母大写
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
