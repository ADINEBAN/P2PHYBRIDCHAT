package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.AudioHelper;
import com.example.client.util.ThreadManager;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
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

        // Kiểm tra xem đây có phải là nhóm không
        boolean isGroup = false;
        if (mainController != null && mainController.currentChatUser != null) {
            isGroup = "GROUP".equals(mainController.currentChatUser.getUsername());
        }

        // Lazy Load Media (Giữ nguyên)
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            return handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
        }

        Node contentNode;

        // --- [1] TẠO NỘI DUNG TIN NHẮN (Giữ nguyên) ---
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            Label lbl = new Label("🚫 Tin nhắn đã thu hồi");
            lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
            contentNode = lbl;
        } else if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            String content = msg.getContent();

            // [MỚI] XỬ LÝ CHỮ "BẠN"
            if (isMe) {
                // Giả sử server gửi: "Chung Ngo đã đổi biệt danh thành: Chung"
                // Ta tìm chữ "đã" và thay thế đoạn trước nó
                int actionIndex = content.indexOf("đã");
                if (actionIndex > 0) {
                    content = "Bạn " + content.substring(actionIndex);
                }
            }

            Label lbl = new Label(content);
            // ... (Style giữ nguyên)
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

        // --- [SỬA ĐỔI QUAN TRỌNG] HIỂN THỊ TÊN BIỆT DANH/DISPLAY NAME ---
        Node finalBubbleNode = bubbleWrapper;
        if (isGroup && !isMe && msg.getType() != MessageDTO.MessageType.NOTIFICATION) {

            // Tạo Label tên mặc định
            String initialName = msg.getSenderName();
            if (initialName == null || initialName.isEmpty()) initialName = "Thành viên " + msg.getSenderId();

            Label nameLabel = new Label(initialName);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #65676b; -fx-font-weight: bold; -fx-padding: 0 0 2 5;");

            // [QUAN TRỌNG - BẠN ĐANG THIẾU ĐOẠN NÀY]
            // Gắn thẻ để MainController tìm được Label này mà sửa tên ngay lập tức
            nameLabel.getProperties().put("TYPE", "NAME_LABEL");
            nameLabel.getProperties().put("USER_ID", msg.getSenderId());

            // Load tên thật/biệt danh mới nhất từ hệ thống
            loadSenderName(nameLabel, msg.getSenderId(), msg.getConversationId());

            VBox groupBubbleContainer = new VBox(nameLabel, bubbleWrapper);
            finalBubbleNode = groupBubbleContainer;
        }

        // --- [4] MENU CHUỘT PHẢI (Giữ nguyên) ---
        if (msg.getType() != MessageDTO.MessageType.NOTIFICATION) {
            bubble.setOnContextMenuRequested(e -> {
                ContextMenu menu = createContextMenu(msg, isMe, bubble, reactionLabel);
                menu.show(bubble, e.getScreenX(), e.getScreenY());
            });
        }

        // --- [5] TẠO HÀNG CHỨA (ROW) ---
        HBox contentRow = new HBox(5);
        contentRow.setAlignment(Pos.CENTER);

        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            contentRow.getChildren().add(bubble);
        } else {
            Button optionsBtn = null;
            if (msg.getType() != MessageDTO.MessageType.RECALL) {
                optionsBtn = new Button("⋮");
                optionsBtn.getStyleClass().add("btn-msg-options");
                optionsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 14px; -fx-cursor: hand; -fx-font-weight: bold;");

                Button finalOptionsBtn = optionsBtn;
                optionsBtn.setOnAction(e -> {
                    ContextMenu menu = createContextMenu(msg, isMe, bubble, reactionLabel);
                    Point2D screenLoc = finalOptionsBtn.localToScreen(0, finalOptionsBtn.getHeight());
                    menu.show(finalOptionsBtn, screenLoc.getX(), screenLoc.getY());
                });
            }

            if (isMe) {
                contentRow.setAlignment(Pos.CENTER_RIGHT);
                if (optionsBtn != null) contentRow.getChildren().addAll(optionsBtn, finalBubbleNode);
                else contentRow.getChildren().add(finalBubbleNode);
            } else {
                contentRow.setAlignment(Pos.CENTER_LEFT);

                // Avatar
                Circle avatarCircle = new Circle(15);
                avatarCircle.setFill(javafx.scene.paint.Color.LIGHTGRAY);

                // Load Avatar theo ID
                loadAvatar(avatarCircle, msg.getSenderId());

                if (optionsBtn != null) contentRow.getChildren().addAll(avatarCircle, finalBubbleNode, optionsBtn);
                else contentRow.getChildren().addAll(avatarCircle, finalBubbleNode);
            }
        }

        // --- [6] ĐÓNG GÓI ---
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

        row.setUserData(msg);

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            if (mainController != null && msg.getUuid() != null) {
                mainController.messageUiMap.put(msg.getUuid(), bubble);
            }
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });

        return bubble;
    }

    // --- [HÀM MỚI] LOAD TÊN BIỆT DANH/DISPLAY NAME TỪ HỆ THỐNG ---
    private static void loadSenderName(Label nameLabel, long senderId, long groupId) {
        // Chạy thread ngầm để không đơ giao diện
        ThreadManager.networkExecutor.submit(() -> {
            try {
                String displayName = null;

                // Cách 1: Tìm trong danh sách bạn bè cục bộ (Nhanh nhất)
                // Thông thường DisplayName trong UserDTO chính là tên hiển thị (biệt danh nếu có)
                if (mainController != null && mainController.getContactManager() != null) {
                    UserDTO u = mainController.getContactManager().findUserInList(senderId);
                    if (u != null) {
                        displayName = u.getDisplayName();
                    }
                }

                // Cách 2: Nếu chưa có (người lạ hoặc cần update mới nhất từ Server)
                // Gọi Server để lấy UserInfo mới nhất
                if (displayName == null) {
                    UserDTO remoteInfo = RmiClient.getDirectoryService().getUserInfo(senderId);
                    if (remoteInfo != null) {
                        displayName = remoteInfo.getDisplayName();
                    }
                }

                /* * MỞ RỘNG: Nếu bạn có API riêng để lấy Biệt danh trong nhóm (VD: getGroupNickname)
                 * thì gọi ở đây. Ví dụ:
                 * String nickname = RmiClient.getGroupService().getGroupNickname(groupId, senderId);
                 * if (nickname != null) displayName = nickname;
                 */

                // Cập nhật lên giao diện
                if (displayName != null && !displayName.isEmpty()) {
                    String finalName = displayName;
                    Platform.runLater(() -> nameLabel.setText(finalName));
                }
            } catch (Exception e) {
                // Lỗi mạng thì giữ nguyên tên cũ
            }
        });
    }

    // --- LOAD AVATAR GIỮ NGUYÊN NHƯNG TỐI ƯU ---
    private static void loadAvatar(Circle avatarCircle, long senderId) {
        if (mainController == null) return;

        // Logic tìm URL avatar
        String avatarUrl = null;
        if (mainController.currentChatUser != null
                && !"GROUP".equals(mainController.currentChatUser.getUsername())
                && mainController.currentChatUser.getId() == senderId) {
            avatarUrl = mainController.currentChatUser.getAvatarUrl();
        }
        else {
            if (mainController.getContactManager() != null) {
                UserDTO u = mainController.getContactManager().findUserInList(senderId);
                if (u != null) avatarUrl = u.getAvatarUrl();
            }
        }

        // Nếu không tìm thấy URL trong cache, thử tải Info từ server (phòng trường hợp người lạ)
        if (avatarUrl == null) {
            ThreadManager.networkExecutor.submit(() -> {
                try {
                    UserDTO u = RmiClient.getDirectoryService().getUserInfo(senderId);
                    if (u != null && u.getAvatarUrl() != null) {
                        downloadAndSetAvatar(avatarCircle, u.getAvatarUrl());
                    }
                } catch (Exception e) {}
            });
        } else {
            downloadAndSetAvatar(avatarCircle, avatarUrl);
        }
    }

    private static void downloadAndSetAvatar(Circle avatarCircle, String url) {
        if (url == null || url.isEmpty()) return;
        ThreadManager.imageExecutor.submit(() -> {
            try {
                byte[] data = RmiClient.getMessageService().downloadFile(url);
                if (data != null) {
                    Image img = new Image(new ByteArrayInputStream(data), 64, 64, true, true);
                    Platform.runLater(() -> avatarCircle.setFill(new ImagePattern(img)));
                }
            } catch (Exception e) {}
        });
    }

    // --- CÁC HÀM KHÁC GIỮ NGUYÊN ---
    private static ContextMenu createContextMenu(MessageDTO msg, boolean isMe, Node anchorNode, Label reactionLabel) {
        ContextMenu contextMenu = new ContextMenu();
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

    public static void updateBubbleContent(VBox bubble, String newContent, boolean isRecall) {
        Platform.runLater(() -> {
            bubble.getChildren().clear();
            Label lbl = new Label(newContent);
            if (isRecall) {
                lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
                bubble.getStyleClass().removeAll("bubble-me", "bubble-other");
                bubble.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 18px; -fx-padding: 10 15;");
                if (bubble.getParent() instanceof StackPane) {
                    StackPane wrapper = (StackPane) bubble.getParent();
                    wrapper.getChildren().removeIf(node -> node instanceof Label);
                }
            } else {
                boolean isMe = bubble.getStyleClass().contains("bubble-me");
                lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            }
            bubble.getChildren().add(lbl);
        });
    }

    private static VBox handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        VBox bubble = new VBox(loadingLabel);
        bubble.setUserData(msg);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        StackPane bubbleWrapper = new StackPane(bubble);
        bubbleWrapper.setAlignment(isMe ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);

        Node finalNode = bubbleWrapper;

        // Check group & name cho Lazy Load (Áp dụng logic loadSenderName tương tự)
        boolean isGroup = false;
        if (mainController != null && mainController.currentChatUser != null) {
            isGroup = "GROUP".equals(mainController.currentChatUser.getUsername());
        }

        if (isGroup && !isMe) {
            Label nameLabel = new Label(msg.getSenderName() != null ? msg.getSenderName() : "Thành viên " + msg.getSenderId());
            nameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #65676b; -fx-padding: 0 0 2 5;");

            // Gọi hàm load tên mới
            loadSenderName(nameLabel, msg.getSenderId(), msg.getConversationId());

            finalNode = new VBox(nameLabel, bubbleWrapper);
        }

        VBox messageBlock = new VBox(finalNode);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));
        row.setUserData(msg);

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