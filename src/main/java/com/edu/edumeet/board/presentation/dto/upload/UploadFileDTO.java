package com.edu.edumeet.board.presentation.dto.upload;


import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

//컨트롤러에서 파라미터를 멀티파트파일로 지정하면 간단한 파일 업로드는 가능
//근데 스웨거에서 테스트하기 불편하니 별도의 DTO로 선언.
@Data
public class UploadFileDTO {

    private List<MultipartFile> files;
}
