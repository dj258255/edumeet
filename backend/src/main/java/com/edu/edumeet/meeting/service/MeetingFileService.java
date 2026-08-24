package com.edu.edumeet.meeting.service;

import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.s3.service.S3Downloader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class MeetingFileService {

    private final MeetingRepository meetingRepository;
    private final ClassMemberRepository classMemberRepository;
    private final S3Downloader s3Downloader;

    /**
     * 미팅 녹화 파일 다운로드
     */
    public FileDownloadInfo downloadMeetingFile(Long meetingId, Long memberId) {
        // 미팅 조회 및 권한 확인
        Meeting meeting = getMeetingWithPermissionCheck(meetingId, memberId);

        // S3 URL이 없는 경우
        if (meeting.getS3url() == null || meeting.getS3url().trim().isEmpty()) {
            throw new IllegalArgumentException("해당 미팅에 녹화 파일이 없습니다.");
        }

        try {
            // S3에서 파일 다운로드
            S3Downloader.S3FileInfo s3FileInfo = s3Downloader.downloadFile(meeting.getS3url());

            // 지원하는 파일 타입인지 검증
            validateSupportedFileType(s3FileInfo.getContentType());

            // 실제 파일의 Content-Type을 기반으로 파일명 생성
            String fileName = generateFileName(meeting, s3FileInfo.getContentType());

            return FileDownloadInfo.builder()
                    .inputStream(s3FileInfo.getInputStream())
                    .fileName(fileName)
                    .contentType(s3FileInfo.getContentType())
                    .contentLength(s3FileInfo.getContentLength())
                    .build();

        } catch (Exception e) {
            log.error("S3 파일 다운로드 실패 - 미팅 ID: {}, S3 URL: {}", meetingId, meeting.getS3url(), e);
            throw new RuntimeException("파일 다운로드에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 미팅 파일 정보 조회
     */
    public FileInfo getMeetingFileInfo(Long meetingId, Long memberId) {
        Meeting meeting = getMeetingWithPermissionCheck(meetingId, memberId);

        if (meeting.getS3url() == null || meeting.getS3url().trim().isEmpty()) {
            return FileInfo.builder()
                    .fileName(null)
                    .fileSize(0L)
                    .contentType(null)
                    .downloadUrl(null)
                    .available(false)
                    .build();
        }

        try {
            S3Downloader.S3FileMetadata metadata = s3Downloader.getFileMetadata(meeting.getS3url());
            String fileName = generateFileName(meeting, metadata.getContentType());

            return FileInfo.builder()
                    .fileName(fileName)
                    .fileSize(metadata.getContentLength())
                    .contentType(metadata.getContentType())
                    .downloadUrl(meeting.getS3url())
                    .available(true)
                    .build();

        } catch (Exception e) {
            log.error("S3 파일 메타데이터 조회 실패 - 미팅 ID: {}, S3 URL: {}", meetingId, meeting.getS3url(), e);
            return FileInfo.builder()
                    .fileName(null)
                    .fileSize(0L)
                    .contentType(null)
                    .downloadUrl(meeting.getS3url())
                    .available(false)
                    .build();
        }
    }

    /**
     * 미팅 파일 존재 여부 확인
     */
    public boolean isMeetingFileExists(Long meetingId, Long memberId) {
        Meeting meeting = getMeetingWithPermissionCheck(meetingId, memberId);

        if (meeting.getS3url() == null || meeting.getS3url().trim().isEmpty()) {
            return false;
        }

        try {
            return s3Downloader.isFileExists(meeting.getS3url());
        } catch (Exception e) {
            log.error("S3 파일 존재 여부 확인 실패 - 미팅 ID: {}, S3 URL: {}", meetingId, meeting.getS3url(), e);
            return false;
        }
    }

    /**
     * 미팅 조회 및 권한 확인
     */
    private Meeting getMeetingWithPermissionCheck(Long meetingId, Long memberId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅입니다."));

        // 권한 확인: 클래스 생성자이거나 클래스 멤버여야 함
        Long classRoomId = meeting.getClassRoom().getId();

        // 클래스 생성자인지 확인
        if (meeting.getClassRoom().getMember().getId().equals(memberId)) {
            return meeting;
        }

        // 클래스 멤버인지 확인
        boolean isMember = classMemberRepository.existsByClassRoomIdAndMemberId(classRoomId, memberId);
        if (!isMember) {
            throw new SecurityException("해당 미팅 파일에 접근할 권한이 없습니다.");
        }

        return meeting;
    }

    /**
     * 파일명 생성 (미팅 제목 + 날짜 + 올바른 확장자) - 안전한 파일명으로 생성
     */
    private String generateFileName(Meeting meeting, String contentType) {
        // 1. 제목에서 안전하지 않은 문자 제거 (파일시스템 호환성)
        String safeTitle = meeting.getTitle()
                .replaceAll("[<>:\"/\\\\|?*]", "") // 파일시스템 금지 문자 제거
                .replaceAll("\\s+", "_") // 공백을 언더스코어로
                .trim();

        // 2. 제목이 너무 길면 자르기 (최대 50자)
        if (safeTitle.length() > 50) {
            safeTitle = safeTitle.substring(0, 50);
        }

        // 3. 날짜와 시간 포맷팅
        String date = meeting.getStartTime().toLocalDate().toString(); // yyyy-MM-dd
        String time = meeting.getStartTime().toLocalTime().toString().substring(0, 5).replace(":", "-"); // HH-mm

        // 4. Content-Type에서 파일 확장자 결정
        String extension = getFileExtensionFromContentType(contentType);

        // 5. 최종 파일명 생성
        return String.format("%s_%s_%s%s", safeTitle, date, time, extension);
    }

    /**
     * Content-Type에서 적절한 파일 확장자 반환 (md, pdf, jpeg만 지원)
     */
    private String getFileExtensionFromContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return ".bin"; // 알 수 없는 파일 타입
        }

        // Content-Type을 소문자로 변환하여 매칭
        String lowerContentType = contentType.toLowerCase();

        // 지원하는 파일 타입들만 처리
        if (lowerContentType.contains("image/jpeg") || lowerContentType.contains("image/jpg")) {
            return ".jpeg";
        }
        if (lowerContentType.contains("application/pdf")) {
            return ".pdf";
        }
        if (lowerContentType.contains("text/markdown") || lowerContentType.contains("text/x-markdown")) {
            return ".md";
        }

        // 지원하지 않는 파일 타입
        log.warn("지원하지 않는 Content-Type: {}", contentType);
        return ".unsupported";
    }

    /**
     * 지원하는 파일 타입인지 검증
     */
    private void validateSupportedFileType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            throw new IllegalArgumentException("파일 타입을 확인할 수 없습니다.");
        }

        String lowerContentType = contentType.toLowerCase();

        // 지원하는 파일 타입 목록
        boolean isSupported = lowerContentType.contains("image/jpeg") ||
                lowerContentType.contains("image/jpg") ||
                lowerContentType.contains("application/pdf") ||
                lowerContentType.contains("text/markdown") ||
                lowerContentType.contains("text/x-markdown");

        if (!isSupported) {
            throw new IllegalArgumentException(
                    String.format("지원하지 않는 파일 타입입니다. (현재: %s, 지원 타입: .jpeg, .pdf, .md)", contentType)
            );
        }
    }

    /**
     * 파일 다운로드 정보
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class FileDownloadInfo {
        private InputStream inputStream;
        private String fileName;
        private String contentType;
        private long contentLength;
    }

    /**
     * 파일 정보
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class FileInfo {
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String downloadUrl;
        private boolean available;
    }
}