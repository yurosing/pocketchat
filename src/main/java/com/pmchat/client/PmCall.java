package com.pmchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Анонимные голосовые звонки напрямую через бэкенд {@code server-pocketchat}
 * вместо группы Simple Voice Chat: сервер выдаёт одноразовый {@code callId},
 * известный только звонящему и адресату, и вслепую перекладывает бинарные
 * PCM-фреймы между их двумя WebSocket-сокетами — без "группы", в которую
 * теоретически мог зайти кто-то третий, и без звука, который где-то
 * сохраняется или логируется. Сигналинг (кто кому звонит) тоже идёт мимо
 * обычного {@code /m}, чтобы не светить служебный текст в чате не-модовым
 * игрокам (см. {@link PmBackend#callInvite}/{@link PmBackend#callPoll}).
 */
public final class PmCall {

    private PmCall() {
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int CHUNK_BYTES = (int) (FORMAT.getSampleRate() * FORMAT.getFrameSize() * 20 / 1000);
    private static final long POLL_INTERVAL_MS = 2000L;

    private static volatile WebSocket socket;
    private static volatile TargetDataLine mic;
    private static volatile SourceDataLine speaker;
    private static volatile boolean connected = false;
    private static volatile long lastPeerAudioAt = 0L;
    private static volatile long lastSelfLevelAt = 0L;
    private static volatile boolean selfSpeaking = false;

    private static volatile String pendingCallId;
    private static volatile String pendingFrom;
    private static long lastPollAt = 0L;

    public static boolean isConnected() {
        return connected;
    }

    // ---------- исходящий звонок ----------

    public static void startCall(String target) {
        if (!PmBackend.isConfigured() || !PmBackend.hasAccount() || target == null || target.isBlank()) return;
        PmBackend.callInvite(target, callId -> {
            if (callId != null) connect(callId);
        });
    }

    // ---------- входящий звонок: короткий опрос из тика ----------

    public static void pollTick() {
        if (!PmBackend.isConfigured() || !PmBackend.hasAccount() || connected) return;
        long now = System.currentTimeMillis();
        if (now - lastPollAt < POLL_INTERVAL_MS) return;
        lastPollAt = now;
        PmBackend.callPoll((callId, from) -> {
            if (callId.equals(pendingCallId)) return;
            pendingCallId = callId;
            pendingFrom = from;
            Minecraft client = Minecraft.getInstance();
            if (!PmChatClient.getConfig().dnd) {
                client.gui.toastManager().addToast(new PmToast(from,
                        Component.translatable("pmchat.call.incoming.toast").getString()));
                PmChatClient.playNotifySound(client);
            }
        });
    }

    public static String pendingCallFrom() {
        return pendingFrom;
    }

    public static void acceptPendingCall() {
        String callId = pendingCallId;
        pendingCallId = null;
        pendingFrom = null;
        if (callId != null) connect(callId);
    }

    public static void declinePendingCall() {
        String callId = pendingCallId;
        pendingCallId = null;
        pendingFrom = null;
        if (callId != null) PmBackend.callCancel(callId);
    }

    // ---------- сокет + аудио ----------

    private static void connect(String callId) {
        String wsUrl = PmBackend.callWsUrl(callId);
        if (wsUrl == null) return;
        try {
            openAudio();
        } catch (LineUnavailableException e) {
            PmChatClient.LOGGER.warn("PmCall: no audio device: {}", e.toString());
            return;
        }
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] buf = new byte[data.remaining()];
                data.get(buf);
                lastPeerAudioAt = System.currentTimeMillis();
                SourceDataLine s = speaker;
                if (s != null) s.write(buf, 0, buf.length);
                webSocket.request(1);
                return null;
            }

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                Minecraft.getInstance().execute(PmCall::endCall);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                PmChatClient.LOGGER.debug("PmCall socket error: {}", error.toString());
                Minecraft.getInstance().execute(PmCall::endCall);
            }
        };
        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI.create(wsUrl), listener)
                .whenComplete((ws, err) -> {
                    if (err != null || ws == null) {
                        PmChatClient.LOGGER.debug("PmCall connect failed: {}", err);
                        Minecraft.getInstance().execute(PmCall::endCall);
                        return;
                    }
                    socket = ws;
                    connected = true;
                    startCapture(ws);
                });
    }

    private static void openAudio() throws LineUnavailableException {
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine t = (TargetDataLine) AudioSystem.getLine(micInfo);
        t.open(FORMAT);
        t.start();
        mic = t;
        DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
        SourceDataLine s = (SourceDataLine) AudioSystem.getLine(spkInfo);
        s.open(FORMAT);
        s.start();
        speaker = s;
    }

    private static void startCapture(WebSocket ws) {
        Thread capture = new Thread(() -> {
            byte[] buf = new byte[CHUNK_BYTES];
            while (connected) {
                TargetDataLine m = mic;
                if (m == null) break;
                int n = m.read(buf, 0, buf.length);
                if (n <= 0) continue;
                lastSelfLevelAt = System.currentTimeMillis();
                selfSpeaking = rms(buf, n) > 600;
                ByteBuffer bb = ByteBuffer.allocate(n);
                bb.put(buf, 0, n);
                bb.flip();
                try {
                    ws.sendBinary(bb, true).join();
                } catch (Exception e) {
                    break;
                }
            }
        }, "pmchat-call-capture");
        capture.setDaemon(true);
        capture.start();
    }

    private static double rms(byte[] buf, int len) {
        long sum = 0;
        int samples = len / 2;
        for (int i = 0; i + 1 < len; i += 2) {
            short v = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
            sum += (long) v * v;
        }
        return samples == 0 ? 0 : Math.sqrt((double) sum / samples);
    }

    /** Говорим ли мы прямо сейчас (по уровню сигнала микрофона за последние ~300мс). */
    public static boolean isSelfSpeaking() {
        return connected && selfSpeaking && System.currentTimeMillis() - lastSelfLevelAt < 300;
    }

    /** Говорит ли собеседник (по факту недавнего приёма аудио от него). */
    public static boolean isPeerSpeaking() {
        return connected && System.currentTimeMillis() - lastPeerAudioAt < 300;
    }

    public static void endCall() {
        connected = false;
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
            } catch (Exception ignored) {
                // сокет уже мог закрыться
            }
            ws.abort();
        }
        TargetDataLine m = mic;
        mic = null;
        if (m != null) {
            m.stop();
            m.close();
        }
        SourceDataLine s = speaker;
        speaker = null;
        if (s != null) {
            s.stop();
            s.close();
        }
    }
}
