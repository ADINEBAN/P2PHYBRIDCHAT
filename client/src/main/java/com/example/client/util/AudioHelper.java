package com.example.client.util;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class AudioHelper {
    // Định dạng âm thanh chuẩn cho ghi âm giọng nói (WAV PCM)
    // 16kHz, 16-bit, Mono (đủ rõ và nhẹ hơn chuẩn CD)
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, true);

    private TargetDataLine microphone;
    private ByteArrayOutputStream outStream;
    private boolean isRecording = false;

    // --- PHẦN GHI ÂM ---

    public void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Microphone không được hỗ trợ!");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(FORMAT);
            microphone.start();

            outStream = new ByteArrayOutputStream();
            isRecording = true;

            // Chạy luồng ghi dữ liệu vào RAM
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int read = microphone.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        outStream.write(buffer, 0, read);
                    }
                }
            }).start();

            System.out.println("Đang ghi âm...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Dừng ghi âm và trả về mảng byte dữ liệu
    public byte[] stopRecording() {
        isRecording = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        System.out.println("Đã dừng ghi âm.");
        return outStream != null ? outStream.toByteArray() : null;
    }

    // --- PHẦN PHÁT LẠI (PLAYBACK) ---

    public static void playAudio(byte[] audioData) {
        new Thread(() -> {
            try {
                // Tạo Stream từ byte array
                ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream ais = new AudioInputStream(bais, FORMAT, audioData.length / FORMAT.getFrameSize());

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);

                speakers.open(FORMAT);
                speakers.start();

                byte[] buffer = new byte[4096];
                int read;
                while ((read = ais.read(buffer)) != -1) {
                    speakers.write(buffer, 0, read);
                }

                speakers.drain();
                speakers.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}