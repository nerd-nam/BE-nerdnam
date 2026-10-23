package com.nerdnam.matching.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 적합도 리포트 요약 (S08 위쪽). 판정 결과로 계산만 한다 — AI 를 부르지 않고 저장하지 않는다 (ADR-0003).
 *
 * <p>한 줄 진단 문장 틀 (단위 테스트로 고정):
 * <ol>
 *   <li>필수를 모두 충족: "필수 N개 모두 근거 경험이 있어요."</li>
 *   <li>필수 미충족이 있음: "필수 N개 중 M개는 근거 경험을 찾지 못했어요(요건 이름 등)."</li>
 *   <li>필수 부분 충족만 있음: "필수 N개 중 K개는 부분 충족이에요."</li>
 *   <li>+ 근거 하나가 요건 2개 이상을 뒷받침하면: "X 경험이 가장 많은 요건(K개)을 뒷받침해요."</li>
 * </ol>
 */
public record FitReport(Count required, Count preferred, String diagnosis) {

    private static final int NAME_MAX = 20;

    /** @param met 충족, partial 부분 충족, total 요건 수 */
    public record Count(int met, int partial, int unmet, int total) {
    }

    static FitReport of(List<RequirementMatch> matches) {
        List<RequirementMatch> required = matches.stream().filter(RequirementMatch::isRequired).toList();
        List<RequirementMatch> preferred = matches.stream().filter(m -> !m.isRequired()).toList();
        return new FitReport(count(required), count(preferred), diagnosis(required, matches));
    }

    private static Count count(List<RequirementMatch> list) {
        int met = (int) list.stream().filter(m -> m.getFinalVerdict() == Verdict.MET).count();
        int partial = (int) list.stream().filter(m -> m.getFinalVerdict() == Verdict.PARTIAL).count();
        return new Count(met, partial, list.size() - met - partial, list.size());
    }

    static String diagnosis(List<RequirementMatch> required, List<RequirementMatch> all) {
        List<String> sentences = new ArrayList<>();
        int n = required.size();
        List<RequirementMatch> unmet = required.stream().filter(m -> m.getFinalVerdict() == Verdict.UNMET).toList();
        long partial = required.stream().filter(m -> m.getFinalVerdict() == Verdict.PARTIAL).count();
        if (n > 0 && unmet.isEmpty() && partial == 0) {
            sentences.add("필수 " + n + "개 모두 근거 경험이 있어요.");
        } else if (!unmet.isEmpty()) {
            String names = cut(unmet.getFirst().getRequirementText()) + (unmet.size() > 1 ? " 등" : "");
            sentences.add("필수 " + n + "개 중 " + unmet.size() + "개는 근거 경험을 찾지 못했어요(" + names + ").");
        } else if (partial > 0) {
            sentences.add("필수 " + n + "개 중 " + partial + "개는 부분 충족이에요.");
        }
        strongestEvidence(all).ifPresent(sentences::add);
        return String.join(" ", sentences);
    }

    /** 충족·부분 충족 요건을 가장 많이 뒷받침한 근거 (2개 이상일 때만) */
    private static java.util.Optional<String> strongestEvidence(List<RequirementMatch> all) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, MatchEvidence> sample = new LinkedHashMap<>();
        for (RequirementMatch m : all) {
            if (m.getFinalVerdict() == Verdict.UNMET) {
                continue;
            }
            Map<String, MatchEvidence> perRequirement = new LinkedHashMap<>();   // 한 요건 안의 같은 원본은 한 번만
            m.getEvidences().forEach(e -> perRequirement.putIfAbsent(e.getSourceType() + ":" + e.getSourceId(), e));
            perRequirement.forEach((key, e) -> {
                counts.merge(key, 1, Integer::sum);
                sample.putIfAbsent(key, e);
            });
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
                        .thenComparingInt(e -> priority(sample.get(e.getKey()).getSourceType())))
                .filter(e -> e.getValue() >= 2)
                .map(e -> sample.get(e.getKey()).label() + " 경험이 가장 많은 요건(" + e.getValue() + "개)을 뒷받침해요.");
    }

    /** 같은 수면 구체적인 근거(사례 > 프로젝트 > 경력 > 기술)를 앞세운다 */
    private static int priority(EvidenceSourceType type) {
        return switch (type) {
            case EPISODE -> 3;
            case PROJECT_SUMMARY -> 2;
            case CAREER -> 1;
            case SKILL -> 0;
        };
    }

    private static String cut(String text) {
        return text.length() <= NAME_MAX ? text : text.substring(0, NAME_MAX - 1) + "…";
    }
}
