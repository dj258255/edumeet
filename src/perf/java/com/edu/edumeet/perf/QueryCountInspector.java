package com.edu.edumeet.perf;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 요청 하나가 날린 SQL 개수를 센다.
 *
 * <p>Hibernate 의 {@code Statistics} 는 SessionFactory 단위 누적값이라 부하 상황에서는
 * 요청별 델타를 낼 수 없다. 여러 스레드가 동시에 카운터를 올리기 때문이다.
 * {@link StatementInspector} 는 SQL 이 나갈 때마다 호출되므로 ThreadLocal 로 세면
 * 요청 단위로 정확하다. {@code open-in-view: false} 라 요청 하나가 한 스레드에서
 * 끝나는 것이 전제다.
 *
 * <p>부하 측정 전용이다. perf 프로파일에서만 등록된다. (application-perf.yml)
 */
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<int[]> COUNTER = ThreadLocal.withInitial(() -> new int[1]);

    public static void reset() {
        COUNTER.get()[0] = 0;
    }

    public static int count() {
        return COUNTER.get()[0];
    }

    @Override
    public String inspect(String sql) {
        COUNTER.get()[0]++;
        return sql;
    }
}
