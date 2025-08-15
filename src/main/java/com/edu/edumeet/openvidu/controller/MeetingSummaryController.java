package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.openvidu.service.MeetingSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meeting")
@RequiredArgsConstructor
@Log4j2
public class MeetingSummaryController {

    private final MeetingSummaryService meetingSummaryService;

    /**
     * 파이썬 서버에서 요약본 파일 업로드 받기
     * URL 패턴: /api/v1/meeting/summary/{classId}/upload
     */
    @PostMapping("/summary/{classId}/upload")
    public ResponseEntity<Map<String, Object>> uploadSummary(
            @PathVariable("classId") Long classId,
            @RequestParam("class_id") String classIdParam,
            @RequestParam(value = "meeting_id", required = false) String meetingIdParam,
            @RequestParam(value = "summary_md", required = false) MultipartFile summaryMd,
            @RequestParam(value = "summary_pdf", required = false) MultipartFile summaryPdf) {

        log.info("📝 파이썬에서 요약본 업로드 요청 - classId: {}, meetingId: {}", classId, meetingIdParam);
        
        // 파라미터 디버깅
        log.info("🔍 파라미터 디버깅:");
        log.info("  - classIdParam: '{}'", classIdParam);
        log.info("  - meetingIdParam: '{}'", meetingIdParam);
        log.info("  - summaryMd: {}", summaryMd != null ? "NOT NULL (size: " + summaryMd.getSize() + ", name: " + summaryMd.getOriginalFilename() + ")" : "NULL");
        log.info("  - summaryPdf: {}", summaryPdf != null ? "NOT NULL (size: " + summaryPdf.getSize() + ", name: " + summaryPdf.getOriginalFilename() + ")" : "NULL");

        Map<String, Object> response = new HashMap<>();

        try {
            // meeting_id가 있으면 사용, 없으면 null
            Long meetingId = null;
            if (meetingIdParam != null && !meetingIdParam.trim().isEmpty()) {
                try {
                    meetingId = Long.parseLong(meetingIdParam.trim());
                } catch (NumberFormatException e) {
                    log.warn("❌ meeting_id 파싱 실패: {}", meetingIdParam);
                }
            }

            // 파일 검증 - MD 또는 PDF 중 하나는 있어야 함 (빈 파일도 일단 허용)
            boolean hasMdFile = summaryMd != null && summaryMd.getOriginalFilename() != null;
            boolean hasPdfFile = summaryPdf != null && summaryPdf.getOriginalFilename() != null;
            
            log.info("📋 파일 검증 결과: hasMdFile={}, hasPdfFile={}", hasMdFile, hasPdfFile);
            
            if (!hasMdFile && !hasPdfFile) {
                response.put("success", false);
                response.put("message", "업로드할 파일이 없습니다. Markdown(.md) 또는 PDF(.pdf) 파일 중 하나는 필요합니다.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 업로드되는 파일 정보 로깅 (크기 포함)
            String fileInfo = String.format("업로드 파일: %s%s", 
                hasMdFile ? String.format("MD(%d bytes) ", summaryMd.getSize()) : "", 
                hasPdfFile ? String.format("PDF(%d bytes)", summaryPdf.getSize()) : "");
            log.info("📁 {}", fileInfo);
            
            log.info("🔍 서비스 호출 전 - meetingSummaryService: {}", meetingSummaryService != null ? "OK" : "NULL");

            // 요약본 업로드 처리
            Map<String, String> uploadResult = meetingSummaryService.uploadSummaryFiles(
                    classId, meetingId, summaryMd, summaryPdf);
                    
            log.info("✅ 서비스 호출 완료 - uploadResult: {}", uploadResult);

            response.put("success", true);
            response.put("message", "파일 업로드 및 DB 업데이트 완료");
            response.put("class_id", classId);
            response.put("meeting_id", uploadResult.get("meeting_id"));
            response.put("uploaded_files", uploadResult);
            
            // 어떤 파일이 업로드되었는지 응답에 포함
            response.put("file_types", 
                String.format("%s%s", 
                    uploadResult.containsKey("summary_md_url") ? "MD " : "",
                    uploadResult.containsKey("summary_pdf_url") ? "PDF" : "").trim());
            response.put("primary_file", 
                uploadResult.get("s3_url") != null && uploadResult.get("s3_url").contains(".pdf") ? "PDF" : "MD");

            log.info("✅ 요약본 업로드 성공 - classId: {}, 결과: {}", classId, uploadResult);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ 요약본 업로드 실패 - 잘못된 요청: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "잘못된 요청: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("❌ 요약본 업로드 실패 - classId: {}", classId, e);
            response.put("success", false);
            response.put("error", "파일 업로드 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 특정 클래스의 요약본 조회
     */
    @GetMapping("/summary/{classId}")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable("classId") Long classId) {

        log.info("📖 요약본 조회 요청 - classId: {}", classId);

        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, String> summaryInfo = meetingSummaryService.getSummaryInfo(classId);

            response.put("success", true);
            response.put("data", summaryInfo);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ 요약본 조회 실패 - 잘못된 요청: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("❌ 요약본 조회 실패 - classId: {}", classId, e);
            response.put("success", false);
            response.put("message", "서버 오류: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
