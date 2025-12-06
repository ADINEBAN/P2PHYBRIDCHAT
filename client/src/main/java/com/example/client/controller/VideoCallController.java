package com.example.client.controller;

import com.example.client.net.VideoCallManager;
import com.example.client.net.VoiceCallManager;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class VideoCallController {
    @FXML private ImageView myCam;
    @FXML private ImageView partnerCam;

    private VoiceCallManager voiceManager;
    private VideoCallManager videoManager;
    private MainController mainController;

    public void initialize() {
        // Khởi tạo Video Manager
        videoManager = new VideoCallManager();
        videoManager.setupUI(myCam, partnerCam);
    }

    public void setDependencies(MainController mainController, VoiceCallManager voiceManager) {
        this.mainController = mainController;
        this.voiceManager = voiceManager;
    }

    // Bắt đầu cả Voice và Video
    public void startCall(String targetIp, int partnerBasePort, int myBasePort) {
        // Quy ước Port:
        // Port chat (TCP) = X
        // Port voice (UDP) = X + 1
        // Port video (UDP) = X + 2

        // Start Audio
        voiceManager.startCall(targetIp, partnerBasePort + 1, myBasePort + 1);

        // Start Video
        videoManager.startVideo(targetIp, partnerBasePort + 2, myBasePort + 2);
    }

    @FXML
    public void handleEndCall() {
        // Dừng các luồng
        if (voiceManager != null) voiceManager.stopCall();
        if (videoManager != null) videoManager.stopVideo();

        // Báo về MainController để gửi tín hiệu CALL_END
        if (mainController != null) {
            mainController.handleEndCallSignal();
        }

        // Đóng cửa sổ
        closeWindow();
    }

    public void closeWindow() {
        Stage stage = (Stage) myCam.getScene().getWindow();
        stage.close();
    }
}