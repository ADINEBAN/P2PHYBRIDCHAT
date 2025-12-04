package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback; // Nhớ import cái này

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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

    // QUAN TRỌNG: Giữ tham chiếu Callback để không bị Garbage Collector dọn mất
    private ClientCallback myCallback;

    @FXML
    public void initialize() {
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // 1. Khởi động P2P Server (Lắng nghe tin nhắn trực tiếp)
            startP2P();

            // 2. Tải danh sách bạn bè ban đầu (Lần đầu tiên phải tải thủ công)
            loadFriendListInitial();

            // 3. ĐĂNG KÝ NHẬN THÔNG BÁO REAL-TIME (Cơ chế mới)
            registerRealTimeUpdates();
        }

        // Sự kiện chọn bạn để chat
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    // --- CƠ CHẾ REAL-TIME: SERVER GỌI VỀ CLIENT ---
    private void registerRealTimeUpdates() {
        try {
            // Định nghĩa hành động khi Server gọi về: Cập nhật giao diện
            myCallback = new ClientCallback() {
                @Override
                public void onFriendStatusChange(UserDTO friend) throws RemoteException {
                    // Cập nhật UI bắt buộc phải chạy trên luồng JavaFX
                    Platform.runLater(() -> updateFriendInList(friend));
                }
            };

            // Export object này ra cổng ngẫu nhiên (0) để Server có thể gọi tới
            UnicastRemoteObject.exportObject(myCallback, 0);

            // Gửi "cái loa" này lên Server
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), myCallback);
            System.out.println("Đã đăng ký nhận thông báo Real-time với Server.");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi đăng ký Real-time: " + e.getMessage());
        }
    }

    // Hàm thông minh để cập nhật danh sách mà không cần reload toàn bộ
    private void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;

        // 1. Tìm xem người này có trong list chưa
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == updatedFriend.getId()) {
                // Có rồi -> Cập nhật trạng thái mới
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                found = true;
                break;
            }
        }

        // 2. Nếu chưa có (bạn mới hoặc mới online lần đầu) -> Thêm vào ĐẦU danh sách
        if (!found) {
            conversationList.getItems().add(0, updatedFriend);
        }

        // 3. Refresh lại ListView để nó vẽ lại (quan trọng để hiện chấm xanh/xám)
        conversationList.refresh();
    }
    // ---------------------------------------------

    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                // Gọi RMI lấy danh sách bạn bè hiện tại
                List<UserDTO> friends = RmiClient.getChatService().getFriendList(SessionStore.currentUser.getId());

                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void switchChat(UserDTO friend) {
        this.currentChatUser = friend;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friend.getDisplayName());
        msgContainer.getChildren().clear();

        loadHistory();
    }

    private void loadHistory() {
        new Thread(() -> {
            try {
                // Tạm thời fix conversation ID = 1 để test
                List<MessageDTO> history = RmiClient.getChatService().getHistory(1);

                Platform.runLater(() -> {
                    for (MessageDTO msg : history) {
                        boolean isMe = msg.getSenderId() == SessionStore.currentUser.getId();
                        addMessageBubble(msg.getContent(), isMe);
                    }
                    msgScrollPane.setVvalue(1.0); // Cuộn xuống cuối
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentChatUser == null) return;

        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setContent(text);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());

        // 1. GỬI P2P
        new Thread(() -> {
            try {
                // Lấy thông tin mới nhất của bạn bè từ UI (đã được Real-time cập nhật)
                // Hoặc chắc ăn hơn thì gọi getUserInfo từ Server
                if (currentChatUser.isOnline()) {
                    p2pClient.send(currentChatUser.getLastIp(), currentChatUser.getLastPort(), msg);
                } else {
                    System.out.println("Bạn đang offline, tin nhắn sẽ được lưu vào Server.");
                }

                // 2. LƯU SERVER
                RmiClient.getChatService().saveMessage(msg);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // 3. HIỆN UI
        addMessageBubble(text, true);
        inputField.clear();
    }

    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> addMessageBubble(msg.getContent(), false));
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