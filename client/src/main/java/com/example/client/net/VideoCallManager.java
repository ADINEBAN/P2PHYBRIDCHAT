package com.example.client.net;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.awt.Dimension;

public class VideoCallManager {
    private DatagramSocket udpSocket;
    private boolean isCalling = false;
    private Webcam webcam;

    private String targetIp;
    private int targetPort; // Port video của đối phương

    // ImageView để hiển thị
    private ImageView myView;
    private ImageView partnerView;

    // Kích thước nén ảnh để gửi qua UDP (64KB max)
    // 320x240 là đủ cho chat video cơ bản và vừa gói tin UDP
    private static final Dimension RESOLUTION = new Dimension(320, 240);

    public void setupUI(ImageView myView, ImageView partnerView) {
        this.myView = myView;
        this.partnerView = partnerView;
    }

    public void startVideo(String targetIp, int targetPort, int myVideoPort) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.isCalling = true;

        try {
            // 1. Cố gắng mở Webcam
            webcam = Webcam.getDefault();
            if (webcam != null) {
                try {
                    // SỬA: Thêm try-catch riêng cho việc mở webcam
                    webcam.setViewSize(RESOLUTION);
                    webcam.open();
                } catch (com.github.sarxos.webcam.WebcamLockException e) {
                    System.err.println("[CẢNH BÁO] Webcam đang bị ứng dụng khác sử dụng! Bạn sẽ không gửi được hình ảnh.");
                    webcam = null; // Đánh dấu là không có webcam để không gửi dữ liệu rác
                } catch (Exception e) {
                    System.err.println("[LỖI] Không thể mở Webcam: " + e.getMessage());
                    webcam = null;
                }
            } else {
                System.err.println("Không tìm thấy Webcam!");
            }

            // 2. Mở Socket UDP (Khác port Audio)
            udpSocket = new DatagramSocket(myVideoPort);

            // 3. Chạy luồng Gửi và Nhận
            // Chỉ gửi video nếu webcam mở thành công
            if (webcam != null && webcam.isOpen()) {
                new Thread(this::sendVideo).start();
            }

            // Luôn luôn nhận video từ đối phương (dù mình hỏng cam thì vẫn xem được họ)
            new Thread(this::receiveVideo).start();

        } catch (Exception e) {
            e.printStackTrace();
            stopVideo();
        }
    }
    private void sendVideo() {
        try {
            while (isCalling && webcam != null && webcam.isOpen()) {
                // Lấy ảnh từ webcam
                BufferedImage bImage = webcam.getImage();
                if (bImage == null) continue;

                // Hiển thị lên UI của mình (Mirror)
                Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                Platform.runLater(() -> {
                    if (myView != null) myView.setImage(fxImage);
                });

                // Nén ảnh sang JPEG byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "jpg", baos);
                byte[] data = baos.toByteArray();

                // Lưu ý: Gói tin UDP tối đa khoảng 65KB.
                // Nếu ảnh > 60KB sẽ bị lỗi. Với 320x240 JPEG thì thường chỉ 10-20KB.
                if (data.length < 60000) {
                    InetAddress address = InetAddress.getByName(targetIp);
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
                    udpSocket.send(packet);
                }

                // Giới hạn FPS (khoảng 20-30 FPS)
                Thread.sleep(40);
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    private void receiveVideo() {
        byte[] buffer = new byte[65000]; // Buffer lớn để chứa ảnh
        try {
            while (isCalling) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                // Chuyển byte[] thành Image
                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                BufferedImage bImage = ImageIO.read(bais);

                if (bImage != null) {
                    Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                    // Update lên UI (Thread an toàn)
                    Platform.runLater(() -> {
                        if (partnerView != null) partnerView.setImage(fxImage);
                    });
                }
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    public void stopVideo() {
        isCalling = false;
        if (webcam != null) webcam.close();
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
    }
}