package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.EmojiHandler;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane; // [MỚI] Import StackPane
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class MainController {

    // --- FXML FIELDS ---
    @FXML public BorderPane mainBorderPane;
    @FXML public Label myDisplayName;
    @FXML public ImageView myAvatarView;
    @FXML public ListView<UserDTO> conversationList;
    @FXML public VBox chatArea, welcomeArea, msgContainer;

    @FXML public Label currentChatTitle;
    @FXML public TextField inputField;
    @FXML public ScrollPane msgScrollPane;
    @FXML public Button micBtn;
    @FXML public Button emojiBtn;
    @FXML public TextField searchMsgField;
    // [QUAN TRỌNG] Liên kết với ChatInfoController (bên phải)
    @FXML private ChatInfoController chatInfoController;

    // [MỚI] Container chứa thanh bên phải (để Bật/Tắt)
    @FXML private StackPane infoSidebarContainer;

    // --- DATA FIELDS ---
    public P2PClient p2pClient;
    public UserDTO currentChatUser;
    public long activeConversationId = -1;
    public boolean isUpdatingList = false;
    public final Map<String, VBox> messageUiMap = new HashMap<>();

    // --- MANAGERS ---
    private ChatManager chatManager;
    private ContactManager contactManager;
    private CallHandler callHandler;
    private NavigationHandler navigationHandler;
    private RealTimeHandler realTimeHandler;

    @FXML
    public void initialize() {
        // 1. Khởi tạo các Manager
        this.contactManager = new ContactManager(this);
        this.chatManager = new ChatManager(this);
        this.callHandler = new CallHandler(this);
        this.navigationHandler = new NavigationHandler(this);

        // 2. Setup UI cơ bản
        conversationList.setCellFactory(param -> new FriendListCell());
        ChatUIHelper.setMainController(this);

        // [MỚI] Mặc định ẩn thanh thông tin bên phải đi (Để giao diện chỉ có 2 phần)
        if (mainBorderPane != null) {
            mainBorderPane.setRight(null);
        }

        // 3. Load thông tin User (Me)
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            loadMyAvatar(me.getAvatarUrl());
            startP2P();
            contactManager.loadFriendListInitial();
            registerRealTimeUpdates();
        }

        // 4. Xử lý sự kiện click vào List Conversation
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingList && newVal != null) {
                this.currentChatUser = newVal;
                chatManager.switchChat(newVal);

                new Thread(() -> {
                    try {
                        long conversationId;
                        if ("GROUP".equals(newVal.getUsername())) {
                            conversationId = newVal.getId();
                        } else {
                            long myId = SessionStore.currentUser.getId();
                            conversationId = RmiClient.getMessageService().getPrivateConversationId(myId, newVal.getId());
                        }

                        // [MỚI - QUAN TRỌNG] Lưu lại ID để dùng cho Real-time
                        this.activeConversationId = conversationId;

                        // Lấy màu và áp dụng
                        String themeColor = RmiClient.getMessageService().getConversationTheme(conversationId);
                        Platform.runLater(() -> applyThemeColor(themeColor));

                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
                // ---------------------------------------

                // C. Cập nhật thanh thông tin bên phải (nếu đang mở)
                if (mainBorderPane.getRight() != null && chatInfoController != null) {
                    chatInfoController.setUserInfo(newVal);
                }
            }
        });

        // 5. Xử lý sự kiện nút Emoji
        if (emojiBtn != null) {
            emojiBtn.setOnAction(e -> {
                EmojiHandler.showEmojiPopup(emojiBtn, (emoji) -> {
                    inputField.appendText(emoji);
                });
            });
        }
        if (searchMsgField != null) {
            searchMsgField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterMessages(newVal);
            });
        }
    }

    // --- P2P & NETWORK SETUP ---
    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void registerRealTimeUpdates() {
        try {
            realTimeHandler = new RealTimeHandler(this);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- HÀM NHẬN TIN NHẮN ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ ||
                    msg.getType() == MessageDTO.MessageType.CALL_ACCEPT ||
                    msg.getType() == MessageDTO.MessageType.CALL_DENY ||
                    msg.getType() == MessageDTO.MessageType.CALL_END) {

                callHandler.handleCallSignal(msg);

            } else {
                chatManager.handleIncomingMessage(msg);

                if (msg.getContent() != null) {
                    if (msg.getContent().contains("đã đổi tên nhóm") || msg.getContent().contains("đã thay đổi ảnh")) {
                        new Thread(() -> {
                            try {
                                UserDTO updatedGroup = RmiClient.getDirectoryService().getUserInfo(msg.getConversationId());
                                if (updatedGroup != null) {
                                    Platform.runLater(() -> updateFriendInList(updatedGroup));
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }).start();
                    }
                    if (msg.getContent().contains("đã giải tán nhóm")) {
                        handleGroupLeft(msg.getConversationId());
                    }
                }
            }
        });
    }

    // --- DELEGATE METHODS ---
    @FXML public void handleSend() { chatManager.handleSend(); }
    @FXML public void handleSendFile() { chatManager.handleSendFile(); }
    @FXML public void startRecording(MouseEvent event) { chatManager.startRecording(event); }
    @FXML public void stopAndSendAudio(MouseEvent event) { chatManager.stopAndSendAudio(event); }

    @FXML public void handleVoiceCall() { callHandler.handleVoiceCall(); }

    @FXML public void handleCreateGroup() { navigationHandler.handleCreateGroup(); }
    @FXML public void handleAddFriend() { navigationHandler.handleAddFriend(); }
    @FXML public void handleShowRequests() { navigationHandler.handleShowRequests(); }
    @FXML public void handleOpenProfile() { navigationHandler.handleOpenProfile(); }

    // [MỚI] Xử lý Bật/Tắt thanh thông tin (Sidebar phải)
    @FXML
    public void handleToggleInfo() {
        // Kiểm tra xem cột phải đang hiện hay ẩn
        if (mainBorderPane.getRight() == null) {
            // Nếu đang ẩn -> Hiện nó ra
            mainBorderPane.setRight(infoSidebarContainer);

            // Cập nhật thông tin ngay lập tức
            if (currentChatUser != null && chatInfoController != null) {
                chatInfoController.setUserInfo(currentChatUser);
            }
        } else {
            // Nếu đang hiện -> Ẩn nó đi
            mainBorderPane.setRight(null);
        }
    }

    // --- HELPER METHODS ---
    public void handleEditAction(MessageDTO msg) { chatManager.handleEditAction(msg); }
    public void handleRecallAction(MessageDTO msg) { chatManager.handleRecallAction(msg); }

    public void loadMyAvatar(String url) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                byte[] data = RmiClient.getMessageService().downloadFile(url);
                if (data != null) {
                    Image img = new Image(new ByteArrayInputStream(data));
                    Platform.runLater(() -> {
                        myAvatarView.setImage(img);
                        double r = myAvatarView.getFitWidth() / 2;
                        Circle clip = new Circle(r, r, r);
                        myAvatarView.setClip(clip);
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    public void handleGroupLeft(long groupId) {
        getContactManager().removeConversation(groupId);
        if (activeConversationId == groupId) {
            welcomeArea.setVisible(true);
            chatArea.setVisible(false);
            currentChatUser = null;
            activeConversationId = -1;
            mainBorderPane.setRight(null);
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Nhóm đã giải tán hoặc bạn đã rời nhóm.");
        a.show();
    }

    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }

    // --- GETTERS ---
    public ContactManager getContactManager() { return contactManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CallHandler getCallHandler() { return callHandler; }
    public NavigationHandler getNavigationHandler() { return navigationHandler; }

    public void updateFriendInList(UserDTO friend) { contactManager.updateFriendInList(friend); }
    public void addFriendToListDirectly(UserDTO friend) { contactManager.addFriendToListDirectly(friend); }
    public void handleEndCallSignal() { callHandler.handleEndCallSignal(); }
    // [MỚI] Hàm lọc tin nhắn
    private void filterMessages(String keyword) {
        if (msgContainer == null) return;
        String search = (keyword == null) ? "" : keyword.toLowerCase().trim();

        for (javafx.scene.Node node : msgContainer.getChildren()) {
            if (node instanceof javafx.scene.layout.HBox) {
                javafx.scene.layout.HBox row = (javafx.scene.layout.HBox) node;

                // Lấy nội dung tin nhắn đã lưu ở Bước 1
                Object userData = row.getUserData();

                // Mặc định hiện tất cả
                boolean isVisible = true;

                // Nếu đang tìm kiếm và dòng này có chứa nội dung
                if (!search.isEmpty() && userData instanceof String) {
                    String content = (String) userData;
                    if (!content.contains(search)) {
                        isVisible = false; // Ẩn nếu không khớp từ khóa
                    }
                } else if (!search.isEmpty() && userData == null) {
                    // Ẩn các dòng không có nội dung text (ví dụ thông báo ngày tháng) khi đang tìm kiếm
                    // Hoặc giữ lại tùy bạn. Ở đây mình ẩn cho gọn.
                    isVisible = false;
                }

                row.setVisible(isVisible);
                row.setManaged(isVisible); // Co lại nếu ẩn
            }
        }
    }

    // [MỚI] Hàm để ChatInfoController gọi sang: Bật/Tắt thanh tìm kiếm
    public void toggleSearchMessage() {
        if (searchMsgField == null) return;

        boolean isShow = !searchMsgField.isVisible();
        searchMsgField.setVisible(isShow);
        searchMsgField.setManaged(isShow);

        if (isShow) {
            searchMsgField.requestFocus(); // Focus để gõ luôn
        } else {
            searchMsgField.clear(); // Xóa chữ và hiện lại tất cả tin nhắn khi tắt
        }
    }
    // [ĐÃ SỬA LỖI] Xử lý xóa tin nhắn phía tôi
    public void handleDeleteForMeAction(MessageDTO msg) {
        if (msg == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Tin nhắn này sẽ bị ẩn khỏi lịch sử chat của BẠN.\nNgười kia vẫn sẽ nhìn thấy. Bạn chắc chứ?");
        alert.setTitle("Xóa ở phía tôi");

        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        long myId = SessionStore.currentUser.getId();
                        // 1. Gọi Server lưu vào bảng hidden_messages
                        boolean success = RmiClient.getMessageService().deleteMessageForUser(myId, msg.getId());

                        if (success) {
                            Platform.runLater(() -> {
                                // 2. Xóa khỏi giao diện (Cách an toàn không bị ClassCastException)
                                VBox bubble = messageUiMap.get(msg.getUuid());

                                if (bubble != null) {
                                    // Bắt đầu từ bong bóng, leo ngược lên trên để tìm cái HBox (Row) nằm trong msgContainer
                                    javafx.scene.Node nodeToDelete = bubble;

                                    // Vòng lặp: Leo lên cha cho đến khi cha của nó chính là msgContainer
                                    while (nodeToDelete.getParent() != null && nodeToDelete.getParent() != msgContainer) {
                                        nodeToDelete = nodeToDelete.getParent();
                                    }

                                    // Nếu tìm thấy và cha nó đúng là msgContainer -> Xóa nó đi
                                    if (nodeToDelete != null && nodeToDelete.getParent() == msgContainer) {
                                        msgContainer.getChildren().remove(nodeToDelete);
                                        // Xóa khỏi map quản lý để giải phóng bộ nhớ
                                        messageUiMap.remove(msg.getUuid());
                                    }
                                } else {
                                    // Trường hợp hiếm: Không tìm thấy bong bóng -> Load lại cho chắc
                                    chatManager.switchChat(currentChatUser);
                                }
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    // Xử lý Ghim tin nhắn
    public void handlePinAction(MessageDTO msg) {
        new Thread(() -> {
            try {
                boolean newStatus = !msg.isPinned(); // Đảo trạng thái (Ghim <-> Bỏ ghim)
                boolean ok = RmiClient.getMessageService().pinMessage(msg.getId(), newStatus);
                if(ok) {
                    Platform.runLater(() -> {
                        msg.setPinned(newStatus);
                        Alert a = new Alert(Alert.AlertType.INFORMATION, newStatus ? "Đã ghim tin nhắn!" : "Đã bỏ ghim!");
                        a.show();
                    });
                }
            } catch(Exception e) { e.printStackTrace(); }
        }).start();
    }
    // [MỚI] Hàm cuộn tới tin nhắn và nháy màu
    public void scrollToMessage(String uuid) {
        VBox bubble = messageUiMap.get(uuid);
        if (bubble != null) {
            // 1. Tính toán cuộn
            // Lấy Node cha (HBox row) để tính vị trí chính xác hơn
            Node row = bubble.getParent().getParent().getParent();
            if (row != null) {
                double contentHeight = msgContainer.getBoundsInLocal().getHeight();
                double nodeY = row.getBoundsInParent().getMinY();
                double vValue = nodeY / contentHeight;
                msgScrollPane.setVvalue(vValue);
            }

            // 2. Hiệu ứng nháy vàng (Flash)
            // Xóa class cũ nếu có để reset hiệu ứng
            bubble.getStyleClass().remove("highlight-bubble");

            // Thêm class để kích hoạt animation
            Platform.runLater(() -> {
                bubble.getStyleClass().add("highlight-bubble");

                // Sau 2 giây tự tắt
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    Platform.runLater(() -> bubble.getStyleClass().remove("highlight-bubble"));
                }).start();
            });
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Tin nhắn này chưa được tải (nằm ở trang cũ).\nHãy cuộn lên để tải thêm!");
            alert.show();
        }
    }
    // Xử lý sự kiện Ghim/Bỏ ghim từ Server báo về (Real-time)
    public void handleRemoteMessageUpdate(String uuid, String actionType) {
        // 1. Tìm bong bóng chat đang hiển thị
        VBox bubble = messageUiMap.get(uuid);

        if (bubble != null) {
            boolean isPinned = "PIN".equals(actionType); // True nếu là hành động GHIM

            // 2. Cập nhật Giao diện (Thêm/Xóa viền vàng)
            if (isPinned) {
                if (!bubble.getStyleClass().contains("pinned-bubble")) {
                    bubble.getStyleClass().add("pinned-bubble");
                }
            } else {
                bubble.getStyleClass().remove("pinned-bubble");
            }

            // 3. [QUAN TRỌNG] Cập nhật dữ liệu ngầm (MessageDTO)
            // Để lần sau bạn chuột phải, menu sẽ hiện đúng là "Bỏ ghim" hay "Ghim"
            if (bubble.getUserData() instanceof MessageDTO) {
                MessageDTO msg = (MessageDTO) bubble.getUserData();
                msg.setPinned(isPinned);
            }
        }

        // 4. Nếu đang mở Sidebar "Tin nhắn đã ghim", hãy refresh lại nó nếu cần
        // (Hiện tại Sidebar của bạn dùng nút bấm để tải lại nên không cần auto-refresh cũng được)
    }
    // [THÊM MỚI] Hàm áp dụng màu nền cho khung chat
    public void applyThemeColor(String hexColor) {
        if (chatArea != null) {
            // Đổi màu nền của VBox chatArea (hoặc msgScrollPane tùy giao diện bạn)
            chatArea.setStyle("-fx-background-color: " + hexColor + ";");

            // Nếu màu tối quá thì đổi chữ thành màu trắng, sáng thì chữ đen (Tùy chọn nâng cao)
        }
    }
    public void handleRemoteThemeUpdate(long conversationId, String newColor) {
        // Kiểm tra: Nếu tin báo đổi màu trùng với cuộc hội thoại đang mở -> Đổi luôn
        if (this.activeConversationId == conversationId) {
            applyThemeColor(newColor);
        }
    }
}