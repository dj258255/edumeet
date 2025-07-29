package com.edu.edumeet.util;


import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Log4j2
public class S3UploaderTest {

    @Autowired
    private S3Uploader s3Uploader;

    @Test
    public void 테스트_원본파일제거_S3업로드(){
        try{
            String filePath = "/Users/beomsu/Pictures/test.png";
            String uploadImageUrl = s3Uploader.upload(filePath);
            log.info(uploadImageUrl);
        } catch (Exception e){
            log.error(e.getMessage());
        }
    }
}
