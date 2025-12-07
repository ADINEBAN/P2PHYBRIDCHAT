package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;

public class ChatUIHelper {

    public static void addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {

        // Lazy load nếu có URL mà chưa có data
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
            return;
        }

        Node contentNode;

        if (msg.getType() == MessageDTO.MessageType.TEXT) {
            Text text = new Text(msg.getContent());
            text.getStyleClass().add(isMe ? "text-me" : "text-other");
            TextFlow textFlow = new TextFlow(text);
            textFlow.setMaxWidth(450);
            contentNode = textFlow;
        }
        else if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            contentNode = createImageNode(msg.getFileData());
        }
        else if (msg.getType() == MessageDTO.MessageType.AUDIO && msg.getFileData() != null) {
            contentNode = createAudioNode(msg, isMe);
        }
        else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            contentNode = createFileNode(msgContainer, msg, isMe);
        }
        else {
            // Fallback
            Label lbl = new Label(msg.getContent());
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        VBox bubble = new VBox(contentNode);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        VBox messageBlock = new VBox(3);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));
        row.getChildren().add(messageBlock);

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    // [FIX] Sửa lỗi ảnh bị trắng trơn
    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);

            // Tạo ImageView
            ImageView imageView = new ImageView(image);

            // Thiết lập kích thước cố định chiều ngang, chiều dọc tự co giãn
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // [QUAN TRỌNG] Tạo bo góc (Clip)
            // Sử dụng Rectangle có kích thước bind chặt theo ImageView
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20);
            clip.setArcHeight(20);

            // Ràng buộc chiều rộng/cao của khung cắt theo ảnh
            clip.widthProperty().bind(imageView.fitWidthProperty());

            // Với chiều cao, ta cần lắng nghe thay đổi vì nó tính toán động
            imageView.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.getHeight() > 0) {
                    clip.setHeight(newVal.getHeight());
                }
            });

            imageView.setClip(clip);
            return imageView;

        } catch (Exception e) {
            e.printStackTrace();
            return new Label("❌ Lỗi ảnh");
        }
    }
    // --- XỬ LÝ NÚT PLAY VOICE ---
    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        String textColor = isMe ? "white" : "#333333";
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 14px;");

        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true); // Disable để tránh spam click

            // Chạy trong Thread riêng để không đơ UI
            new Thread(() -> {
                AudioHelper.playAudio(msg.getFileData());
                try {
                    // Ước lượng thời gian chờ hoặc chờ AudioHelper xong
                    Thread.sleep(2000);
                } catch (Exception ex) {}

                Platform.runLater(() -> {
                    playBtn.setText("▶  Nghe lại");
                    playBtn.setDisable(false);
                });
            }).start();
        });
        return playBtn;
    }

    // [FIX] Hiển thị tên file chính xác cả khi load lại lịch sử
    private static Node createFileNode(VBox container, MessageDTO msg, boolean isMe) {
        // 1. Ưu tiên lấy tên file gốc (khi vừa gửi xong)
        String fName = msg.getFileName();

        // 2. Nếu null (do load lịch sử), trích xuất từ nội dung "[Tập tin] ..."
        if (fName == null || fName.isEmpty()) {
            if (msg.getContent() != null && msg.getContent().startsWith("[Tập tin] ")) {
                fName = msg.getContent().substring(10); // Cắt bỏ chữ "[Tập tin] "
            } else {
                fName = "Tài liệu"; // Fallback nếu không tìm thấy tên
            }
        }

        // Cắt bớt nếu tên quá dài
        String displayName = fName;
        if (displayName.length() > 25) displayName = displayName.substring(0, 22) + "...";

        Button downloadBtn = new Button("📄 " + displayName);
        String textColor = isMe ? "white" : "#333333";
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-font-size: 14px;");

        // Biến final để dùng trong lambda
        String finalName = fName;

        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(finalName);
            File file = fileChooser.showSaveDialog(downloadBtn.getScene().getWindow());
            if (file != null) {
                // Tải file trong luồng riêng để không đơ UI
                new Thread(() -> {
                    try {
                        byte[] data = msg.getFileData();
                        // Nếu data null (lazy load chưa tải xong hoặc lịch sử), phải tải lại từ server
                        if (data == null && msg.getAttachmentUrl() != null) {
                            data = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                        }

                        if (data != null) {
                            Files.write(file.toPath(), data);
                            Platform.runLater(() -> {
                                // Có thể hiện thông báo tải xong tại đây nếu muốn
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
        return downloadBtn;
    }
    private static void handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox bubble = new VBox(loadingLabel);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        VBox messageBlock = new VBox(bubble);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.setVvalue(1.0);
        });

        new Thread(() -> {
            try {
                byte[] downloadedData = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                Platform.runLater(() -> {
                    if (downloadedData != null) {
                        msg.setFileData(downloadedData);
                        Node realNode;

                        if (msg.getType() == MessageDTO.MessageType.IMAGE) realNode = createImageNode(downloadedData);
                        else if (msg.getType() == MessageDTO.MessageType.AUDIO) realNode = createAudioNode(msg, isMe);
                        else realNode = createFileNode(msgContainer, msg, isMe);

                        bubble.getChildren().setAll(realNode);
                        msgContainer.layout();
                        msgScrollPane.layout();
                        msgScrollPane.setVvalue(1.0);
                    } else {
                        loadingLabel.setText("❌ Lỗi tải");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}