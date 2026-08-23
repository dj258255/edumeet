package com.edu.edumeet.s3.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3Downloader {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * S3 URL에서 파일 다운로드
     */
    public S3FileInfo downloadFile(String s3Url) {
        try {
            String key = extractKeyFromUrl(s3Url);
            log.info("S3 파일 다운로드 시작 - Bucket: {}, Key: {}", bucket, key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // ResponseInputStream을 직접 받아서 처리
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);

            // response() 메서드로 메타데이터 접근
            GetObjectResponse objectResponse = responseInputStream.response();

            return S3FileInfo.builder()
                    .inputStream(responseInputStream) // ResponseInputStream을 그대로 사용
                    .contentType(objectResponse.contentType() != null ? objectResponse.contentType() : "application/octet-stream")
                    .contentLength(objectResponse.contentLength() != null ? objectResponse.contentLength() : 0L)
                    .build();

        } catch (NoSuchKeyException e) {
            log.error("S3 파일을 찾을 수 없음 - URL: {}", s3Url);
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + s3Url);
        } catch (Exception e) {
            log.error("S3 파일 다운로드 실패 - URL: {}", s3Url, e);
            throw new RuntimeException("S3 파일 다운로드에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * S3 파일 메타데이터 조회
     */
    public S3FileMetadata getFileMetadata(String s3Url) {
        try {
            String key = extractKeyFromUrl(s3Url);
            log.info("S3 파일 메타데이터 조회 - Bucket: {}, Key: {}", bucket, key);

            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);

            return S3FileMetadata.builder()
                    .contentType(response.contentType() != null ? response.contentType() : "application/octet-stream")
                    .contentLength(response.contentLength() != null ? response.contentLength() : 0L)
                    .lastModified(response.lastModified())
                    .build();

        } catch (NoSuchKeyException e) {
            log.error("S3 파일을 찾을 수 없음 - URL: {}", s3Url);
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + s3Url);
        } catch (Exception e) {
            log.error("S3 파일 메타데이터 조회 실패 - URL: {}", s3Url, e);
            throw new RuntimeException("S3 파일 메타데이터 조회에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * S3 파일 존재 여부 확인
     */
    public boolean isFileExists(String s3Url) {
        try {
            String key = extractKeyFromUrl(s3Url);
            log.debug("S3 파일 존재 여부 확인 - Bucket: {}, Key: {}", bucket, key);

            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            log.info("S3 파일이 존재하지 않음 - URL: {}", s3Url);
            return false;
        } catch (Exception e) {
            log.error("S3 파일 존재 여부 확인 실패 - URL: {}", s3Url, e);
            return false;
        }
    }

    /**
     * S3 URL에서 키(파일 경로) 추출
     */
    private String extractKeyFromUrl(String s3Url) {
        try {
            if (s3Url == null || s3Url.trim().isEmpty()) {
                throw new IllegalArgumentException("S3 URL이 비어있습니다.");
            }

            log.debug("S3 URL 파싱 시작: {}", s3Url);

            // URL 형식: https://bucket-name.s3.region.amazonaws.com/key
            // 또는: https://s3.region.amazonaws.com/bucket-name/key
            URI uri = new URI(s3Url);
            String path = uri.getPath();

            // 맨 앞의 '/' 제거
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // 경로가 비어있으면 오류
            if (path.isEmpty()) {
                throw new IllegalArgumentException("S3 URL에서 키를 추출할 수 없습니다: " + s3Url);
            }

            // Virtual-hosted-style URL인지 확인 (bucket-name.s3.region.amazonaws.com)
            String host = uri.getHost();
            if (host != null && host.startsWith(bucket + ".s3")) {
                // Virtual-hosted-style: 전체 path가 key
                log.debug("Virtual-hosted-style URL에서 추출된 키: {}", path);
                return path;
            } else {
                // Path-style URL: bucket 이름 제거 필요
                if (path.startsWith(bucket + "/")) {
                    path = path.substring(bucket.length() + 1);
                }
                log.debug("Path-style URL에서 추출된 키: {}", path);
                return path;
            }

        } catch (URISyntaxException e) {
            log.error("잘못된 S3 URL 형식 - URL: {}", s3Url, e);
            throw new IllegalArgumentException("잘못된 S3 URL 형식입니다: " + s3Url);
        }
    }

    /**
     * S3 파일 정보 (다운로드용)
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class S3FileInfo {
        private InputStream inputStream;
        private String contentType;
        private long contentLength;
    }

    /**
     * S3 파일 메타데이터
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class S3FileMetadata {
        private String contentType;
        private long contentLength;
        private java.time.Instant lastModified;
    }
}