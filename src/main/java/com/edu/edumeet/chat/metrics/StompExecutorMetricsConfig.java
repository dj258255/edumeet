package com.edu.edumeet.chat.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * STOMP 채널 실행기를 Micrometer 에 바인딩한다. (#39)
 *
 * <h3>왜 이게 붕괴 측정의 핵심인가</h3>
 * 기본 실행기는 <b>큐 용량이 무한</b>이다. 스레드 풀이 절대 거부하지 않으므로
 * <b>포화가 에러가 아니라 지연으로만 나타난다.</b>
 * 에러율만 보면 서버는 정상인데 사용자는 메시지를 수 초 뒤에 받는다.
 *
 * <p>{@code executor.queued} 가 없으면 그 상태를 볼 방법이 없다.
 *
 * <h3>왜 이름으로 찾는가</h3>
 * {@code AbstractMessageBrokerConfiguration} 이 정의하는 빈이라 타입으로 찾으면
 * 애플리케이션의 다른 {@code TaskExecutor} 와 섞인다. 이름으로 콕 집는다.
 * <b>없으면 조용히 넘어간다</b> — Spring 이 구현을 바꿔도 앱이 뜨지 못하는 일은 없어야 한다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StompExecutorMetricsConfig implements InitializingBean {

    private static final List<String> EXECUTOR_BEANS = List.of(
            "clientInboundChannelExecutor",
            "clientOutboundChannelExecutor");

    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    @Override
    public void afterPropertiesSet() {
        for (String beanName : EXECUTOR_BEANS) {
            bind(beanName);
        }
    }

    private void bind(String beanName) {
        if (!applicationContext.containsBean(beanName)) {
            log.warn("{} 빈이 없다. 큐 길이 지표 없이 뜬다 - 붕괴 측정 전에 확인해야 한다.", beanName);
            return;
        }
        Object bean = applicationContext.getBean(beanName);
        ExecutorService executor = unwrap(bean);
        if (executor == null) {
            log.warn("{} 이 ThreadPoolTaskExecutor 가 아니다({}). 큐 길이를 잴 수 없다.",
                    beanName, bean.getClass().getName());
            return;
        }
        ExecutorServiceMetrics.monitor(
                meterRegistry, executor, beanName, List.of(Tag.of("component", "stomp")));
        log.info("STOMP 실행기 계측 등록 - {}", beanName);
    }

    private ExecutorService unwrap(Object bean) {
        if (bean instanceof ThreadPoolTaskExecutor taskExecutor) {
            return taskExecutor.getThreadPoolExecutor();
        }
        if (bean instanceof ExecutorService executorService) {
            return executorService;
        }
        if (bean instanceof TaskExecutor) {
            return null;   // 큐를 노출하지 않는 구현이다
        }
        return null;
    }
}
