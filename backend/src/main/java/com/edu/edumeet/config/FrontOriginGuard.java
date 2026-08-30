package com.edu.edumeet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 운영에서 허용 출처가 개발용 값이면 앱을 안 띄운다. (#186)
 *
 * <h2>왜 생겼나</h2>
 *
 * <p>운영 {@code .env} 에 이 줄이 있었다.
 *
 * <pre>
 *   FRONT_URL=http://localhost:3000FRONT_URL2=http://localhost:5173
 *                                   ↑ 여기서 줄이 안 나뉘었다
 * </pre>
 *
 * <p>줄바꿈 하나가 빠져 두 변수가 한 값으로 합쳐졌고, {@code FRONT_URL2} 는
 * 아예 존재하지 않아 기본값(개발용)으로 떨어졌다.
 *
 * <p>이 값은 <b>브라우저가 붙을 수 있는 출처</b>를 정한다. 그래서 운영에서
 * 이렇게 됐다.
 *
 * <pre>
 *   Origin 없음 (부하 도구)                    101 Switching Protocols
 *   Origin: https://studywithtymee.com (브라우저)  403
 * </pre>
 *
 * <p><b>채팅도 실시간 자막도 브라우저에서 하나도 안 됐다.</b>
 * 프론트는 사이트 도메인에서, API 는 api 서브도메인에서 나가므로
 * 모든 호출이 교차 출처라 로그인부터 막힌다.
 *
 * <h2>왜 아무도 몰랐나</h2>
 *
 * <p><b>확인하던 모든 것이 {@code Origin} 헤더를 안 보냈다.</b>
 *
 * <ul>
 *   <li>부하 측정(k6·python) — 브라우저가 아니라 그 헤더를 안 붙인다</li>
 *   <li>배포 스모크 검사 — 정적 페이지를 받아 200 을 본다. API 를 안 부른다</li>
 *   <li>통합 시험 — 같은 프로세스 안이라 교차 출처가 아니다</li>
 * </ul>
 *
 * <p>동일 출처 정책은 <b>{@code Origin} 이 있을 때만</b> 검사한다.
 * 그래서 사람이 아닌 것들은 전부 통과했고, 초록불만 보였다.
 *
 * <h2>그래서 무엇을 막나</h2>
 *
 * <p>운영 프로필에서 이 값이 개발용이거나 모양이 이상하면 <b>앱이 안 뜬다.</b>
 * 이 저장소가 이미 {@code APP_IMAGE} 와 {@code GRAFANA_PASSWORD} 에 쓰는 기준과 같다 —
 * <b>조용히 잘못 뜨는 것보다 안 뜨는 게 낫다.</b>
 */
@Component
@Profile("prod")
@Slf4j
public class FrontOriginGuard {

    public FrontOriginGuard(@Value("${front.url}") String frontUrl,
                            @Value("${front.url2}") String frontUrl2) {
        check("FRONT_URL", frontUrl);
        check("FRONT_URL2", frontUrl2);
        log.info("허용 출처 확인: {} · {}", frontUrl, frontUrl2);
    }

    /** 개발용이거나 모양이 이상하면 던진다. */
    static void check(String name, String value) {
        String v = value == null ? "" : value.trim();

        if (v.isEmpty()) {
            throw fail(name, v, "비어 있다");
        }
        // ★ 이번 사고의 모양이다. 출처에는 '=' 가 들어갈 수 없다.
        //   줄바꿈이 빠져 다음 변수가 값 뒤에 붙으면 정확히 이렇게 된다.
        if (v.contains("=")) {
            throw fail(name, v, "값 안에 '=' 가 있다. .env 에서 줄바꿈이 빠져 "
                    + "다음 변수가 이 값 뒤에 붙었을 수 있다");
        }
        if (v.chars().anyMatch(Character::isWhitespace)) {
            throw fail(name, v, "값 안에 공백이 있다");
        }
        if (!v.startsWith("https://")) {
            throw fail(name, v, "운영 출처는 https 여야 한다. 브라우저가 https 페이지에서 "
                    + "http 출처로 붙지 못한다");
        }
        for (String dev : List.of("localhost", "127.0.0.1", "0.0.0.0")) {
            if (v.contains(dev)) {
                throw fail(name, v, "개발용 주소(" + dev + ")다. 운영 브라우저의 출처와 "
                        + "다르면 WebSocket 핸드셰이크와 CORS 가 전부 막힌다");
            }
        }
    }

    private static IllegalStateException fail(String name, String value, String why) {
        return new IllegalStateException("""
                %s 가 운영에 쓸 수 없는 값이다: "%s"
                  %s

                이 값은 브라우저가 붙을 수 있는 출처를 정한다.
                잘못되면 조용히 실패한다 - 부하 도구와 헬스체크는 Origin 을 안 보내서
                전부 통과하고, 진짜 브라우저만 403 을 받는다.
                그래서 안 뜨게 한다.""".formatted(name, value, why));
    }
}
