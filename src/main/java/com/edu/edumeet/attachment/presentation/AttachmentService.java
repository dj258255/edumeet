package com.edu.edumeet.attachment.presentation;

import com.edu.edumeet.attachment.domain.Attachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AttachmentService {

    /**
     * 파일 업로드 처리
     * @param files 업로드할 파일 목록
     * @param domain 파일이 속한 도메인 (board, classroom 등)
     * @param referenceId 참조 ID (게시글 ID 등)
     * @return 업로드된 파일 정보 목록
     */
    List<Attachment> uploadFiles(List<MultipartFile> files, String domain, Long referenceId);
    
    /**
     * 파일 정보 조회
     * @param fileName 파일명 (UUID_원본파일명 형식)
     * @return 파일 정보와 URL이 포함된 Map
     */
    Map<String, Object> getFileInfo(String fileName);
    
    /**
     * 파일 삭제
     * @param fileName 삭제할 파일명 (UUID_원본파일명 형식)
     * @return 삭제 성공 여부
     */
    boolean removeFile(String fileName);
    
    /**
     * 도메인과 참조 ID로 파일 목록 조회
     * @param domain 파일이 속한 도메인
     * @param referenceId 참조 ID
     * @return 파일 정보 목록
     */
    List<Attachment> getFilesByReference(String domain, Long referenceId);
}
