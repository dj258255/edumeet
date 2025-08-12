package com.edu.edumeet.s3.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3Uploader {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

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
                    .acl(ObjectCannedACL.PUBLIC_READ)
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
        return String.format("https://%s.s3.%s.amazonaws.com/%s_%s", bucket, region, uuid, fileName);
    }

    //썸네일 url 생성(이미지인 경우만)
    public String getThumbnailUrl(String uuid, String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/s_%s_%s", bucket, region, uuid, fileName);
    }

    //도메인별 원본 파일 생성
    public String getDomainOriginalUrl(String domain, String uuid, String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s/%s_%s", bucket, region, domain, uuid, fileName);
    }

    //도메인별 썸네일 url 생성
    public String getDomainThumbnailUrl(String domain, String uuid, String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s/s_%s_%s", bucket, region, domain, uuid, fileName);
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
                .acl(ObjectCannedACL.PUBLIC_READ)
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
}