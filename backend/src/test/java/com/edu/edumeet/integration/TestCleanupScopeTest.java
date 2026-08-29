package com.edu.edumeet.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시험이 자기 것만 지우는지 본다. (#172)
 *
 * <p>스프링 컨텍스트는 시험 클래스끼리 공유된다. 그래서 정리 코드에서
 * 조건 없이 표를 비우면 <b>다른 시험이 쓰는 중인 행까지 지운다.</b>
 *
 * <p>실제로 이렇게 깨졌다 - 정원 동시성 시험이 강의실을 통째로 지우다가
 * 다른 시험이 만든 수강생에 외래키로 걸렸다. 수강생을 만들지도 않는
 * 시험이 실패하고, 남의 데이터가 그 순간 있었느냐에 따라 갈리니
 * <b>실행마다 다른 시험이 빨개진다.</b>
 *
 * <p>"가끔 빨간" 상태는 사람이 재실행부터 누르게 만들고,
 * 재실행은 진짜 실패까지 같이 묻는다. 그래서 규칙으로 고정한다.
 *
 * <p>대상은 <b>여러 시험이 함께 쓰는 표</b>다. 게시판·댓글처럼 그 시험들만
 * 쓰는 표는 통째로 비워도 남에게 영향이 없어 뺐다. 목록을 늘리는 기준은
 * "다른 시험도 이 표에 행을 만드는가" 다.
 */
@DisplayName("시험 정리는 자기가 만든 것만 지운다")
class TestCleanupScopeTest {

    /** 회의·자막·채팅 계열은 시험 여러 개가 같은 컨텍스트에서 함께 쓴다. */
    private static final List<String> SHARED = List.of(
            "Meeting", "MeetingParticipant", "ChatMessage",
            "CaptionSegment", "ClassRoom", "ClassMember", "Member");

    /** {@code DELETE FROM X} 뒤에 WHERE 도 별칭도 없으면 표 전체를 지운다. */
    private static final Pattern BULK = Pattern.compile(
            "DELETE\\s+FROM\\s+(\\w+)\\s*(?=\")");

    @Test
    @DisplayName("★ 공유하는 표를 조건 없이 비우지 않는다")
    void no_unscoped_delete_on_shared_tables() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.stripLeading().startsWith("//") || line.stripLeading().startsWith("*")) {
                        continue;   // 주석에서 사례로 인용한 것은 잡지 않는다
                    }
                    Matcher m = BULK.matcher(line);
                    while (m.find()) {
                        if (SHARED.contains(m.group(1))) {
                            offenders.add("%s:%d  %s".formatted(f, i + 1, line.trim()));
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("""
                    조건 없이 표 전체를 지우는 곳이 있다. 같은 컨텍스트를 쓰는
                    다른 시험의 행까지 지워서, 엉뚱한 시험이 실행마다 다르게 깨진다.
                    이 시험이 만든 id 로 범위를 좁혀라 -
                      DELETE FROM Meeting m WHERE m.id IN :ids""")
                .isEmpty();
    }

    @Test
    @DisplayName("규칙이 실제로 잡는지 확인한다 - 안 잡히는 규칙은 없는 것과 같다")
    void the_rule_actually_matches() {
        assertThat(BULK.matcher("em.createQuery(\"DELETE FROM Meeting\").executeUpdate();").find())
                .as("조건 없는 삭제를 못 잡으면 이 시험은 늘 초록이다")
                .isTrue();
        assertThat(BULK.matcher("em.createQuery(\"DELETE FROM Meeting m WHERE m.id = :id\")").find())
                .as("범위를 좁힌 삭제까지 잡으면 고칠 방법이 없어진다")
                .isFalse();
    }
}
