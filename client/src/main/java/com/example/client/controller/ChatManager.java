package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.scene.input.MouseEvent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

public class ChatManager {
    private final MainController mc;
    private final AudioHelper audioRecorder = new AudioHelper();
    private long recordingStartTime;

    public ChatManager(MainController mc) {
        this.mc = mc;
    }

    public void handleIncomingMessage(MessageDTO msg) {
        // 1. Xử lý Recall / Edit (Giữ nguyên)
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            if (mc.messageUiMap.containsKey(msg.getUuid())) {
                VBox bubble = mc.messageUiMap.get(msg.getUuid());
                ChatUIHelper.updateBubbleContent(bubble, "🚫 Tin nhắn đã thu hồi", true);
            }
            return;
        }
        if (msg.getType() == MessageDTO.MessageType.EDIT) {
            if (mc.messageUiMap.containsKey(msg.getUuid())) {
                VBox bubble = mc.messageUiMap.get(msg.getUuid());
                ChatUIHelper.updateBubbleContent(bubble, msg.getContent(), false);
            }
            return;
        }

        // 2. Xử lý tin nhắn mới (Giữ nguyên)
        if (mc.activeConversationId != -1 && msg.getConversationId() == mc.activeConversationId) {
            VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, false);
            if (msg.getUuid() != null && bubble != null) {
                mc.messageUiMap.put(msg.getUuid(), bubble);
            }
            new Thread(() -> {
                try {
                    RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        // 3. Update sidebar (Giữ nguyên)
        mc.getContactManager().moveUserToTop(msg);

        // ========================================================================
        // [ĐÃ SỬA] LOGIC BẮT SỰ KIỆN ĐỔI TÊN CHO CẢ 2 PHÍA (MÌNH VÀ NGƯỜI KHÁC)
        // ========================================================================
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION && msg.getContent() != null) {
            String content = msg.getContent();

            if (content.contains("đổi tên nhóm") || content.contains("đổi biệt danh") || content.contains("đổi tên hiển thị")) {
                try {
                    String newName = "";
                    int index = content.lastIndexOf("thành");
                    if (index != -1) {
                        String temp = content.substring(index + 5);
                        if (temp.startsWith(":")) temp = temp.substring(1);
                        newName = temp.trim();
                    }

                    if (!newName.isEmpty()) {
                        final String finalName = newName;

                        // --- TRƯỜNG HỢP 1: ĐỔI TÊN NHÓM ---
                        if (content.contains("đổi tên nhóm")) {
                            long groupId = msg.getConversationId();

                            // 1. Cập nhật danh sách bên trái
                            UserDTO groupUpdate = new UserDTO();
                            groupUpdate.setId(groupId);
                            groupUpdate.setDisplayName(finalName);
                            groupUpdate.setUsername("GROUP");
                            mc.getContactManager().updateFriendInList(groupUpdate);

                            // 2. GỌI HÀM BÊN MAIN CONTROLLER ĐỂ CẬP NHẬT HEADER & SIDEBAR
                            // [ĐÂY LÀ DÒNG BẠN ĐANG THIẾU]
                            mc.updateCurrentChatName(groupId, finalName);
                        }

                        // --- TRƯỜNG HỢP 2: ĐỔI BIỆT DANH THÀNH VIÊN ---
                        else {
                            long userId = msg.getSenderId();

                            // 1. Cập nhật danh sách bên trái
                            UserDTO userUpdate = new UserDTO();
                            userUpdate.setId(userId);
                            userUpdate.setDisplayName(finalName);
                            mc.getContactManager().updateFriendInList(userUpdate);

                            // 2. Cập nhật tên trên các tin nhắn cũ
                            if (mc.activeConversationId == msg.getConversationId()) {
                                mc.updateSenderNameInUI(userId, finalName);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public void sendP2PMessage(MessageDTO msg) {
        UserDTO targetCache = mc.currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        // 1. Gửi P2P
        if (isGroup) {
            try {
                List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(mc.activeConversationId);
                for (Long memId : memberIds) {
                    if (memId == SessionStore.currentUser.getId()) continue;
                    UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                    if (memInfo != null && memInfo.isOnline()) {
                        mc.p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                    }
                }
            } catch (Exception e) { System.err.println("Lỗi gửi Group P2P: " + e.getMessage()); }
        } else {
            if (targetCache != null && targetCache.getLastIp() != null) {
                mc.p2pClient.send(targetCache.getLastIp(), targetCache.getLastPort(), msg);
            }
        }

        // 2. Cập nhật UI
        if (msg.getType() != MessageDTO.MessageType.RECALL &&
                msg.getType() != MessageDTO.MessageType.EDIT &&
                msg.getType() != MessageDTO.MessageType.CALL_REQ &&
                msg.getType() != MessageDTO.MessageType.CALL_ACCEPT &&
                msg.getType() != MessageDTO.MessageType.CALL_DENY &&
                msg.getType() != MessageDTO.MessageType.CALL_END) {

            VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, true);
            if (bubble != null && msg.getUuid() != null) {
                mc.messageUiMap.put(msg.getUuid(), bubble);
            }
        }

        mc.getContactManager().moveUserToTop(msg);

        // 3. Backup Server (ĐÃ SỬA LỖI MẤT VOICE CHAT)
        if (msg.getType() == MessageDTO.MessageType.RECALL || msg.getType() == MessageDTO.MessageType.EDIT) {
            return;
        }

        new Thread(() -> {
            try {
                MessageDTO backupMsg = new MessageDTO();
                backupMsg.setConversationId(msg.getConversationId());
                backupMsg.setSenderId(msg.getSenderId());
                backupMsg.setCreatedAt(msg.getCreatedAt());
                backupMsg.setContent(msg.getContent());
                backupMsg.setType(msg.getType());
                backupMsg.setUuid(msg.getUuid());

                // Logic lưu file chuẩn chỉ
                if (msg.getAttachmentUrl() != null && !msg.getAttachmentUrl().isEmpty()) {
                    // Nếu đã có URL (đã upload từ hàm stopAndSendAudio), dùng luôn
                    backupMsg.setAttachmentUrl(msg.getAttachmentUrl());
                }
                else if (msg.getFileData() != null && msg.getType() != MessageDTO.MessageType.TEXT) {
                    String fName = msg.getFileName();

                    // [FIX] Nếu không có tên file, tự tạo tên mới
                    if (fName == null || fName.isEmpty()) {
                        fName = "file_" + System.currentTimeMillis();
                    }

                    // [FIX QUAN TRỌNG] Bắt buộc thêm đuôi .wav cho tin nhắn thoại
                    // Nếu không có bước này, khi tải lại lịch sử sẽ bị lỗi không phát được
                    if (msg.getType() == MessageDTO.MessageType.AUDIO && !fName.endsWith(".wav")) {
                        fName += ".wav";
                    }

                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);
                    backupMsg.setAttachmentUrl(serverPath);
                }

                // Không lưu file data vào DB để giảm tải
                backupMsg.setFileData(null);

                RmiClient.getMessageService().saveMessage(backupMsg);
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
            } catch (Exception e) {
                System.err.println(">> Client: Lỗi Backup Server: " + e.getMessage());
            }
        }).start();
    }

    public void handleSend() {
        String text = mc.inputField.getText().trim();
        if (text.isEmpty() || mc.currentChatUser == null) return;
        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(mc.activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent(text);
        msg.setType(MessageDTO.MessageType.TEXT);
        sendP2PMessage(msg);
        mc.inputField.clear();
    }

    public void switchChat(UserDTO friendOrGroup) {
        if (friendOrGroup == null) return;

        if (mc.searchMsgField != null && mc.searchMsgField.isVisible()) {
            mc.toggleSearchMessage();
        }

        mc.currentChatUser = friendOrGroup;

        // 1. Cập nhật Giao diện cơ bản (Tên, ẩn Welcome, hiện Chat)
        mc.welcomeArea.setVisible(false);
        mc.chatArea.setVisible(true);
        mc.currentChatTitle.setText(friendOrGroup.getDisplayName());

        // Reset Avatar tạm thời và xóa tin nhắn cũ
        mc.currentChatTitle.setGraphic(null);
        mc.msgContainer.getChildren().clear();
        mc.messageUiMap.clear(); // Xóa map tin nhắn cũ để giải phóng bộ nhớ

        // Reset count tin nhắn chưa đọc trên giao diện
        friendOrGroup.setUnreadCount(0);
        mc.conversationList.refresh();

        // 2. Load Avatar (Tối ưu: Resize ảnh nhỏ 50x50 để nhẹ RAM)
        if (friendOrGroup.getAvatarUrl() != null && !friendOrGroup.getAvatarUrl().isEmpty()) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(friendOrGroup.getAvatarUrl());
                    if (data != null) {
                        // [TỐI ƯU] Resize ảnh về 50x50px
                        Image img = new Image(new ByteArrayInputStream(data), 50, 50, true, true);
                        Platform.runLater(() -> {
                            Circle avatarCircle = new Circle(18);
                            avatarCircle.setFill(new ImagePattern(img));
                            mc.currentChatTitle.setGraphic(avatarCircle);
                            mc.currentChatTitle.setGraphicTextGap(10);
                        });
                    }
                } catch (Exception e) {}
            }).start();
        }

        // 3. Lấy ID hội thoại và Tải tin nhắn
        new Thread(() -> {
            try {
                long conversationId;

                // Nếu là Nhóm -> ID chính là ID UserDTO
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    conversationId = friendOrGroup.getId();
                } else {
                    // Nếu là 1-1 -> Gọi Server lấy ID riêng
                    conversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }

                // [QUAN TRỌNG] Cập nhật ID vào MainController để ChatInfoController dùng (cho chức năng Ghim)
                mc.activeConversationId = conversationId;

                // Đánh dấu đã đọc
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), conversationId);

                // Tải lịch sử tin nhắn
                loadHistory(conversationId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    private void loadHistory(long conversationId) {
        try {
            long myId = SessionStore.currentUser.getId();
            List<MessageDTO> history = RmiClient.getMessageService()
                    .getMessagesInConversation(conversationId, myId);

            Platform.runLater(() -> {
                mc.msgContainer.getChildren().clear();
                mc.messageUiMap.clear();

                for (MessageDTO msg : history) {
                    // [LOGIC MỚI] Quét lịch sử để cập nhật biệt danh cho người vào sau
                    if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
                        checkAndProcessNicknameChange(msg);
                    }

                    boolean isMe = msg.getSenderId() == myId;
                    VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, isMe);

                    if (msg.getUuid() != null && bubble != null) {
                        mc.messageUiMap.put(msg.getUuid(), bubble);
                    }
                }
                scrollToBottom();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm phụ trợ để xử lý đổi tên từ lịch sử
    private void checkAndProcessNicknameChange(MessageDTO msg) {
        String content = msg.getContent();
        if (content != null && (content.contains("đã đổi biệt danh") || content.contains("đã đổi tên hiển thị"))) {
            try {
                int index = content.lastIndexOf("thành");
                if (index != -1) {
                    String newName = content.substring(index + 5).replace(":", "").trim();
                    if (!newName.isEmpty()) {
                        // Cập nhật vào cache danh sách bạn bè
                        UserDTO u = new UserDTO();
                        u.setId(msg.getSenderId());
                        u.setDisplayName(newName);
                        mc.getContactManager().updateFriendInList(u);

                        // Không cần gọi updateSenderNameInUI ở đây vì các tin nhắn đang được vẽ ra lần lượt
                    }
                }
            } catch (Exception e) {}
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            mc.msgContainer.layout();
            mc.msgScrollPane.layout();
            Platform.runLater(() -> mc.msgScrollPane.setVvalue(1.0));
        });
    }

    public void handleSendFile() {
        if (mc.currentChatUser == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tất cả file", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(mc.mainBorderPane.getScene().getWindow());

        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(mc.activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setFileData(fileBytes);
                    msg.setFileName(selectedFile.getName());

                    String lowerName = selectedFile.getName().toLowerCase();
                    if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                            lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")) {
                        msg.setType(MessageDTO.MessageType.IMAGE);
                        msg.setContent("[Hình ảnh]");
                    } else {
                        msg.setType(MessageDTO.MessageType.FILE);
                        msg.setContent("[Tập tin] " + selectedFile.getName());
                    }
                    sendP2PMessage(msg);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    public void startRecording(MouseEvent event) {
        if (mc.currentChatUser == null) return;
        recordingStartTime = System.currentTimeMillis();
        mc.micBtn.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");
        audioRecorder.startRecording();
    }

    public void stopAndSendAudio(MouseEvent event) {
        if (mc.currentChatUser == null) return;

        // Reset giao diện nút mic
        mc.micBtn.setStyle("-fx-text-fill: #667eea; -fx-font-size: 20px;");

        // Kiểm tra thời lượng (tránh click nhầm quá nhanh)
        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording();
            return;
        }

        byte[] audioData = audioRecorder.stopRecording();

        if (audioData != null && audioData.length > 0) {
            // Chạy trong luồng riêng để không đơ giao diện khi upload
            new Thread(() -> {
                try {
                    // 1. Tạo tên file chuẩn (QUAN TRỌNG: Phải có đuôi .wav)
                    String fileName = "voice_" + System.currentTimeMillis() + ".wav";

                    // 2. UPLOAD LÊN SERVER TRƯỚC (Chắc chắn lưu rồi mới gửi)
                    // Hàm này sẽ trả về đường dẫn file trên server (ví dụ: /uploads/voice_123.wav)
                    String serverUrl = RmiClient.getMessageService().uploadFile(audioData, fileName);

                    if (serverUrl == null) {
                        System.err.println("Upload Voice thất bại!");
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Lỗi mạng: Không thể gửi ghi âm!").show());
                        return;
                    }

                    // 3. Đóng gói tin nhắn
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(mc.activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setType(MessageDTO.MessageType.AUDIO);
                    msg.setContent("[Tin nhắn thoại]");

                    // Gắn dữ liệu quan trọng
                    msg.setFileData(audioData);       // Để hiện ngay bên mình
                    msg.setFileName(fileName);        // Tên file
                    msg.setAttachmentUrl(serverUrl);  // URL server (Để người nhận tải nếu P2P lỗi & Lưu lịch sử)

                    // 4. Gửi đi
                    sendP2PMessage(msg);

                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Lỗi gửi Voice: " + e.getMessage()).show());
                }
            }).start();
        }
    }

    public void handleEditAction(MessageDTO targetMsg) {
        TextInputDialog dialog = new TextInputDialog(targetMsg.getContent());
        dialog.setTitle("Chỉnh sửa tin nhắn");
        dialog.setHeaderText(null);
        dialog.setContentText("Nội dung mới:");
        dialog.showAndWait().ifPresent(newContent -> {
            MessageDTO editMsg = new MessageDTO();
            editMsg.setType(MessageDTO.MessageType.EDIT);
            editMsg.setUuid(targetMsg.getUuid());
            editMsg.setContent(newContent);
            editMsg.setConversationId(mc.activeConversationId);
            editMsg.setSenderId(SessionStore.currentUser.getId());
            sendP2PMessage(editMsg);

            if (mc.messageUiMap.containsKey(targetMsg.getUuid())) {
                ChatUIHelper.updateBubbleContent(mc.messageUiMap.get(targetMsg.getUuid()), newContent, false);
            }
            new Thread(() -> {
                try {
                    RmiClient.getMessageService().updateMessage(targetMsg.getUuid(), newContent, MessageDTO.MessageType.EDIT);
                } catch(Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    public void handleRecallAction(MessageDTO targetMsg) {
        MessageDTO recallMsg = new MessageDTO();
        recallMsg.setType(MessageDTO.MessageType.RECALL);
        recallMsg.setUuid(targetMsg.getUuid());
        recallMsg.setConversationId(mc.activeConversationId);
        recallMsg.setSenderId(SessionStore.currentUser.getId());
        sendP2PMessage(recallMsg);

        if (mc.messageUiMap.containsKey(targetMsg.getUuid())) {
            ChatUIHelper.updateBubbleContent(mc.messageUiMap.get(targetMsg.getUuid()), "🚫 Tin nhắn đã thu hồi", true);
        }
        new Thread(() -> {
            try {
                RmiClient.getMessageService().updateMessage(targetMsg.getUuid(), null, MessageDTO.MessageType.RECALL);
            } catch(Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void sendReaction(MessageDTO msg, String emoji) {
    }
    // ==========================================================
    // [MỚI] XỬ LÝ KHO LƯU TRỮ (MEDIA & FILES)
    // ==========================================================

    // 1. Hàm mở Kho Ảnh
    public void openImageRepository() {
        showRepositoryDialog("Kho Ảnh", MessageDTO.MessageType.IMAGE);
    }

    // 2. Hàm mở Kho Tài Liệu
    public void openFileRepository() {
        showRepositoryDialog("Kho Tài Liệu", MessageDTO.MessageType.FILE);
    }

    // 3. Hàm chung để hiển thị Dialog danh sách
    private void showRepositoryDialog(String title, MessageDTO.MessageType targetType) {
        if (mc.activeConversationId == -1) return;

        // Tạo Dialog
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Danh sách " + title);

        // Nút đóng
        javafx.scene.control.ButtonType closeButton = new javafx.scene.control.ButtonType("Đóng", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);

        // List view để hiện file
        javafx.scene.control.ListView<MessageDTO> listView = new javafx.scene.control.ListView<>();
        listView.setPrefSize(400, 500);

        // Fetch dữ liệu từ Server (Lấy lịch sử chat)
        new Thread(() -> {
            try {
                long myId = SessionStore.currentUser.getId();
                // Lấy toàn bộ tin nhắn của hội thoại hiện tại
                List<MessageDTO> allMsgs = RmiClient.getMessageService()
                        .getMessagesInConversation(mc.activeConversationId, myId);

                // Lọc ra các tin nhắn đúng loại (Ảnh hoặc File)
                List<MessageDTO> filteredList = allMsgs.stream()
                        .filter(msg -> msg.getType() == targetType && msg.getAttachmentUrl() != null)
                        .toList();

                Platform.runLater(() -> {
                    listView.getItems().addAll(filteredList);
                    if (filteredList.isEmpty()) {
                        dialog.setHeaderText("Trống! Chưa có " + title + " nào.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Tùy chỉnh cách hiển thị từng dòng trong List
        listView.setCellFactory(param -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(MessageDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Hiện tên file và ngày gửi
                    String name = item.getFileName();
                    if (name == null || name.isEmpty()) name = (targetType == MessageDTO.MessageType.IMAGE) ? "Hình ảnh" : "Tài liệu";

                    String time = item.getCreatedAt() != null ?
                            " (" + item.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ")" : "";

                    setText("📂 " + name + time);
                    setStyle("-fx-padding: 10; -fx-font-size: 14px;");
                }
            }
        });

        // Sự kiện khi click vào file -> Tải về
        listView.setOnMouseClicked(event -> {
            MessageDTO selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) { // Double click để tải
                downloadFileFromRepo(selected);
            }
        });

        dialog.getDialogPane().setContent(listView);
        dialog.show();
    }

    // 4. Hàm tải file khi chọn từ kho
    private void downloadFileFromRepo(MessageDTO msg) {
        String fName = msg.getFileName() != null ? msg.getFileName() : "downloaded_file";
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fName);
        fileChooser.setTitle("Lưu file về máy");
        File file = fileChooser.showSaveDialog(mc.mainBorderPane.getScene().getWindow());

        if (file != null) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                    if (data != null) {
                        Files.write(file.toPath(), data);
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Tải xuống thành công!\n" + file.getAbsolutePath());
                            alert.show();
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Lỗi tải file: " + e.getMessage()).show());
                }
            }).start();
        }
    }
}