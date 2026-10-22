package com.nerdnam.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 모듈 경계 검사 (01 문서 4장 의존 규칙, BE-17). 소스 파일의 import 를 읽어 확인한다:
 * <ul>
 *   <li>다른 모듈의 엔티티(@Entity)·리포지토리·infrastructure 를 쓰지 않는다 — 공개 서비스·조회 결과·이벤트·값만</li>
 *   <li>모듈 사이 의존에 순환이 없다</li>
 * </ul>
 * common·infra 는 모두가 쓰는 공통 모듈이라 검사에서 뺀다.
 */
class ModuleBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/com/nerdnam");
    private static final Set<String> SHARED = Set.of("common", "infra");
    private static final Pattern IMPORT = Pattern.compile("^import com\\.nerdnam\\.([a-z]+)\\.([\\w.]+);", Pattern.MULTILINE);

    private static final Map<String, String> sources = new HashMap<>();   // 클래스 이름(com.nerdnam...) → 소스

    @BeforeAll
    static void load() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fqcn = "com.nerdnam." + ROOT.relativize(file).toString()
                        .replace(".java", "").replace('/', '.').replace('\\', '.');
                sources.put(fqcn, Files.readString(file));
            }
        }
    }

    @Test
    void 다른_모듈의_엔티티_리포지토리_infrastructure를_쓰지_않는다() {
        List<String> violations = new ArrayList<>();
        dependencies((from, fromClass, to, target) -> {
            String source = sources.getOrDefault(target, "");
            boolean entity = source.contains("@Entity");
            boolean repository = target.endsWith("Repository");
            boolean infrastructure = target.contains(".infrastructure.");
            if (entity || repository || infrastructure) {
                violations.add(fromClass + " → " + target);
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void 모듈_사이에_순환_의존이_없다() {
        Map<String, Set<String>> graph = new TreeMap<>();
        dependencies((from, fromClass, to, target) -> graph.computeIfAbsent(from, k -> new TreeSet<>()).add(to));

        List<String> cycles = new ArrayList<>();
        for (String start : graph.keySet()) {
            if (reaches(graph, start, start, new HashSet<>())) {
                cycles.add(start + " → " + graph.get(start));
            }
        }
        assertThat(cycles).as("모듈 의존: " + graph).isEmpty();
    }

    private interface Edge {
        void accept(String fromModule, String fromClass, String toModule, String targetClass);
    }

    private static void dependencies(Edge edge) {
        sources.forEach((fqcn, source) -> {
            String from = module(fqcn);
            if (from == null || SHARED.contains(from)) {
                return;
            }
            Matcher m = IMPORT.matcher(source);
            while (m.find()) {
                String to = m.group(1);
                if (!to.equals(from) && !SHARED.contains(to) && !to.equals("NerdnamApplication")) {
                    edge.accept(from, fqcn, to, "com.nerdnam." + to + "." + m.group(2));
                }
            }
        });
    }

    private static boolean reaches(Map<String, Set<String>> graph, String current, String target, Set<String> seen) {
        for (String next : graph.getOrDefault(current, Set.of())) {
            if (next.equals(target)) {
                return true;
            }
            if (seen.add(next) && reaches(graph, next, target, seen)) {
                return true;
            }
        }
        return false;
    }

    private static String module(String fqcn) {
        String rest = fqcn.substring("com.nerdnam.".length());
        int dot = rest.indexOf('.');
        return dot < 0 ? null : rest.substring(0, dot);
    }
}
