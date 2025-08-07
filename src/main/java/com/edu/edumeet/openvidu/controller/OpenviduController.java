package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.openvidu.service.OpenviduService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetingroom")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OpenviduController {

    private final OpenviduService openviduService;

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> createToken(@RequestBody Map<String, String> params) {
        String roomName = params.get("roomName");
        String participantName = params.get("participantName");

        if (roomName == null || participantName == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "roomName and participantName are required"
            ));
        }

        Map<String, Object> token = openviduService.createToken(roomName, participantName);
        return ResponseEntity.ok(token);
    }

    @GetMapping("/room/{roomName}")
    public ResponseEntity<Map<String, Object>> getRoomInfo(@PathVariable String roomName) {
        Map<String, Object> roomInfo = openviduService.getRoomInfo(roomName);

        if (roomInfo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(roomInfo);
    }
}