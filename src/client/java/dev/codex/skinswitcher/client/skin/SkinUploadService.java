package dev.codex.skinswitcher.client.skin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import dev.codex.skinswitcher.mixin.client.AuthlibMinecraftClientAccessor;
import dev.codex.skinswitcher.mixin.client.MinecraftAccessor;
import dev.codex.skinswitcher.mixin.client.YggdrasilServiceAccessor;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkinUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger("SkinSwitcher");
    private static final URI UPLOAD_URI = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private SkinUploadService() {
    }

    public static CompletableFuture<UploadResult> upload(SkinPreset preset) {
        String token;
        try {
            token = getSessionToken();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not access the active Minecraft session token ({}).", exception.getClass().getSimpleName());
            return CompletableFuture.completedFuture(new UploadResult(Outcome.NETWORK_ERROR, 0, "SessionUnavailable"));
        }
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(new UploadResult(Outcome.NO_ACCOUNT, 0, ""));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                long fileSize = Files.size(preset.file());
                if (fileSize <= 0 || fileSize > 2_000_000) {
                    return new UploadResult(Outcome.INVALID_FILE, 0, "");
                }

                byte[] png = Files.readAllBytes(preset.file());
                String boundary = "----SkinSwitcher" + UUID.randomUUID().toString().replace("-", "");
                byte[] requestBody = multipartBody(boundary, preset, png);
                HttpRequest request = HttpRequest.newBuilder(UPLOAD_URI)
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                        .build();

                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    LOGGER.info("Minecraft profile skin upload accepted (HTTP {}).", status);
                } else {
                    LOGGER.warn("Minecraft profile skin upload rejected (HTTP {}).", status);
                }
                return new UploadResult(status >= 200 && status < 300 ? Outcome.ACCEPTED : Outcome.REJECTED, status, "");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Minecraft profile skin upload interrupted.");
                return new UploadResult(Outcome.NETWORK_ERROR, 0, "InterruptedException");
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Minecraft profile skin upload failed ({}).", exception.getClass().getSimpleName());
                return new UploadResult(Outcome.NETWORK_ERROR, 0, exception.getClass().getSimpleName());
            }
        });
    }

    private static String getSessionToken() {
        Minecraft client = Minecraft.getInstance();
        UserApiService service = ((MinecraftAccessor) client).skinswitcher$getUserApiService();
        if (!(service instanceof YggdrasilUserApiService yggdrasil)) return null;

        MinecraftClient authClient = ((YggdrasilServiceAccessor) yggdrasil).skinswitcher$getMinecraftClient();
        return ((AuthlibMinecraftClientAccessor) authClient).skinswitcher$getAccessToken();
    }

    private static byte[] multipartBody(String boundary, SkinPreset preset, byte[] png) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(png.length + 512);
        writeUtf8(body, "--" + boundary + "\r\n");
        writeUtf8(body, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
        writeUtf8(body, preset.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM ? "slim\r\n" : "classic\r\n");
        writeUtf8(body, "--" + boundary + "\r\n");
        writeUtf8(body, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
        writeUtf8(body, "Content-Type: image/png\r\n\r\n");
        body.write(png);
        writeUtf8(body, "\r\n--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    private static void writeUtf8(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    public enum Outcome {
        ACCEPTED,
        REJECTED,
        NO_ACCOUNT,
        INVALID_FILE,
        NETWORK_ERROR
    }

    public record UploadResult(Outcome outcome, int statusCode, String safeErrorType) {
    }
}
