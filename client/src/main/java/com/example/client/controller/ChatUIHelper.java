package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.util.AudioHelper;
import com.example.client.util.ThreadManager;
import com.example.common.dto.MessageDTO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;

public class ChatUIHelper {

    private static MainController mainController;

    public static void setMainController(MainController mc) {
        mainController = mc;
    }

    public static VBox addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {

        // Lazy Load
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            return handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
        }

        Node contentNode;

        // --- [1] TẠO NỘI DUNG ---
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            Label lbl = new Label("🚫 Tin nhắn đã thu hồi");
            lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
            contentNode = lbl;
        } else if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            Label lbl = new Label(msg.getContent());
            lbl.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px; -fx-font-style: italic; -fx-padding: 5 10; -fx-background-color: #f0f0f0; -fx-background-radius: 10;");
            contentNode = lbl;
        } else if (msg.getType() == MessageDTO.MessageType.TEXT) {
            Text text = new Text(msg.getContent());
            text.getStyleClass().add(isMe ? "text-me" : "text-other");
            TextFlow textFlow = new TextFlow(text);
            textFlow.setMaxWidth(450);
            contentNode = textFlow;
        } else if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            contentNode = createImageNode(msg.getFileData());
        } else if (msg.getType() == MessageDTO.MessageType.AUDIO && msg.getFileData() != null) {
            contentNode = createAudioNode(msg, isMe);
        } else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            contentNode = createFileNode(msgContainer, msg, isMe);
        } else {
            Label lbl = new Label(msg.getContent() != null ? msg.getContent() : "Tin nhắn không xác định");
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        // --- [2] BONG BÓNG CHAT ---
        VBox bubble = new VBox(contentNode);
        bubble.setUserData(msg);
        if (msg.getType() != MessageDTO.MessageType.NOTIFICATION) {
            bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");
        } else {
            bubble.setAlignment(Pos.CENTER);
        }
//        if (msg.isPinned()) {
//            bubble.getStyleClass().add("pinned-bubble");
//        }

        // --- [3] WRAPPER (CHỨA REACTION) ---
        StackPane bubbleWrapper = new StackPane();
        bubbleWrapper.setAlignment(isMe ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);
        bubbleWrapper.getChildren().add(bubble);

        Label reactionLabel = new Label();
        reactionLabel.setVisible(false);
        reactionLabel.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 3, 0, 0, 0); -fx-padding: 2; -fx-font-size: 11px; -fx-border-color: #e4e6eb; -fx-border-radius: 10; -fx-min-width: 20; -fx-alignment: center;");

        StackPane.setMargin(reactionLabel, new Insets(0, isMe ? 0 : -5, -8, isMe ? -5 : 0));
        StackPane.setAlignment(reactionLabel, isMe ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);

        if (msg.getType() != MessageDTO.MessageType.RECALL && msg.getType() != MessageDTO.MessageType.NOTIFICATION) {
            bubbleWrapper.getChildren().add(reactionLabel);
        }

        // --- [4] XỬ LÝ SỰ KIỆN MENU (Chuột phải vào bong bóng cũng hiện) ---
        if (msg.getType() != MessageDTO.MessageType.NOTIFICATION) {
            bubble.setOnContextMenuRequested(e -> {
                ContextMenu menu = createContextMenu(msg, isMe, bubble, reactionLabel);
                menu.show(bubble, e.getScreenX(), e.getScreenY());
            });
        }

        // --- [5] TẠO HÀNG CHỨA (AVATAR + NÚT OPTION + BONG BÓNG) ---
        HBox contentRow = new HBox(5);
        contentRow.setAlignment(Pos.CENTER);

        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            contentRow.getChildren().add(bubble);
        } else {
            // Nút 3 chấm (Tạo chung để dùng cho cả 2 bên)
            Button optionsBtn = null;
            if (msg.getType() != MessageDTO.MessageType.RECALL) {
                optionsBtn = new Button("⋮");
                optionsBtn.getStyleClass().add("btn-msg-options");
                // Style cho nút mờ nhạt, hiện khi hover (hoặc luôn hiện)
                optionsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 14px; -fx-cursor: hand; -fx-font-weight: bold;");

                Button finalOptionsBtn = optionsBtn;
                optionsBtn.setOnAction(e -> {
                    ContextMenu menu = createContextMenu(msg, isMe, bubble, reactionLabel);
                    Point2D screenLoc = finalOptionsBtn.localToScreen(0, finalOptionsBtn.getHeight());
                    menu.show(finalOptionsBtn, screenLoc.getX(), screenLoc.getY());
                });
            }

            if (isMe) {
                // --- TIN NHẮN CỦA TÔI ---
                contentRow.setAlignment(Pos.CENTER_RIGHT);
                if (optionsBtn != null) {
                    // Nút Option -> Bong bóng
                    contentRow.getChildren().addAll(optionsBtn, bubbleWrapper);
                } else {
                    contentRow.getChildren().add(bubbleWrapper);
                }
            } else {
                // --- TIN NHẮN NGƯỜI KHÁC ---
                contentRow.setAlignment(Pos.CENTER_LEFT);

                // Avatar
                Circle avatarCircle = new Circle(15);
                avatarCircle.setFill(javafx.scene.paint.Color.LIGHTGRAY);
                loadAvatar(avatarCircle);

                if (optionsBtn != null) {
                    // [ĐÃ SỬA] Avatar -> Bong bóng -> Nút Option
                    contentRow.getChildren().addAll(avatarCircle, bubbleWrapper, optionsBtn);
                } else {
                    contentRow.getChildren().addAll(avatarCircle, bubbleWrapper);
                }
            }
        }

        // --- [6] ĐÓNG GÓI VÀO BLOCK ---
        VBox messageBlock = new VBox(3);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) messageBlock.setAlignment(Pos.CENTER);

        messageBlock.getChildren().add(contentRow);

        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) row.setAlignment(Pos.CENTER);

        row.setPadding(new Insets(2, 10, 2, 10));
        row.getChildren().add(messageBlock);

        // Lưu text để tìm kiếm
        if (msg.getContent() != null) row.setUserData(msg.getContent().toLowerCase());

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            // Lưu uuid vào map để xóa sau này
            if (mainController != null && msg.getUuid() != null) {
                mainController.messageUiMap.put(msg.getUuid(), bubble);
            }
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });

        return bubble;
    }

    // --- HÀM TẠO MENU (ĐÃ SỬA: HIỆN NÚT GHIM/XÓA CHO CẢ 2 BÊN) ---
    private static ContextMenu createContextMenu(MessageDTO msg, boolean isMe, Node anchorNode, Label reactionLabel) {
        ContextMenu contextMenu = new ContextMenu();

        // 1. CHỨC NĂNG CHUNG (Ghim & Xóa phía tôi) - HIỆN CHO TẤT CẢ
        if (msg.getType() != MessageDTO.MessageType.RECALL &&
                msg.getType() != MessageDTO.MessageType.NOTIFICATION &&
                mainController != null) {

            MenuItem pinItem = new MenuItem(msg.isPinned() ? "Bỏ ghim" : "📌 Ghim tin nhắn");
            pinItem.setOnAction(ev -> mainController.handlePinAction(msg));
            contextMenu.getItems().add(pinItem);

            MenuItem deleteMeItem = new MenuItem("🗑 Xóa ở phía tôi");
            deleteMeItem.setStyle("-fx-text-fill: red;");
            deleteMeItem.setOnAction(ev -> mainController.handleDeleteForMeAction(msg));
            contextMenu.getItems().add(deleteMeItem);

            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // 2. CHỨC NĂNG RIÊNG (Sửa & Thu hồi) - CHỈ HIỆN CHO TÔI
        if (isMe && msg.getType() != MessageDTO.MessageType.RECALL && mainController != null) {
            if (msg.getType() == MessageDTO.MessageType.TEXT) {
                MenuItem editItem = new MenuItem("✏ Chỉnh sửa");
                editItem.setOnAction(ev -> mainController.handleEditAction(msg));
                contextMenu.getItems().add(editItem);
            }
            MenuItem recallItem = new MenuItem("🚫 Thu hồi (Mọi người)");
            recallItem.setOnAction(ev -> mainController.handleRecallAction(msg));
            contextMenu.getItems().add(recallItem);
        }

        return contextMenu;
    }

    // --- CÁC HÀM HELPER KHÁC GIỮ NGUYÊN (LOAD AVATAR, MEDIA...) ---
    // (Đã dùng ThreadManager như phiên bản trước để tối ưu)

    private static void loadAvatar(Circle avatarCircle) {
        String avatarUrl = null;
        if (mainController != null && mainController.currentChatUser != null) {
            avatarUrl = mainController.currentChatUser.getAvatarUrl();
        }
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            final String finalUrl = avatarUrl;
            ThreadManager.imageExecutor.submit(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(finalUrl);
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data), 64, 64, true, true);
                        Platform.runLater(() -> avatarCircle.setFill(new ImagePattern(img)));
                    }
                } catch (Exception e) {}
            });
        }
    }

    public static void updateBubbleContent(VBox bubble, String newContent, boolean isRecall) {
        Platform.runLater(() -> {
            bubble.getChildren().clear();
            Label lbl = new Label(newContent);

            if (isRecall) {
                // 1. Style lại bong bóng thành màu xám (Tin nhắn thu hồi)
                lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
                bubble.getStyleClass().removeAll("bubble-me", "bubble-other");
                bubble.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 18px; -fx-padding: 10 15;");

                // 2. [QUAN TRỌNG] Tìm và xóa nút 3 chấm + Reaction
                if (bubble.getParent() instanceof StackPane) {
                    StackPane wrapper = (StackPane) bubble.getParent();

                    // A. Xóa Reaction (nếu có) - Reaction là Label nằm trong wrapper
                    wrapper.getChildren().removeIf(node -> node instanceof Label);

                    // B. Xóa Nút 3 chấm - Nằm trong HBox contentRow (cha của wrapper)
                    if (wrapper.getParent() instanceof HBox) {
                        HBox parentRow = (HBox) wrapper.getParent();

                        // Xóa tất cả các nút Button trong hàng này (Chính là nút 3 chấm)
                        parentRow.getChildren().removeIf(node -> node instanceof Button);
                    }
                }
            } else {
                // Logic hiển thị tin nhắn chỉnh sửa (không phải thu hồi)
                boolean isMe = bubble.getStyleClass().contains("bubble-me");
                lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            }

            bubble.getChildren().add(lbl);
        });
    }
    private static VBox handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        // (Giữ nguyên logic Lazy Load đã tối ưu ở câu trả lời trước)
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        VBox bubble = new VBox(loadingLabel);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");
        StackPane bubbleWrapper = new StackPane(bubble);
        bubbleWrapper.setAlignment(isMe ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);
        VBox messageBlock = new VBox(bubbleWrapper);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            if (mainController != null && msg.getUuid() != null) mainController.messageUiMap.put(msg.getUuid(), bubble);
            msgScrollPane.setVvalue(1.0);
        });

        ThreadManager.networkExecutor.submit(() -> {
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
                    } else { loadingLabel.setText("❌ Lỗi tải"); }
                });
            } catch (Exception e) {}
        });
        return bubble;
    }

    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis, 400, 0, true, true);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            StackPane container = new StackPane(imageView);
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20); clip.setArcHeight(20);
            clip.widthProperty().bind(container.widthProperty());
            clip.heightProperty().bind(container.heightProperty());
            container.setClip(clip);
            return container;
        } catch (Exception e) { return new Label("❌ Lỗi ảnh"); }
    }

    private static Node createFileNode(VBox container, MessageDTO msg, boolean isMe) {
        String fName = msg.getFileName() != null ? msg.getFileName() : "Tài liệu";
        Button downloadBtn = new Button("📄 " + (fName.length() > 25 ? fName.substring(0, 22) + "..." : fName));
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + (isMe ? "white" : "#333333") + "; -fx-cursor: hand; -fx-font-size: 14px;");
        downloadBtn.setOnAction(event -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(fName);
            File f = fc.showSaveDialog(downloadBtn.getScene().getWindow());
            if (f != null) {
                ThreadManager.networkExecutor.submit(() -> { try { Files.write(f.toPath(), msg.getFileData()); } catch (Exception e) {} });
            }
        });
        return downloadBtn;
    }

    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + (isMe ? "white" : "#333333") + "; -fx-font-weight: bold;");
        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true);
            ThreadManager.networkExecutor.submit(() -> {
                AudioHelper.playAudio(msg.getFileData());
                try { Thread.sleep(2000); } catch (Exception ex) {}
                Platform.runLater(() -> { playBtn.setText("▶  Nghe lại"); playBtn.setDisable(false); });
            });
        });
        return playBtn;
    }

    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}