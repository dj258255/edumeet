package com.edu.edumeet.s3.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
@Slf4j
public class S3Config {

    /**
     * 자격증명이 없을 때 쓰는 자리표시자. {@code application.yml} 의 기본값과 같아야 한다. (#51)
     *
     * <p>빈 문자열을 두면 {@code AwsBasicCredentials.create} 가 NPE 를 던져
     * <b>앱 전체가 뜨지 못한다.</b> S3 는 첨부·썸네일·요약본에만 쓰이는데
     * 그것 때문에 서비스가 안 뜨는 건 과하다.
     *
     * <p>대신 <b>조용히 넘어가지 않는다.</b> 기동 시 경고를 남긴다 -
     * 설정이 빠진 채로 떠 있는 상태를 로그만 보고 알 수 있어야 한다.
     */
    private static final String NOT_CONFIGURED = "not-configured";

    /**
     * 한 번의 S3 호출이 재시도까지 포함해 잡을 수 있는 최대 시간.
     *
     * <p>AWS SDK v2 는 소켓 타임아웃(기본 30초)으로 <b>멈춤</b>은 막아주지만
     * {@code apiCallTimeout} 은 기본값이 없다. 그래서 재시도가 겹치면
     * (기본 3회 x 30초) 한 요청이 90초 넘게 스레드를 잡을 수 있다.
     *
     * <p>2분으로 넉넉히 잡은 이유 — 이 값은 <b>전송 시간까지 포함</b>한다.
     * 업로드 상한이 100MB(application.yml)이므로 짧게 걸면 정상 업로드가 죽는다.
     * 목표는 업로드를 빠르게 실패시키는 것이 아니라 <b>병적인 상황에 상한을 두는 것</b>이다.
     */
    private static final Duration API_CALL_TIMEOUT = Duration.ofMinutes(2);

    private static ClientOverrideConfiguration timeouts() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(API_CALL_TIMEOUT)
                .build();
    }

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;



    @PostConstruct
    void warnIfNotConfigured() {
        if (NOT_CONFIGURED.equals(accessKey) || NOT_CONFIGURED.equals(secretKey)) {
            log.warn("AWS 자격증명이 설정되지 않았다. 앱은 뜨지만 S3 를 쓰는 기능(첨부·썸네일·요약본)은 "
                     + "호출 시점에 실패한다. .env 의 AWS_ACCESS_KEY / AWS_SECRET_KEY 를 채워야 한다. (#51)");
        }
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .overrideConfiguration(timeouts())
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }


    @Bean
    public S3Presigner s3Presigner(){
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }
}
