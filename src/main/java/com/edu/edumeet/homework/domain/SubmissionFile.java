//package com.edu.edumeet.homework.domain;
//
//import lombok.Builder;
//import lombok.Getter;
//
//@Getter
//@Builder
//public class SubmissionFile {
//    private Long id;
//    private Long submissionId;
//    private String fileName;        //UUID_원본파일명
//    private String originalFileName; // 사용자가 업로드한 원본 파일명
//    private String fileUrl;     //S3 URL
//    private Long fileSize;
//
//    //파일 크기를 MB로
//    public double getFileSizeInMB(){
//        if(fileSize == null) return 0.0;
//        return fileSize / 1024.0 / 1024.0;
//    }
//
//    // 파일 확장자 추출
//    public String getFileExtension() {
//        if (originalFileName == null || !originalFileName.contains(".")) {
//            return "";
//        }
//        return originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase();
//    }
//
//
//}
