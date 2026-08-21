package com.edu.edumeet.perf;

import com.edu.edumeet.attachment.dto.AttachmentAdapter;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.dto.AssignmentDTO;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.repository.SubmissionRepository;
import com.edu.edumeet.homework.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 과제 목록 조회의 N+1 개선 전/후를 같은 조건에서 비교한다.
 *
 * <p>왜 별도 엔드포인트인가 — 운영 엔드포인트는 인증을 요구하고 로그인 사용자의
 * 제출 상태를 섞어 넣는다. 부하 도구에서 토큰을 돌리는 것도, 사용자별 분기가 섞이는 것도
 * 측정 대상을 흐린다. 여기서는 조회 전략만 바꾸고 나머지는 동일하게 둔다.
 *
 * <p>왜 한 프로세스인가 — 두 전략을 서로 다른 실행에서 재면 JIT 워밍업, 버퍼 풀 상태,
 * 머신 부하가 달라진다. 같은 JVM 에서 같은 데이터로 번갈아 재야 차이가 조회 전략에서
 * 온 것이라고 말할 수 있다.
 *
 * <p>단 Hibernate 의 {@code default_batch_fetch_size} 는 요청 단위로 못 바꾸는 전역
 * 설정이라 그것만 앱을 두 번 띄워 비교한다. (scripts/run-benchmark.sh)
 *
 * <p>perf 프로파일 전용이다. 운영에서는 이 빈이 생성되지 않아 URL 자체가 없다.
 */
@Profile("perf")
@RestController
@RequestMapping("/api/perf")
@RequiredArgsConstructor
public class AssignmentBenchmarkController {

    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AttachmentAdapter attachmentAdapter;

    @GetMapping("/assignments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AssignmentDTO>> list(
            @RequestParam Long classId,
            @RequestParam(defaultValue = "batch") String strategy) {

        QueryCountInspector.reset();

        List<AssignmentDTO> result = "naive".equals(strategy)
                ? naive(classId)
                : assignmentService.getAssignmentsByClassId(classId);

        return ResponseEntity.ok()
                .header("X-Query-Count", String.valueOf(QueryCountInspector.count()))
                .header("X-Strategy", strategy)
                .header("X-Result-Size", String.valueOf(result.size()))
                .body(result);
    }

    /**
     * 개선 전 구현. 커밋 314e60f 이전의 AssignmentServiceImpl 코드를 그대로 옮겼다.
     *
     * <p>과제마다 제출물을 따로 조회하므로 쿼리가 과제 수에 비례한다.
     */
    private List<AssignmentDTO> naive(Long classId) {
        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(classId);

        return assignments.stream()
                .map(assignment -> {
                    List<Submission> submissions =
                            submissionRepository.findByAssignmentIdWithSubmissionFiles(assignment.getId());
                    return assignmentService.domainToDtoWithSubmissionFiles(
                            assignment, submissions, attachmentAdapter);
                })
                .collect(Collectors.toList());
    }
}
