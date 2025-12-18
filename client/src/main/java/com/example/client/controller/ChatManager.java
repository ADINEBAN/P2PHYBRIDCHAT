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
        // 1. Xử lý Recall / Edit
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

        // 2. Xử lý tin nhắn mới
        if (mc.activeConversationId != -1 && msg.getConversationId() == mc.activeConversationId) {
            // [QUAN TRỌNG] ChatUIHelper sẽ lo việc hiển thị Avatar bên cạnh tin nhắn
            VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, false);
            if (msg.getUuid() != null && bubble != null) {
                mc.messageUiMap.put(msg.getUuid(), bubble);
            }
            // Mark read
            new Thread(() -> {
                try {
                    RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        // 3. Update sidebar
        mc.getContactManager().moveUserToTop(msg);
    }

    public void sendP2PMessage(MessageDTO msg) {
        UserDTO targetCache = mc.currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        // 1. Gửi mạng
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

        // 2. Cập nhật UI (Chỉ tin nhắn mới)
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

        // 3. Backup Server
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

                if (msg.getFileData() != null && msg.getType() != MessageDTO.MessageType.TEXT) {
                    String fName = msg.getFileName() != null ? msg.getFileName() : "file_" + System.currentTimeMillis();
                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);
                    backupMsg.setAttachmentUrl(serverPath);
                    backupMsg.setFileData(null);
                }
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
            // Lấy ID của mình để Server biết đường lọc bỏ những tin nhắn mình đã "Xóa phía tôi"
            long myId = SessionStore.currentUser.getId();

            // [SỬA LẠI] Gọi hàm mới: getMessagesInConversation
            List<MessageDTO> history = RmiClient.getMessageService()
                    .getMessagesInConversation(conversationId, myId);

            Platform.runLater(() -> {
                // Xóa tin nhắn cũ trên giao diện
                mc.msgContainer.getChildren().clear();
                mc.messageUiMap.clear();

                for (MessageDTO msg : history) {
                    boolean isMe = msg.getSenderId() == myId;

                    // Vẽ bong bóng chat
                    VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, isMe);

                    // Lưu lại tham chiếu UI để sau này xử lý sự kiện (Ghim, Xóa, Sửa)
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
        mc.micBtn.setStyle("-fx-text-fill: #667eea; -fx-font-size: 20px;");
        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording();
            return;
        }
        byte[] audioData = audioRecorder.stopRecording();
        if (audioData != null) {
            new Thread(() -> {
                MessageDTO msg = new MessageDTO();
                msg.setSenderId(SessionStore.currentUser.getId());
                msg.setSenderName(SessionStore.currentUser.getDisplayName());
                msg.setConversationId(mc.activeConversationId);
                msg.setCreatedAt(LocalDateTime.now());
                msg.setType(MessageDTO.MessageType.AUDIO);
                msg.setFileData(audioData);
                msg.setContent("[Tin nhắn thoại]");
                sendP2PMessage(msg);
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
}