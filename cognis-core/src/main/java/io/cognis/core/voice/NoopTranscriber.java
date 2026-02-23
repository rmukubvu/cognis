package io.cognis.core.voice;

import java.io.IOException;
import java.nio.file.Path;

public final class NoopTranscriber implements Transcriber {
    @Override
    public String transcribe(Path audioPath) throws IOException {
        throw new IOException("transcriber is not configured");
    }
}
