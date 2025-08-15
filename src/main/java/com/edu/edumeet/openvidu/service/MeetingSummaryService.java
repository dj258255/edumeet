package com.edu.edumeet.openvidu.service;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.openvidu.domain.Meeting;
import com.edu.edumeet.openvidu.repository.MeetingRepository;
import com.edu.edumeet.s3.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class MeetingSummaryService {

    private final MeetingRepository meetingRepository;
    private final ClassRepository classRepository;
    private final S3Uploader s3Uploader;

    /**
     * 파이썬에서 보낸 요약본 파일들을 S3에 업로드하고 Meeting의 s3url에 저장
     * 파이썬 코드의 send_summary_to_api 함수 요청을 처리
     */
    @Transactional
    public Map<String, String> uploadSummaryFiles(Long classId, Long meetingId,
                                                  MultipartFile summaryMd, MultipartFile summaryPdf) {

        log.info("📤 요약본 파일 업로드 시작 - classId: {}, meetingId: {}", classId, meetingId);
        
        try {
            // 1. 클래스 존재 여부 확인
            log.info("🔍 ClassRepository 확인 중...");
            ClassRoom classRoom = classRepository.findById(classId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클래스입니다: " + classId));
            log.info("✅ 클래스 조회 성공: {}", classRoom.getTitle());

            // 2. Meeting 조회 또는 생성
            log.info("🔍 Meeting 조회/생성 중...");
            Meeting meeting = findOrCreateMeeting(classId, meetingId, classRoom);
            log.info("✅ Meeting 준비 완료: {}", meeting.getId());

            Map<String, String> result = new HashMap<>();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String s3Directory = String.format("summaries/class_%s/meeting_%s_%s", 
                                              classId, meeting.getId(), timestamp);
            log.info("📁 S3 디렉토리: {}", s3Directory);

            String primaryUrl = null; // s3url 필드에 저장할 주요 URL (PDF 우선, 없으면 MD)
            boolean hasUploadedFile = false;

            // 3. Markdown 파일 업로드
            if (summaryMd != null && summaryMd.getOriginalFilename() != null) {
                log.info("🔍 Markdown 파일 업로드 시작... (size: {})", summaryMd.getSize());
                
                // 빈 파일 경고만 출력하고 계속 진행
                if (summaryMd.isEmpty()) {
                    log.warn("⚠️ Markdown 파일이 비어있지만 계속 진행합니다.");
                } else {
                    validateMarkdownFile(summaryMd);
                }
                
                String mdFileName = String.format("summary_%s.md", timestamp);
                String mdUrl = s3Uploader.uploadMultipartFile(summaryMd, s3Directory, mdFileName);
                result.put("summary_md_url", mdUrl);
                primaryUrl = mdUrl; // MD를 일단 primary로 설정
                hasUploadedFile = true;
                log.info("✅ Markdown 파일 S3 업로드 완료: {}", mdUrl);
            }

            // 4. PDF 파일 업로드 (우선순위 높음)
            if (summaryPdf != null && summaryPdf.getOriginalFilename() != null) {
                log.info("🔍 PDF 파일 업로드 시작... (size: {})", summaryPdf.getSize());
                
                // 빈 파일 경고만 출력하고 계속 진행
                if (summaryPdf.isEmpty()) {
                    log.warn("⚠️ PDF 파일이 비어있지만 계속 진행합니다.");
                } else {
                    validatePdfFile(summaryPdf);
                }
                
                String pdfFileName = String.format("summary_%s.pdf", timestamp);
                String pdfUrl = s3Uploader.uploadMultipartFile(summaryPdf, s3Directory, pdfFileName);
                result.put("summary_pdf_url", pdfUrl);
                primaryUrl = pdfUrl; // PDF가 있으면 항상 primaryUrl로 덮어쓰기 (우선순위)
                hasUploadedFile = true;
                log.info("✅ PDF 파일 S3 업로드 완료: {}", pdfUrl);
            }

            // 업로드된 파일이 하나도 없으면 에러
            if (!hasUploadedFile) {
                throw new IllegalArgumentException("업로드할 파일이 없습니다. MD 또는 PDF 파일 중 하나는 필요합니다.");
            }

            // 5. Meeting 엔티티의 s3url 필드 업데이트 (primaryUrl은 항상 존재)
            log.info("🔍 Meeting 엔티티 업데이트 중...");
            meeting.changeS3Url(primaryUrl);
            meetingRepository.save(meeting);
            log.info("✅ Meeting s3url 업데이트 완료 - meetingId: {}, s3url: {}", 
                    meeting.getId(), primaryUrl);
            
            // 업로드된 파일 정보 로깅
            StringBuilder uploadInfo = new StringBuilder("업로드된 파일: ");
            if (result.containsKey("summary_md_url")) {
                uploadInfo.append("MD ");
            }
            if (result.containsKey("summary_pdf_url")) {
                uploadInfo.append("PDF ");
            }
            uploadInfo.append("(Primary: ").append(primaryUrl.contains(".pdf") ? "PDF" : "MD").append(")");
            log.info(uploadInfo.toString());

            // 6. 응답 데이터 구성
            result.put("class_id", classId.toString());
            result.put("meeting_id", meeting.getId().toString());
            result.put("s3_url", primaryUrl);
            result.put("uploaded_at", LocalDateTime.now().toString());

            log.info("🎉 요약본 업로드 완료 - classId: {}, meetingId: {}, primaryUrl: {}, uploadedFiles: {}",
                    classId, meeting.getId(), primaryUrl, 
                    result.keySet().stream()
                        .filter(key -> key.endsWith("_url"))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none"));

            return result;

        } catch (Exception e) {
            log.error("❌ 요약본 업로드 실패 - classId: {}, meetingId: {}", classId, meetingId, e);
            throw new RuntimeException("요약본 업로드 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Meeting 조회 또는 생성
     * meetingId가 있으면 해당 Meeting 조회, 없으면 새로 생성
     */
    private Meeting findOrCreateMeeting(Long classId, Long meetingId, ClassRoom classRoom) {
        if (meetingId != null) {
            // meetingId가 주어진 경우 해당 Meeting 조회
            Meeting meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅입니다: " + meetingId));

            // 미팅이 해당 클래스에 속하는지 확인
            if (!meeting.getClassRoom().getId().equals(classId)) {
                throw new IllegalArgumentException("미팅이 해당 클래스에 속하지 않습니다.");
            }
            
            log.info("📋 기존 Meeting 사용 - meetingId: {}", meetingId);
            return meeting;
        } else {
            // meetingId가 없으면 해당 클래스의 최신 미팅 조회 또는 새로 생성
            Optional<Meeting> latestMeeting = meetingRepository.findTopByClassRoomIdOrderByStartTimeDesc(classId);
            
            if (latestMeeting.isPresent()) {
                Meeting meeting = latestMeeting.get();
                log.info("📋 최신 Meeting 사용 - meetingId: {}", meeting.getId());
                return meeting;
            } else {
                // 미팅이 없으면 새로 생성
                Meeting newMeeting = Meeting.builder()
                        .classRoom(classRoom)
                        .title("AI 요약 미팅 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                        .description("파이썬 AI 서버에서 생성된 요약본")
                        .startTime(LocalDateTime.now())
                        .build();
                
                Meeting savedMeeting = meetingRepository.save(newMeeting);
                log.info("📋 새 Meeting 생성 - meetingId: {}", savedMeeting.getId());
                return savedMeeting;
            }
        }
    }

    /**
     * 클래스의 요약본 정보 조회
     */
    public Map<String, String> getSummaryInfo(Long classId) {

        ClassRoom classRoom = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클래스입니다: " + classId));

        Map<String, String> result = new HashMap<>();
        result.put("class_id", classId.toString());
        result.put("class_name", classRoom.getTitle());

        // 해당 클래스의 요약본이 있는 최신 미팅 조회 (s3url 기준)
        Optional<Meeting> latestMeetingWithSummary = meetingRepository
                .findTopByClassRoomIdAndS3urlIsNotNullOrderByStartTimeDesc(classId);

        if (latestMeetingWithSummary.isPresent()) {
            Meeting meeting = latestMeetingWithSummary.get();
            result.put("latest_meeting_id", meeting.getId().toString());
            result.put("latest_meeting_title", meeting.getTitle());
            result.put("latest_meeting_s3_url", meeting.getS3url());
            result.put("latest_meeting_start_time", meeting.getStartTime().toString());
        } else {
            result.put("message", "요약본이 있는 미팅이 없습니다.");
        }

        return result;
    }

    /**
     * Markdown 파일 검증
     */
    private void validateMarkdownFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Markdown 파일이 비어있습니다.");
        }

        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        log.debug("📝 Markdown 파일 검증 - fileName: {}, contentType: {}, size: {}", 
                 fileName, contentType, file.getSize());

        // Content-Type 또는 파일 확장자로 검증
        boolean isValidMd = (contentType != null &&
                (contentType.contains("text/markdown") ||
                        contentType.contains("text/x-markdown") ||
                        contentType.contains("text/plain"))) ||
                (fileName != null && fileName.toLowerCase().endsWith(".md"));

        if (!isValidMd) {
            throw new IllegalArgumentException("유효하지 않은 Markdown 파일입니다.");
        }

        // 파일 크기 제한 (10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Markdown 파일 크기가 너무 큽니다. (최대 10MB)");
        }
    }

    /**
     * PDF 파일 검증
     */
    private void validatePdfFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("PDF 파일이 비어있습니다.");
        }

        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        log.debug("📄 PDF 파일 검증 - fileName: {}, contentType: {}, size: {}", 
                 fileName, contentType, file.getSize());

        // Content-Type 또는 파일 확장자로 검증
        boolean isValidPdf = (contentType != null && contentType.contains("application/pdf")) ||
                (fileName != null && fileName.toLowerCase().endsWith(".pdf"));

        if (!isValidPdf) {
            throw new IllegalArgumentException("유효하지 않은 PDF 파일입니다.");
        }

        // 파일 크기 제한 (50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("PDF 파일 크기가 너무 큽니다. (최대 50MB)");
        }
    }
}
