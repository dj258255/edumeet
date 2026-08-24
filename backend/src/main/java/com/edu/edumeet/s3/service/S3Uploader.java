package com.edu.edumeet.s3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.File;
import java.nio.file.Paths;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3Uploader {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    /** S3 호환 엔드포인트. 비면 AWS S3. (#64) */
    @Value("${spring.cloud.aws.s3.endpoint:}")
    private String endpoint;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    // 로컬 파일을 S3로 업로드
    public String upload(String filePath) throws RuntimeException {
        File targetFile = new File(filePath);
        String uploadImageUrl = putS3(targetFile, targetFile.getName());
        removeOriginalFile(targetFile);
        return uploadImageUrl;
    }


    //로컬 파일을 S3의 특정 디렉토리로 업로드
    public String upload(String filePath, String s3Directory) throws RuntimeException {
        File targetFile = new File(filePath);
        String s3Key = s3Directory + "/" + targetFile.getName();
        String uploadImageUrl = putS3(targetFile, s3Key);
        removeOriginalFile(targetFile);
        return uploadImageUrl;
    }



    //Presigned URL 생성 (UUID 와 파일명 분리)
    public String generatePresignedUrl(String s3Directory, String uuid, String fileName, Duration duration) {
        try {
            String s3Key = String.format("%s/%s_%s", s3Directory, uuid, fileName);

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    // ================== URL 생성 메서드들 ==================

    //기본 원본 파일 url 생성
    public String getOriginalUrl(String uuid, String fileName) {
        return objectUrl("%s_%s".formatted(uuid, fileName));
    }

    //썸네일 url 생성(이미지인 경우만)
    public String getThumbnailUrl(String uuid, String fileName) {
        return objectUrl("s_%s_%s".formatted(uuid, fileName));
    }

    //도메인별 원본 파일 생성
    public String getDomainOriginalUrl(String domain, String uuid, String fileName) {
        return objectUrl("%s/%s_%s".formatted(domain, uuid, fileName));
    }

    //도메인별 썸네일 url 생성
    public String getDomainThumbnailUrl(String domain, String uuid, String fileName) {
        return objectUrl("%s/s_%s_%s".formatted(domain, uuid, fileName));
    }

    // ================== 파일 삭제 메서드들 ==================

    //S3에서 파일 삭제
    public void removeS3File(String fileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 성공: {}", fileName);
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {} - {}", fileName, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    // ================== URL 파싱 메서드들 ==================
    //url에서 uuid 추출
    public String extractUuidFromUrl(String url) {
        try {
            // URL에서 파일명 부분 추출
            String fileName = url.substring(url.lastIndexOf("/") + 1);
            // 첫 번째 '_' 앞까지가 UUID
            int underscoreIndex = fileName.indexOf('_');
            if (underscoreIndex > 0) {
                return fileName.substring(0, underscoreIndex);
            }
        } catch (Exception e) {
            log.error("URL에서 UUID 추출 실패: {} - {}", url, e.getMessage());
        }
        return null;
    }


    //URL에서 원본 파일명 추출
    public String extractFileNameFromUrl(String url) {
        try {
            // URL에서 파일명 부분 추출
            String fileName = url.substring(url.lastIndexOf("/") + 1);
            // 첫 번째 '_' 뒤부터가 원본 파일명
            int underscoreIndex = fileName.indexOf('_');
            if (underscoreIndex > 0 && underscoreIndex < fileName.length() - 1) {
                return fileName.substring(underscoreIndex + 1);
            }
        } catch (Exception e) {
            log.error("URL에서 파일명 추출 실패: {} - {}", url, e.getMessage());
        }
        return null;
    }

    //url에서 전체 파일명 추출
    public String extractFullFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
            log.error("URL에서 전체 파일명 추출 실패: {} - {}", url, e.getMessage());
        }
        return null;
    }

    // ================== 헬퍼 메서드들 ==================

    //전체 파일명 생성(UUID_원본파이명)
    public String createFullFileName(String uuid, String fileName) {
        return uuid + "_" + fileName;
    }

    //썸네일 파일명 생성 (s_uuid_원본파일명)
    public String createThumbnailFileName(String uuid, String fileName) {
        return "s_" + uuid + "_" + fileName;
    }


    //private 메서드들
    private String putS3(File uploadFile, String s3Key) throws RuntimeException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        s3Client.putObject(putObjectRequest, Paths.get(uploadFile.getPath()));
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, s3Key);
    }


    //S3로 업로드 후 원본 파일 삭제
    private void removeOriginalFile(File targetFile) {
        if (targetFile.exists() && targetFile.delete()) {
            log.info("로컬 파일 삭제 성공: {}", targetFile.getName());
        } else {
            log.warn("로컬 파일 삭제 실패: {}", targetFile.getName());
        }
    }

    public String uploadMultipartFile(MultipartFile file, String directory) throws RuntimeException {
        try {
            // 임시 파일 생성
            String originalFileName = file.getOriginalFilename();
            String uuid = UUID.randomUUID().toString();
            String s3Key = directory + "/" + uuid + "_" + originalFileName;

            // S3에 직접 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, s3Key);
        } catch (Exception e) {
            log.error("MultipartFile S3 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage());
        }
    }

    /**
     * MultipartFile을 S3의 특정 디렉토리에 지정된 파일명으로 업로드
     * @param file 업로드할 파일
     * @param directory S3 디렉토리 경로
     * @param fileName 저장할 파일명
     * @return S3 URL
     */
    public String uploadMultipartFile(MultipartFile file, String directory, String fileName) throws RuntimeException {
        try {
            String s3Key = directory + "/" + fileName;

            // S3에 직접 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 업로드 성공: {}", s3Key);
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, s3Key);
        } catch (Exception e) {
            log.error("MultipartFile S3 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage());
        }
    }

    // ── 주소 생성과 프리사인 (#64) ──────────────────────────────────

    /**
     * 객체 주소. <b>AWS 형식을 하드코딩하지 않는다.</b>
     *
     * <p>예전에는 {@code https://{bucket}.s3.{region}.amazonaws.com/{key}} 로 만들어
     * DB 에 그대로 저장했다. 그래서 스토리지를 바꾸면 <b>저장된 데이터에 박힌 도메인</b> 때문에
     * 기존 파일을 못 찾는다. R2 는 경로 스타일이라 형식 자체가 다르다.
     */
    public String objectUrl(String key) {
        if (endpoint != null && !endpoint.isBlank()) {
            return "%s/%s/%s".formatted(endpoint.replaceAll("/$", ""), bucket, key);
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    /**
     * 저장된 주소를 <b>기간이 정해진 읽기 링크</b>로 바꾼다.
     *
     * <p>업로드가 더 이상 {@code PUBLIC_READ} 로 올리지 않는다(#64).
     * 파일은 비공개이고, 볼 자격이 확인된 뒤에만 이 링크를 발급한다.
     *
     * <p>예전 구조는 <b>URL 을 아는 사람이 곧 볼 자격이 있는 사람</b>이었다.
     * 과제 제출물과 수업 STT 전문이 그렇게 열려 있었다.
     */
    public String presignedGetUrl(String storedUrl, Duration duration) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return null;
        }
        String key = extractKey(storedUrl);
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * 저장된 주소에서 객체 키만 뽑는다.
     *
     * <p>두 형식을 모두 받는다 — 예전에 저장된 AWS 가상 호스트 주소와
     * 경로 스타일(R2) 주소. <b>기존 데이터를 마이그레이션하지 않고 읽으려면 필요하다.</b>
     */
    String extractKey(String storedUrl) {
        String path = URI.create(storedUrl).getPath();       // /bucket/key 또는 /key
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith(bucket + "/")) {                 // 경로 스타일
            path = path.substring(bucket.length() + 1);
        }
        return path;
    }
}
