package com.edu.edumeet.board.presentation.dto.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadResultDTO {

    private String uuid;
    private String fileName;
    private boolean img;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String getLink(){
        if(img){
            //이미지면 섬네일 S3 URL 반환
            return String.format("https://%s.s3.amazonaws.com/s_%s_%s",
                    bucket,uuid,fileName);
        } else{
            //일반 파일은 원본 S3 URL 반환
            return String.format("https://%s.s3.amazonaws.com/%s_%s",
                    bucket,uuid,fileName);
        }
    }


//    public String getLink(){
//
//        if(img){
//            return "s_" + uuid + "_" + fileName; //이미지면 썸네일
//        } else{
//            return uuid+"_"+fileName;
//        }
//    }
}
