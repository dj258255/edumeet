package com.edu.edumeet.meeting.controller;

import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.meeting.service.MeetingFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meeting/files")
@RequiredArgsConstructor
@Log4j2
public class MeetingDownloadController {

    private final MeetingFileService meetingFileService;

    @Operation(summary = "미팅 녹화 파일 다운로드",
            description = "미팅 ID로 녹화 파일을 S3에서 다운로드합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "다운로드 성공"),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/download/{meetingId}")
    public ResponseEntity<Resource> downloadMeetingFile(
            @Parameter(description = "미팅 ID", required = true)
            @PathVariable Long meetingId,
            @AuthenticationPrincipal SecurityMember member) {

        log.info("미팅 파일 다운로드 요청 - 미팅 ID: {}, 사용자: {}", meetingId, member.getEmail());

        try {
            // 파일 다운로드 스트림과 메타데이터 조회
            MeetingFileService.FileDownloadInfo downloadInfo =
                    meetingFileService.downloadMeetingFile(meetingId, member.getMemberId());

            // 파일명 인코딩 (한글 파일명 지원) - RFC 5987 표준 준수
            String encodedFileName = URLEncoder.encode(downloadInfo.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // ASCII 호환 파일명 생성 (특수문자 제거)
            String asciiFileName = downloadInfo.getFileName()
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            // HTTP 헤더 설정 - RFC 2616과 RFC 5987 표준 모두 지원
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                            asciiFileName, encodedFileName));
            headers.add(HttpHeaders.CONTENT_TYPE, downloadInfo.getContentType());
            headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(downloadInfo.getContentLength()));

            // 추가 헤더 설정 (한글 지원)
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            // InputStreamResource로 응답
            InputStreamResource resource = new InputStreamResource(downloadInfo.getInputStream());

            log.info("미팅 파일 다운로드 시작 - 파일명: {}, 크기: {} bytes",
                    downloadInfo.getFileName(), downloadInfo.getContentLength());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (SecurityException e) {
            log.warn("미팅 파일 다운로드 권한 없음 - 미팅 ID: {}, 사용자: {}", meetingId, member.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.warn("미팅 파일 다운로드 실패 - 미팅 ID: {}, 오류: {}", meetingId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("미팅 파일 다운로드 중 오류 발생 - 미팅 ID: {}", meetingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "미팅 파일 정보 조회",
            description = "미팅 녹화 파일의 메타데이터를 조회합니다.")
    @GetMapping("/info/{meetingId}")
    public ResponseEntity<Map<String, Object>> getMeetingFileInfo(
            @Parameter(description = "미팅 ID", required = true)
            @PathVariable Long meetingId,
            @AuthenticationPrincipal SecurityMember member) {

        log.info("미팅 파일 정보 조회 요청 - 미팅 ID: {}, 사용자: {}", meetingId, member.getEmail());

        try {
            MeetingFileService.FileInfo fileInfo =
                    meetingFileService.getMeetingFileInfo(meetingId, member.getMemberId());

            Map<String, Object> response = new HashMap<>();
            response.put("meetingId", meetingId);
            response.put("fileName", fileInfo.getFileName());
            response.put("fileSize", fileInfo.getFileSize());
            response.put("contentType", fileInfo.getContentType());
            response.put("downloadUrl", fileInfo.getDownloadUrl());
            response.put("available", fileInfo.isAvailable());

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.warn("미팅 파일 정보 조회 권한 없음 - 미팅 ID: {}, 사용자: {}", meetingId, member.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.warn("미팅 파일 정보 조회 실패 - 미팅 ID: {}, 오류: {}", meetingId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("미팅 파일 정보 조회 중 오류 발생 - 미팅 ID: {}", meetingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "미팅 파일 존재 여부 확인",
            description = "미팅에 녹화 파일이 있는지 확인합니다.")
    @GetMapping("/exists/{meetingId}")
    public ResponseEntity<Map<String, Boolean>> checkMeetingFileExists(
            @Parameter(description = "미팅 ID", required = true)
            @PathVariable Long meetingId,
            @AuthenticationPrincipal SecurityMember member) {

        try {
            boolean exists = meetingFileService.isMeetingFileExists(meetingId, member.getMemberId());

            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", exists);

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("미팅 파일 존재 여부 확인 중 오류 발생 - 미팅 ID: {}", meetingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}