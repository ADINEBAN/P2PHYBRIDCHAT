package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.List;

public class MainController {

    @FXML private Label myDisplayName;
    @FXML private ListView<UserDTO> conversationList;
    @FXML private VBox chatArea, welcomeArea, msgContainer;
    @FXML private Label currentChatTitle;
    @FXML private TextField inputField;
    @FXML private ScrollPane msgScrollPane;

    private P2PClient p2pClient;
    private UserDTO currentChatUser;

    // Lưu ID hội thoại đang mở để kiểm tra tin nhắn đến
    private long activeConversationId = -1;

    private ClientCallback myCallback;

    @FXML
    public void initialize() {
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // 1. Khởi động P2P
            startP2P();

            // 2. Tải danh sách bạn bè/nhóm lần đầu
            loadFriendListInitial();

            // 3. Đăng ký nhận thông báo Real-time
            registerRealTimeUpdates();
        }

        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    private void registerRealTimeUpdates() {
        try {
            // Định nghĩa hành động khi Server báo tin
            myCallback = new ClientCallback() {
                @Override
                public void onFriendStatusChange(UserDTO friend) throws RemoteException {
                    Platform.runLater(() -> updateFriendInList(friend));
                }

                // [MỚI] Xử lý khi có lời mời kết bạn mới
                @Override
                public void onNewFriendRequest(UserDTO sender) throws RemoteException {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Thông báo");
                        alert.setHeaderText("Lời mời kết bạn mới!");
                        alert.setContentText(sender.getDisplayName() + " (" + sender.getUsername() + ") muốn kết bạn với bạn.");
                        alert.show();
                    });
                }

                // [MỚI] Xử lý khi lời mời của mình được chấp nhận
                @Override
                public void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Tin vui");
                        alert.setHeaderText(null);
                        alert.setContentText(newFriend.getDisplayName() + " đã chấp nhận lời mời kết bạn!");
                        alert.show();

                        // Thêm ngay người bạn mới vào danh sách mà không cần reload
                        // (Giả sử bạn ấy đang online để hiện chấm xanh luôn)
                        newFriend.setOnline(true);
                        updateFriendInList(newFriend);
                    });
                }
            };

            // Export object này ra để Server có thể gọi
            UnicastRemoteObject.exportObject(myCallback, 0);

            // Đăng ký với Server
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), myCallback);
            System.out.println("Đã đăng ký nhận thông báo Real-time.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                found = true;
                break;
            }
        }
        if (!found) conversationList.getItems().add(0, updatedFriend);
        conversationList.refresh();
    }

    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                // [THAY ĐỔI] Dùng FriendService để lấy danh sách
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());

                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void switchChat(UserDTO friendOrGroup) {
        this.currentChatUser = friendOrGroup;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friendOrGroup.getDisplayName());
        msgContainer.getChildren().clear();

        new Thread(() -> {
            try {
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    // Nếu là nhóm, ID của UserDTO chính là ID hội thoại
                    activeConversationId = friendOrGroup.getId();
                } else {
                    // [THAY ĐỔI] Dùng MessageService để lấy ID hội thoại riêng
                    activeConversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }

                loadHistory(activeConversationId);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void loadHistory(long conversationId) {
        try {
            // [THAY ĐỔI] Dùng MessageService để lấy lịch sử
            List<MessageDTO> history = RmiClient.getMessageService().getHistory(conversationId);

            Platform.runLater(() -> {
                msgContainer.getChildren().clear();
                for (MessageDTO msg : history) {
                    boolean isMe = msg.getSenderId() == SessionStore.currentUser.getId();
                    addMessageBubble(msg.getContent(), isMe);
                }
                msgScrollPane.setVvalue(1.0);
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleCreateGroup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create-group.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Tạo Nhóm");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleAddFriend() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/add-friend.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Thêm bạn bè");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentChatUser == null || activeConversationId == -1) return;

        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setContent(text);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(activeConversationId);

        boolean isGroup = "GROUP".equals(currentChatUser.getUsername());

        new Thread(() -> {
            try {
                if (isGroup) {
                    // Dùng GroupService lấy thành viên
                    List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(activeConversationId);

                    for (Long memId : memberIds) {
                        if (memId == SessionStore.currentUser.getId()) continue;

                        // [THAY ĐỔI] Dùng DirectoryService để tìm IP
                        UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                        if (memInfo != null && memInfo.isOnline()) {
                            p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                        }
                    }
                } else {
                    // [THAY ĐỔI] Dùng DirectoryService để tìm IP chat 1-1
                    UserDTO target = RmiClient.getDirectoryService().getUserInfo(currentChatUser.getId());
                    if (target != null && target.isOnline()) {
                        p2pClient.send(target.getLastIp(), target.getLastPort(), msg);
                    }
                }

                // [THAY ĐỔI] Dùng MessageService để lưu tin nhắn
                RmiClient.getMessageService().saveMessage(msg);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        addMessageBubble(text, true);
        inputField.clear();
    }

    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            // Chỉ hiện tin nhắn nếu đang mở đúng hội thoại đó
            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                addMessageBubble(msg.getContent(), false);
            }
        });
    }

    private void addMessageBubble(String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(350);
        label.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12;"
                : "-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12;");

        HBox container = new HBox(label);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        msgContainer.getChildren().add(container);
        Platform.runLater(() -> msgScrollPane.setVvalue(1.0));
    }
}