package com.nerdnam.infra.ai;

/**
 * SSE 응답 본문에서 결과 JSON 을 꺼낸다. 규칙:
 * <ul>
 *   <li>{@code event: error} 가 있으면 실패</li>
 *   <li>{@code event: result} 의 data 가 있으면 그것 (여러 개면 마지막)</li>
 *   <li>SSE 가 아니면(JSON 한 덩어리) 본문 그대로</li>
 * </ul>
 * 진행 상황 이벤트(event: delta 등)는 무시한다. 여러 줄 data 는 줄바꿈으로 잇는다 (SSE 규격).
 */
final class ServerSentEvents {

    private ServerSentEvents() {
    }

    static String resultData(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("빈 응답");
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        String result = null;
        String event = "message";
        StringBuilder data = new StringBuilder();
        for (String line : (body + "\n\n").split("\\r?\\n", -1)) {
            if (line.isEmpty()) {   // 이벤트 하나 끝
                if (!data.isEmpty()) {
                    if ("error".equals(event)) {
                        throw new IllegalStateException("AI 서버 오류 이벤트: " + data);
                    }
                    if ("result".equals(event)) {
                        result = data.toString();
                    }
                }
                event = "message";
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = line.substring(6).strip();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).stripLeading());
            }
        }
        if (result == null) {
            throw new IllegalStateException("결과 이벤트가 없어요");
        }
        return result;
    }
}
