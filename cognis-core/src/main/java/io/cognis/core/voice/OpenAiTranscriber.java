package io.cognis.core.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class OpenAiTranscriber implements Transcriber {
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiTranscriber(String apiBase, String apiKey, String model) {
        String base = apiBase == null ? "" : apiBase.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/audio/transcriptions")) {
            base = base + "/audio/transcriptions";
        }

        this.endpoint = base;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "whisper-1" : model;
        this.client = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(90)).build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String transcribe(Path audioPath) throws IOException {
        if (apiKey.isBlank() || endpoint.isBlank()) {
            throw new IOException("transcriber is not configured");
        }
        if (!Files.exists(audioPath)) {
            throw new IOException("audio file does not exist: " + audioPath);
        }

        RequestBody fileBody = RequestBody.create(Files.readAllBytes(audioPath), MediaType.parse("application/octet-stream"));
        RequestBody multipart = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioPath.getFileName().toString(), fileBody)
            .addFormDataPart("model", model)
            .build();

        Request request = new Request.Builder()
            .url(endpoint)
            .post(multipart)
            .header("Authorization", "Bearer " + apiKey)
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("transcription failed with status " + response.code() + ": " + body);
            }
            JsonNode root = mapper.readTree(body);
            String text = root.path("text").asText("").trim();
            if (text.isBlank()) {
                throw new IOException("transcription response did not include text");
            }
            return text;
        }
    }
}
