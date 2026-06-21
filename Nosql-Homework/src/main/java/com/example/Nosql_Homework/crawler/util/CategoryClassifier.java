package com.example.Nosql_Homework.crawler.util;

import com.example.Nosql_Homework.entity.Project;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 项目品类分类器 — 基于 topics + description 的关键词规则匹配
 * <p>
 * 分类结果语义:
 * <ul>
 *   <li>{@code "web" / "ai" / ...} — 匹配到某个分类</li>
 *   <li>{@code CategoryClassifier#UNCLASSIFIED_EMPTY ("")} — 已尝试但未匹配到任何分类</li>
 *   <li>调用方自行维护 {@code null} = 从未尝试分类</li>
 * </ul>
 */
@Slf4j
public final class CategoryClassifier {

    private CategoryClassifier() {}

    /** 已尝试分类但未匹配成功的标记值，区别于 null（从未尝试） */
    public static final String UNCLASSIFIED_EMPTY = "";

    /**
     * 分类 → 匹配关键词集合
     * key 必须全小写；topics / description 分词后也会转小写做匹配
     */
    private static final Map<String, Set<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        // ===== AI / ML =====
        CATEGORY_KEYWORDS.put("ai", new HashSet<>(Arrays.asList(
                "machine-learning", "deep-learning", "neural-network", "nlp",
                "natural-language-processing", "computer-vision", "llm",
                "large-language-model", "gpt", "chatgpt", "transformer",
                "pytorch", "tensorflow", "keras", "jax", "huggingface",
                "artificial-intelligence", "reinforcement-learning",
                "generative-ai", "stable-diffusion", "llama", "langchain",
                "vector-database", "embeddings", "text-generation",
                "image-generation", "speech-recognition", "object-detection",
                "semantic-search", "rag",
                "ai", "agent", "agents", "deepseek", "mcp",
                "springai", "context-engineering", "prompt-engineering",
                "copilot", "openai", "anthropic", "claude", "gemini",
                "diffusion", "fine-tuning", "lora", "rnn", "cnn",
                "tokenizer", "chatbot", "ai-agent", "llmops"
        )));

        // ===== Web =====
        CATEGORY_KEYWORDS.put("web", new HashSet<>(Arrays.asList(
                "react", "vue", "angular", "svelte", "nextjs", "nuxt",
                "frontend", "web", "website", "webapp", "web-application",
                "spa", "ssr", "jamstack", "html", "css", "javascript",
                "browser", "pwa", "webpack", "vite", "babel",
                "tailwindcss", "bootstrap", "material-ui", "ant-design",
                "web-development", "web-framework", "fullstack"
        )));

        // ===== Mobile =====
        CATEGORY_KEYWORDS.put("mobile", new HashSet<>(Arrays.asList(
                "android", "ios", "flutter", "react-native", "swift",
                "kotlin-multiplatform", "mobile", "mobile-app", "xamarin",
                "ionic", "capacitor", "cordova", "wearos", "watchos",
                "swiftui", "jetpack-compose", "mobile-development"
        )));

        // ===== Desktop =====
        CATEGORY_KEYWORDS.put("desktop", new HashSet<>(Arrays.asList(
                "desktop", "electron", "tauri", "gtk", "qt", "wpf",
                "winforms", "uwp", "javafx", "swing", "desktop-app",
                "desktop-application", "cross-platform-desktop"
        )));

        // ===== Framework =====
        CATEGORY_KEYWORDS.put("framework", new HashSet<>(Arrays.asList(
                "framework", "spring", "spring-boot", "django", "express",
                "laravel", "rails", "flask", "fastapi", "nestjs",
                "gin", "echo", "fiber", "micronaut", "quarkus",
                "beego", "tornado", "play-framework", "vertx",
                "web-framework", "micro-framework", "backend-framework"
        )));

        // ===== Library =====
        CATEGORY_KEYWORDS.put("library", new HashSet<>(Arrays.asList(
                "library", "sdk", "api-client", "utilities", "utils",
                "helpers", "toolkit", "client-library", "driver",
                "connector", "wrapper", "binding", "plugin", "extension",
                "middleware", "adapter", "sdk-java", "sdk-python",
                "sdk-go", "sdk-js", "java-library", "python-library"
        )));

        // ===== CLI =====
        CATEGORY_KEYWORDS.put("cli", new HashSet<>(Arrays.asList(
                "cli", "command-line", "terminal", "tui", "shell",
                "bash", "zsh", "powershell", "console", "command-line-tool",
                "command-line-interface", "cobra", "clap", "commander",
                "terminal-based", "terminal-emulator"
        )));

        // ===== API =====
        CATEGORY_KEYWORDS.put("api", new HashSet<>(Arrays.asList(
                "api", "rest-api", "graphql", "grpc", "restful",
                "openapi", "swagger", "api-gateway", "api-server",
                "api-rest", "rest", "restful-api", "http-api",
                "api-wrapper", "backend-api", "json-api"
        )));

        // ===== Database =====
        CATEGORY_KEYWORDS.put("database", new HashSet<>(Arrays.asList(
                "database", "sql", "nosql", "postgresql", "mysql",
                "mongodb", "redis", "sqlite", "orm", "jdbc",
                "mariadb", "cassandra", "elasticsearch", "neo4j",
                "graph-database", "key-value-store", "document-database",
                "time-series-database", "in-memory-database",
                "database-driver", "connection-pool", "query-builder"
        )));

        // ===== DevOps =====
        CATEGORY_KEYWORDS.put("devops", new HashSet<>(Arrays.asList(
                "docker", "kubernetes", "k8s", "ci-cd", "terraform",
                "ansible", "jenkins", "devops", "infrastructure",
                "infrastructure-as-code", "helm", "prometheus",
                "grafana", "github-actions", "gitlab-ci", "circleci",
                "travis-ci", "vagrant", "packer", "container",
                "containerization", "orchestration", "service-mesh",
                "istio", "cloud-native", "microservices"
        )));

        // ===== Security =====
        CATEGORY_KEYWORDS.put("security", new HashSet<>(Arrays.asList(
                "security", "crypto", "cryptography", "encryption",
                "authentication", "authorization", "oauth", "oauth2",
                "jwt", "openid-connect", "vulnerability", "penetration-testing",
                "security-tools", "ssl", "tls", "xss", "csrf",
                "web-security", "network-security", "security-scanner",
                "password-manager", "2fa", "rbac", "audit"
        )));

        // ===== Game =====
        CATEGORY_KEYWORDS.put("game", new HashSet<>(Arrays.asList(
                "game", "gamedev", "game-engine", "unity", "godot",
                "unreal", "game-development", "gaming", "opengl",
                "vulkan", "directx", "sdl", "raylib", "monogame",
                "game-framework", "game-server", "multiplayer",
                "game-mod", "emulator", "retro-game"
        )));

        // ===== Tool =====
        CATEGORY_KEYWORDS.put("tool", new HashSet<>(Arrays.asList(
                "tool", "tools", "devtools", "development-tools",
                "developer-tools", "productivity", "automation",
                "generator", "scaffold", "boilerplate", "starter",
                "template", "code-generator", "debugger", "profiler",
                "linter", "formatter", "package-manager", "build-tool"
        )));

        // ===== Data / Analytics =====
        CATEGORY_KEYWORDS.put("data", new HashSet<>(Arrays.asList(
                "data-science", "data-analysis", "data-visualization",
                "big-data", "analytics", "etl", "data-pipeline",
                "data-engineering", "spark", "hadoop", "kafka",
                "flink", "airflow", "dbt", "stream-processing",
                "batch-processing", "data-warehouse", "data-lake",
                "business-intelligence", "dashboard", "reporting",
                "pandas", "numpy", "matplotlib", "jupyter-notebook"
        )));
    }

    /**
     * 对项目进行分类
     * <p>topics 权重 3x，description 权重 1x，优先信任 GitHub 人工标注的话题标签</p>
     * @return 分类名 (如 "web")，若无匹配则返回 {@link #UNCLASSIFIED_EMPTY}
     */
    public static String classify(Project project) {
        if (project == null) return UNCLASSIFIED_EMPTY;

        // 分开收集 topics 标签和 description 标签
        Set<String> topicTags = new HashSet<>();
        if (project.getTopics() != null) {
            for (String topic : project.getTopics()) {
                if (topic != null) topicTags.add(topic.toLowerCase().trim());
            }
        }

        Set<String> descTags = new HashSet<>();
        if (project.getDescription() != null) {
            for (String word : project.getDescription().toLowerCase().split("\\W+")) {
                String w = word.trim();
                if (!w.isEmpty() && w.length() >= 2) descTags.add(w);
            }
        }

        // 计算每个分类的加权得分: topic匹配 ×3 + description匹配 ×1
        String bestCategory = UNCLASSIFIED_EMPTY;
        int bestScore = 0;

        for (Map.Entry<String, Set<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            Set<String> keywords = entry.getValue();
            int topicScore = 0;
            int descScore = 0;

            for (String tag : topicTags) {
                if (keywords.contains(tag)) topicScore++;
            }
            for (String tag : descTags) {
                if (keywords.contains(tag)) descScore++;
            }

            int totalScore = topicScore * 3 + descScore;
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestCategory = entry.getKey();
            }
        }

        if (bestScore == 0) {
            log.debug("  未匹配到分类: fullName={}, topics={}", project.getFullName(), project.getTopics());
            return UNCLASSIFIED_EMPTY;
        }

        log.debug("  分类结果: fullName={} → {} (得分: {})", project.getFullName(), bestCategory, bestScore);
        return bestCategory;
    }

    /**
     * 对项目进行分类，同时区分"从未分类"和"已尝试无结果"
     * @return 分类名，无匹配返回 {@link #UNCLASSIFIED_EMPTY}
     */
    public static String classifyOrEmpty(Project project) {
        String result = classify(project);
        return result != null ? result : UNCLASSIFIED_EMPTY;
    }

    /** 获取所有已定义的分类名称 */
    public static Set<String> getAllCategories() {
        return Collections.unmodifiableSet(CATEGORY_KEYWORDS.keySet());
    }
}
