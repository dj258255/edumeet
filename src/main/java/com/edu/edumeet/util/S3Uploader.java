package com.edu.edumeet.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import java.io.File;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    //S3 파일 업로드 하기
    public String upload(String filePath) throws RuntimeException {
        File targetFile = new File(filePath);

        String uploadImageUrl = putS3(targetFile, targetFile.getName());

        removeOriginalFile(targetFile);
        return uploadImageUrl;
    }

    //S3로 업로드
    private String putS3(File uploadFile, String fileName) throws RuntimeException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();

        s3Client.putObject(putObjectRequest, Paths.get(uploadFile.getPath()));

        return String.format("https://%s.s3.amazonaws.com/%s", bucket, fileName);
    }

    //S3로 업로드 후 원본 파일 삭제
    private void removeOriginalFile(File targetFile) {
        if (targetFile.exists() && targetFile.delete()) {
            log.info("파일 삭제 성공");
            return;
        }
        log.info("파일 삭제 실패");
    }

    public void removeS3File(String fileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 성공");
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {} - {}", fileName, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}