package io.cognis.core.voice;

import java.io.IOException;
import java.nio.file.Path;

public interface Transcriber {
    String transcribe(Path audioPath) throws IOException;
}
