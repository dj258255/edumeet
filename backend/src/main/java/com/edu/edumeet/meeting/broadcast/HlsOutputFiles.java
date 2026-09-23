package com.edu.edumeet.meeting.broadcast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** HLS 출력 파일의 시작 전 정리와 세션 단위 종료 정리를 한곳에 둔다. */
final class HlsOutputFiles {

    private static final String PLAYLIST = "live.m3u8";

    private HlsOutputFiles() {
    }

    /** 새 송출 전에는 어느 이전 세션의 HLS 산출물이든 지운다. */
    static void removeAll(Path dir) throws IOException {
        if (Files.notExists(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(HlsOutputFiles::isHlsOutput).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * 끝난 세션의 init·segment만 지운다. live.m3u8 은 현재 파일이 그 세션을 가리킬 때만 지운다.
     * 늦은 종료 정리가 새 세션의 재생 파일을 지우지 않게 하는 경계다.
     */
    static void removeSession(Path dir, String sessionId) throws IOException {
        if (Files.notExists(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(path -> belongsToSession(path, sessionId)).toList()) {
                Files.deleteIfExists(file);
            }
        }

        Path playlist = dir.resolve(PLAYLIST);
        if (Files.isRegularFile(playlist) && playlistReferencesSession(playlist, sessionId)) {
            Files.deleteIfExists(playlist);
        }
    }

    private static boolean isHlsOutput(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file)
                && (PLAYLIST.equals(name) || name.startsWith("init_") || name.startsWith("seg_"));
    }

    private static boolean belongsToSession(Path file, String sessionId) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file)
                && (name.equals("init_" + sessionId + ".mp4") || name.startsWith("seg_" + sessionId + "_"));
    }

    private static boolean playlistReferencesSession(Path playlist, String sessionId) throws IOException {
        String contents = Files.readString(playlist);
        return contents.contains("init_" + sessionId + ".mp4")
                || contents.contains("seg_" + sessionId + "_");
    }
}
