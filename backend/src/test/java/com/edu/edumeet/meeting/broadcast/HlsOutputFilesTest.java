package com.edu.edumeet.meeting.broadcast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.DisplayName("HLS 세션 단위 파일 정리")
class HlsOutputFilesTest {

    @TempDir Path directory;

    @Test
    @org.junit.jupiter.api.DisplayName("A 종료 정리는 새 B의 조각과 플레이리스트를 건드리지 않는다")
    void old_session_does_not_delete_new_session_files() throws IOException {
        Path oldInit = write("init_A.mp4");
        Path oldSegment = write("seg_A_00000.m4s");
        Path newInit = write("init_B.mp4");
        Path newSegment = write("seg_B_00000.m4s");
        Path playlist = write("live.m3u8", "#EXTM3U\n#EXT-X-MAP:URI=\"init_B.mp4\"\nseg_B_00000.m4s\n");

        HlsOutputFiles.removeSession(directory, "A");

        assertThat(oldInit).doesNotExist();
        assertThat(oldSegment).doesNotExist();
        assertThat(newInit).exists();
        assertThat(newSegment).exists();
        assertThat(playlist).exists();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("A를 가리키는 플레이리스트는 A 종료 때 지운다")
    void old_session_deletes_its_playlist() throws IOException {
        Path playlist = write("live.m3u8", "#EXTM3U\n#EXT-X-MAP:URI=\"init_A.mp4\"\nseg_A_00000.m4s\n");

        HlsOutputFiles.removeSession(directory, "A");

        assertThat(playlist).doesNotExist();
    }

    private Path write(String file) throws IOException {
        return write(file, "HLS");
    }

    private Path write(String file, String contents) throws IOException {
        Path path = directory.resolve(file);
        Files.writeString(path, contents);
        return path;
    }
}
