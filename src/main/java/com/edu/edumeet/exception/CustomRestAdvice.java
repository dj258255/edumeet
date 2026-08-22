package com.edu.edumeet.exception;


import com.edu.edumeet.openvidu.exception.LiveKitUnavailableException;
import org.springframework.security.access.AccessDeniedException;
import com.edu.edumeet.openvidu.exception.SessionCapacityExceededException;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;


@RestControllerAdvice
@Log4j2
public class CustomRestAdvice {
    // Valid 과정에서 문제가 발생하면 처리할 수 있도록
// ResControllerAdvice를 설계하자
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.EXPECTATION_FAILED)
    public ResponseEntity<Map<String, String>> handleBindException(BindException e){
        log.error(e);

        Map<String, String> errorMap = new HashMap<>();

        if(e.hasErrors()){
            BindingResult bindingResult = e.getBindingResult();

            bindingResult.getFieldErrors().forEach(fieldError -> {
                errorMap.put(fieldError.getField(), fieldError.getCode());
            });
        }

        return ResponseEntity.badRequest().body(errorMap);
    }


    //서버의 문제가 아니라 데이터의 문제가 있다고 전송
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.EXPECTATION_FAILED)
    public ResponseEntity<Map<String, String>> handleFKException(Exception e) {

        log.error(e);

        Map<String, String> errorMap = new HashMap<>();

        errorMap.put("time", "" + System.currentTimeMillis());
        errorMap.put("message", "constraint fails");
        return ResponseEntity.badRequest().body(errorMap);
    }


    //데이터가 존재하지 않는 경우의 처리
    @ExceptionHandler({NoSuchElementException.class,
                        EmptyResultDataAccessException.class})    //존재하지 않는 번호의 삭제 예외
    @ResponseStatus(HttpStatus.EXPECTATION_FAILED)
    public ResponseEntity<Map<String, String>> handleNoSuchElement(Exception e){

        log.error(e);

        Map<String, String> errorMap = new HashMap<>();

        errorMap.put("time", "" + System.currentTimeMillis());
        errorMap.put("msg" , "No Such Element Exception");
        return ResponseEntity.badRequest().body(errorMap);
    }

    /**
     * LiveKit 에 닿지 못했다. 우리 잘못이 아니라 의존 시스템의 장애이므로 503 이다.
     *
     * <p>"방이 없다"(404)와 반드시 구분한다. 404 로 뭉뚱그리면 클라이언트는
     * "삭제됐구나"로 오해하고, 운영에서는 LiveKit 장애가 보이지 않는다.
     */
    @ExceptionHandler(LiveKitUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<Map<String, String>> handleLiveKitUnavailable(LiveKitUnavailableException e) {
        log.error("LiveKit 사용 불가", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "LIVEKIT_UNAVAILABLE",
                             "message", "화상 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    /**
     * 정원이 찼다. 클라이언트 요청 자체는 올바르므로 500 이 아니라 409 다.
     *
     * <p>핸들러가 없어 500 으로 나가고 있었다. 부하 측정에서 정원 초과와 진짜 오류를
     * 구분할 수 없어 드러났다. (#17)
     */
    @ExceptionHandler(SessionCapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<Map<String, String>> handleSessionCapacity(SessionCapacityExceededException e) {
        log.info("정원 초과 입장 시도: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "SESSION_CAPACITY_EXCEEDED", "message", e.getMessage()));
    }

    /**
     * 권한 없음. 인증은 됐지만 그 리소스를 만질 자격이 없다 → 403.
     *
     * <p>401(인증 필요)과 구분한다. 401 로 뭉뚱그리면 클라이언트가 "다시 로그인하면 되겠지"로
     * 오해한다.
     */
    /**
     * 잘못된 입력은 400 이다. (#27)
     *
     * <p>이전에는 서비스가 {@code catch (Exception e) -> throw new RuntimeException(...)} 으로
     * 전부 재포장했다. IllegalArgumentException 도 RuntimeException 을 상속하므로 여기 걸려
     * 컨트롤러의 400 분기는 <b>절대 실행되지 않았다.</b> 없는 classId 를 보내도 500 이 나갔다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {

        log.warn("잘못된 요청: {}", e.getMessage());

        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("time", "" + System.currentTimeMillis());
        errorMap.put("msg", e.getMessage());
        return ResponseEntity.badRequest().body(errorMap);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        log.warn("권한 없는 접근: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ACCESS_DENIED", "message", e.getMessage()));
    }
}
