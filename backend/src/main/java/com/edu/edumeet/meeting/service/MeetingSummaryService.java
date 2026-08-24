package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.dto.SummaryUploadResult;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.classroom.service.ClassAccessChecker;
import com.edu.edumeet.s3.service.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 파이썬 AI 서버가 만든 회의 요약본(MD/PDF)을 S3 에 올리고 회의에 연결한다. (#27)
 *
 * <h3>트랜잭션 경계를 왜 이렇게 잡았나</h3>
 * 이전 구현은 메서드 전체가 {@code @Transactional} 이었고 그 안에서 S3 업로드를 두 번 했다.
 * PDF 50MB 를 올리는 동안 DB 커넥션을 계속 붙잡는다. 커넥션 풀 고갈 경로다.
 *
 * <p>그래서 <b>느린 네트워크 I/O 를 트랜잭션 밖으로</b> 뺐다:
 * <pre>
 *   검증        (트랜잭션 없음)
 *   회의 조회    (짧은 읽기)
 *   S3 업로드    (트랜잭션 없음 - 여기가 오래 걸린다)
 *   DB 기록      (짧은 쓰기)
 * </pre>
 *
 * <h3>왜 TransactionTemplate 인가</h3>
 * 오케스트레이션 메서드에서 {@code this.write(...)} 를 부르면
 * <b>Spring AOP 프록시를 거치지 않아 {@code @Transactional} 이 무시된다.</b>
 * 별도 빈으로 쪼개거나 TransactionTemplate 을 쓰는 수밖에 없는데,
 * 여기서는 <b>트랜잭션 경계가 코드에 그대로 보이는</b> 쪽을 택했다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingSummaryService {

    private static final long MAX_MD_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 50L * 1024 * 1024;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final MeetingRepository meetingRepository;
    private final S3Uploader s3Uploader;
    private final ClassAccessChecker classAccessChecker;
    private final TransactionTemplate transactionTemplate;

    /**
     * 요약본을 업로드한다. 회의는 <b>이미 존재해야 한다.</b>
     *
     * <p>이전 구현은 meetingId 가 없으면 최신 회의에 덮어쓰거나 "AI 요약 미팅" 을 새로 만들었다.
     * 열린 적 없는 회의가 DB 에 생기고, 엉뚱한 회의에 요약본이 붙었다.
     * 요약본은 <b>이미 끝난 회의의 산출물</b>이므로 회의를 만들 이유가 없다.
     */
    public SummaryUploadResult uploadSummary(Long meetingId, MultipartFile markdown, MultipartFile pdf) {
        validate(markdown, pdf);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅입니다: " + meetingId));

        // 빠른 경로. 이미 있으면 S3 업로드 자체를 건너뛴다.
        // 실제 판정은 아래 쓰기 트랜잭션 안에서 한 번 더 한다 (경쟁 조건).
        if (meeting.hasSummary()) {
            log.info("요약본이 이미 있어 업로드를 건너뛴다 - meetingId={}", meetingId);
            return existingResult(meeting);
        }

        String directory = "summaries/meeting_%d_%s".formatted(meetingId, LocalDateTime.now().format(STAMP));
        String stamp = LocalDateTime.now().format(STAMP);

        // --- 트랜잭션 밖. 여기가 수십 초 걸릴 수 있다. ---
        String markdownUrl = upload(markdown, directory, "summary_%s.md".formatted(stamp));
        String pdfUrl = upload(pdf, directory, "summary_%s.pdf".formatted(stamp));

        return record(meetingId, markdownUrl, pdfUrl);
    }

    /**
     * 회의에 요약본 URL 을 기록한다. 짧은 쓰기 트랜잭션이다.
     *
     * <p>여기서 {@code hasSummary()} 를 다시 확인하는 이유:
     * 위쪽 빠른 경로와 이 시점 사이에 다른 요청이 먼저 기록했을 수 있다.
     * 트랜잭션 안에서 확인해야 <b>마지막 판정</b>이 된다.
     */
    private SummaryUploadResult record(Long meetingId, String markdownUrl, String pdfUrl) {
        return transactionTemplate.execute(status -> {
            Meeting meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅입니다: " + meetingId));

            if (meeting.hasSummary()) {
                log.info("경쟁하는 요청이 먼저 기록했다. 이번 업로드는 반영하지 않는다 - meetingId={}", meetingId);
                return existingResult(meeting);
            }

            meeting.recordSummary(markdownUrl, pdfUrl);
            log.info("요약본 기록 완료 - meetingId={}, md={}, pdf={}", meetingId, markdownUrl != null, pdfUrl != null);
            return new SummaryUploadResult(
                    meetingId, meeting.getClassRoom().getId(), markdownUrl, pdfUrl, false);
        });
    }

    private SummaryUploadResult existingResult(Meeting meeting) {
        return new SummaryUploadResult(
                meeting.getId(),
                meeting.getClassRoom().getId(),
                meeting.getSummaryMdUrl(),
                meeting.getSummaryPdfUrl(),
                true);
    }

    private String upload(MultipartFile file, String directory, String fileName) {
        if (isAbsent(file)) {
            return null;
        }
        return s3Uploader.uploadMultipartFile(file, directory, fileName);
    }

    /**
     * 요약본 조회. 이제 MD 와 PDF 를 <b>둘 다</b> 돌려준다.
     * 이전에는 s3url 단일 컬럼이라 PDF 가 MD 를 덮어썼고 MD 는 조회할 방법이 없었다.
     */
    /** 읽기 링크 유효 시간. 요약본을 여는 동안 유지되면 충분하다. */
    private static final Duration READ_LINK_TTL = Duration.ofMinutes(15);

    @Transactional(readOnly = true)
    public Optional<SummaryUploadResult> findLatestSummary(Long classId, String email) {
        // 요약본은 수업 STT 전문이다. 로그인만 하면 아무 클래스나 읽을 수 있었다. (#62)
        classAccessChecker.requireMember(classId, email);

        return meetingRepository
                .findTopByClassRoomIdAndS3urlIsNotNullOrderByStartTimeDesc(classId)
                // 저장된 주소를 그대로 주지 않는다. 요약본은 수업 STT 전문이다. (#64)
                // 파일이 비공개가 됐으므로 볼 자격이 확인된 지금 시점에만 기간 링크를 발급한다.
                .map(meeting -> new SummaryUploadResult(
                        meeting.getId(),
                        meeting.getClassRoom().getId(),
                        s3Uploader.presignedGetUrl(meeting.getSummaryMdUrl(), READ_LINK_TTL),
                        s3Uploader.presignedGetUrl(meeting.getSummaryPdfUrl(), READ_LINK_TTL),
                        true));
    }

    // --- 검증 ---

    /**
     * 이전 구현은 빈 파일이면 경고만 찍고 검증을 <b>건너뛴 뒤 그대로 업로드</b>했다.
     * 0바이트 객체가 S3 에 올라가고 DB 에 URL 이 박혔다.
     */
    private void validate(MultipartFile markdown, MultipartFile pdf) {
        boolean hasMarkdown = !isAbsent(markdown);
        boolean hasPdf = !isAbsent(pdf);

        if (!hasMarkdown && !hasPdf) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다. Markdown(.md) 또는 PDF(.pdf) 중 하나는 필요합니다.");
        }
        if (hasMarkdown) {
            requireExtensionOrType(markdown, ".md", "text/markdown", "text/x-markdown", "text/plain");
            requireSizeUnder(markdown, MAX_MD_BYTES, "Markdown", "10MB");
        }
        if (hasPdf) {
            requireExtensionOrType(pdf, ".pdf", "application/pdf");
            requireSizeUnder(pdf, MAX_PDF_BYTES, "PDF", "50MB");
        }
    }

    /** 파일 파트가 아예 없거나, 이름이 없거나, 내용이 비었으면 "없는 것" 으로 본다. */
    private boolean isAbsent(MultipartFile file) {
        return file == null || file.isEmpty() || file.getOriginalFilename() == null;
    }

    private void requireExtensionOrType(MultipartFile file, String extension, String... contentTypes) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(extension)) {
            return;
        }
        String actual = file.getContentType();
        if (actual != null) {
            for (String allowed : contentTypes) {
                if (actual.contains(allowed)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(
                "유효하지 않은 %s 파일입니다: name=%s, contentType=%s".formatted(extension, name, actual));
    }

    private void requireSizeUnder(MultipartFile file, long limit, String label, String humanLimit) {
        if (file.getSize() > limit) {
            throw new IllegalArgumentException("%s 파일이 너무 큽니다. (최대 %s)".formatted(label, humanLimit));
        }
    }
}
