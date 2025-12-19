package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.EmojiHandler;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import java.io.IOException;
public class MainController {

    // --- FXML FIELDS ---
    @FXML public BorderPane mainBorderPane;
    @FXML public Label myDisplayName;
    @FXML public ImageView myAvatarView;

    // [SỬA LỖI] Dùng đúng tên biến khớp với fx:id trong FXML
    @FXML public ListView<UserDTO> conversationList;

    // Ô tìm kiếm bạn bè (Sidebar trái)
    @FXML private TextField txtSearchFriend;

    @FXML public VBox chatArea, welcomeArea, msgContainer;
    @FXML public Label currentChatTitle;
    @FXML public TextField inputField;
    @FXML public ScrollPane msgScrollPane;
    @FXML public Button micBtn;
    @FXML public Button emojiBtn;

    // Thanh tìm kiếm tin nhắn (Sidebar phải hoặc Popup)
    @FXML public TextField searchMsgField;

    // Controller Sidebar phải
    @FXML private ChatInfoController chatInfoController;
    @FXML private StackPane infoSidebarContainer;

    // --- [MỚI] SEARCH CONTROL (Tìm trong đoạn chat) ---
    @FXML private Label lblSearchCount; // Hiển thị "2/5"
    @FXML private Button btnSearchUp;   // Nút mũi tên lên
    @FXML private Button btnSearchDown; // Nút mũi tên xuống

    // Biến lưu trữ logic tìm kiếm tin nhắn
    private List<Node> searchMatches = new ArrayList<>();
    private int currentSearchIndex = -1;

    // --- DATA FIELDS ---
    public P2PClient p2pClient;
    public UserDTO currentChatUser;
    public long activeConversationId = -1;
    public boolean isUpdatingList = false;
    public final Map<String, VBox> messageUiMap = new HashMap<>();

    // --- LIST DATA (QUẢN LÝ DANH SÁCH BẠN BÈ) ---
    private ObservableList<UserDTO> masterData = FXCollections.observableArrayList();
    private FilteredList<UserDTO> filteredData;

    // --- MANAGERS ---
    private ChatManager chatManager;
    private ContactManager contactManager;
    private CallHandler callHandler;
    private NavigationHandler navigationHandler;
    private RealTimeHandler realTimeHandler;
    @FXML
    public void handleLogout() {
        // 1. Hiện hộp thoại xác nhận
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText(null);
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất không?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                performLogout();
            }
        });
    }
    private void performLogout() {
        try {
            // 1. Gửi tín hiệu Offline lên Server
            // (Đảm bảo gọi đúng service AuthService hoặc UserService tùy vào code server của bạn)
            if (SessionStore.currentUser != null) {
                try {
                    // Gọi hàm logout để cập nhật trạng thái trong DB
                    RmiClient.getAuthService().logout(SessionStore.currentUser.getId());
                } catch (Exception e) {
                    System.err.println("Lỗi khi báo Offline cho server: " + e.getMessage());
                }
            }

            // 2. Xóa session lưu trữ cục bộ
            SessionStore.currentUser = null;

            // 3. Dừng các kết nối chạy ngầm (nếu có)
            if (chatManager != null) {
                // Nếu ChatManager có logic P2P cần đóng, hãy xử lý ở đây
                // Ví dụ: mc.p2pClient.close();
            }

            // 4. Đóng cửa sổ chính (Main View) hiện tại -> "Thoát chương trình"
            Stage currentStage = (Stage) mainBorderPane.getScene().getWindow();
            currentStage.close();

            // 5. Tự động mở lại cửa sổ Đăng nhập -> "Khởi động lại"
            try {
                // Đường dẫn file FXML dựa trên cấu trúc file bạn đã upload (/view/login-view.fxml)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login-view.fxml"));
                Parent root = loader.load();

                Stage loginStage = new Stage();
                loginStage.setTitle("Đăng nhập - Hybrid Messenger");
                loginStage.setScene(new Scene(root));
                loginStage.setResizable(false);

                // Hiển thị màn hình đăng nhập mới
                loginStage.show();

            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Lỗi: Không tìm thấy file giao diện đăng nhập!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // 1. Khởi tạo Managers
        this.contactManager = new ContactManager(this);
        this.chatManager = new ChatManager(this);
        this.callHandler = new CallHandler(this);
        this.navigationHandler = new NavigationHandler(this);

        // 2. Setup ChatInfoController
        if (chatInfoController != null) {
            chatInfoController.setMainController(this);
        }

        // 3. [QUAN TRỌNG] Setup List Bạn Bè (Fix lỗi mất lịch sử)
        setupFriendListSearch();

        // 4. Setup UI Helpers
        ChatUIHelper.setMainController(this);
        if (mainBorderPane != null) {
            mainBorderPane.setRight(null); // Mặc định ẩn sidebar phải
        }

        // 5. Load User Info
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            loadMyAvatar(me.getAvatarUrl());
            startP2P();

            // Tải danh sách bạn bè
            loadFriendListToMaster();

            registerRealTimeUpdates();
        }

        // 6. Listener chuyển cuộc trò chuyện
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            handleSwitchConversation(newVal);
        });

        // 7. Event Handlers
        if (emojiBtn != null) {
            emojiBtn.setOnAction(e -> EmojiHandler.showEmojiPopup(emojiBtn, (emoji) -> inputField.appendText(emoji)));
        }

        // [QUAN TRỌNG] Logic Tìm kiếm tin nhắn (Scan & Scroll)
        if (searchMsgField != null) {
            // Khi gõ chữ -> Quét và lưu vị trí, KHÔNG ẩn tin nhắn
            searchMsgField.textProperty().addListener((obs, oldVal, newVal) -> {
                executeSearch(newVal);
            });
        }
    }

    // --- [LOGIC 1] FIX LIST BẠN BÈ & TÌM BẠN ---
    private void setupFriendListSearch() {
        // Bọc masterData vào FilteredList
        filteredData = new FilteredList<>(masterData, p -> true);

        // Gán vào conversationList (ListView chính trong FXML)
        conversationList.setItems(filteredData);
        conversationList.setCellFactory(param -> new FriendListCell());

        // Logic tìm kiếm bạn bè (Lọc danh sách bên trái)
        if (txtSearchFriend != null) {
            txtSearchFriend.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(user -> {
                    if (newValue == null || newValue.isEmpty()) return true;
                    String lower = newValue.toLowerCase();
                    return user.getDisplayName().toLowerCase().contains(lower) ||
                            user.getUsername().toLowerCase().contains(lower);
                });
            });
        }
    }

    public void loadFriendListToMaster() {
        new Thread(() -> {
            try {
                long myId = SessionStore.currentUser.getId();
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(myId);
                Platform.runLater(() -> {
                    masterData.clear();
                    if (friends != null) masterData.addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- [LOGIC 2] TÌM KIẾM TIN NHẮN (SCAN & SCROLL) ---
    private void executeSearch(String keyword) {
        searchMatches.clear();
        currentSearchIndex = -1;

        // Reset highlight cũ
        for (Node node : msgContainer.getChildren()) {
            node.getStyleClass().remove("search-highlight");
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            if (lblSearchCount != null) lblSearchCount.setText("");
            return;
        }

        String search = keyword.toLowerCase().trim();

        // Quét toàn bộ dòng chat
        for (Node node : msgContainer.getChildren()) {
            boolean match = false;
            Object data = node.getUserData();

            // Kiểm tra dữ liệu được gắn trong ChatUIHelper
            if (data instanceof MessageDTO) {
                // Trường hợp lưu cả object (như Bước 1 mình vừa hướng dẫn)
                String content = ((MessageDTO) data).getContent();
                if (content != null && content.toLowerCase().contains(search)) {
                    match = true;
                }
            } else if (data instanceof String) {
                // Trường hợp cũ (lưu string) - Giữ lại để tương thích ngược
                if (((String) data).contains(search)) {
                    match = true;
                }
            }

            if (match) {
                searchMatches.add(node);
            }
        }

        // Cập nhật kết quả
        if (!searchMatches.isEmpty()) {
            currentSearchIndex = searchMatches.size() - 1; // Nhảy tới tin mới nhất
            navigateToMatch();
        } else {
            if (lblSearchCount != null) lblSearchCount.setText("0/0");
        }
    }

    private void navigateToMatch() {
        if (searchMatches.isEmpty() || currentSearchIndex < 0) return;

        // Update Label đếm
        if (lblSearchCount != null) {
            lblSearchCount.setText((currentSearchIndex + 1) + "/" + searchMatches.size());
        }

        // Lấy Node đích
        Node targetNode = searchMatches.get(currentSearchIndex);

        // Tính toán Scroll
        double contentHeight = msgContainer.getBoundsInLocal().getHeight();
        double nodeY = targetNode.getBoundsInParent().getMinY();
        double vValue = nodeY / contentHeight;

        // Tinh chỉnh để tin nhắn nằm giữa view
        double viewportHeight = msgScrollPane.getViewportBounds().getHeight();
        if(contentHeight > viewportHeight) {
            // Logic cuộn nâng cao để targetNode nằm giữa màn hình
            double centerRatio = (nodeY - viewportHeight/2) / (contentHeight - viewportHeight);
            // Giới hạn 0.0 -> 1.0
            vValue = Math.max(0, Math.min(1, centerRatio));

            // Fallback đơn giản nếu tính toán phức tạp lỗi:
            vValue = nodeY / contentHeight;
        }

        msgScrollPane.setVvalue(vValue);

        // Highlight
        for (Node n : searchMatches) n.getStyleClass().remove("search-highlight");
        targetNode.getStyleClass().add("search-highlight");
    }

    @FXML
    public void handleSearchUp() {
        if (searchMatches.isEmpty()) return;
        currentSearchIndex--;
        if (currentSearchIndex < 0) currentSearchIndex = searchMatches.size() - 1;
        navigateToMatch();
    }

    @FXML
    public void handleSearchDown() {
        if (searchMatches.isEmpty()) return;
        currentSearchIndex++;
        if (currentSearchIndex >= searchMatches.size()) currentSearchIndex = 0;
        navigateToMatch();
    }

    public void toggleSearchMessage() {
        if (searchMsgField == null) return;

        boolean isShow = !searchMsgField.isVisible();

        // 1. Hiện ô nhập liệu
        searchMsgField.setVisible(isShow);
        searchMsgField.setManaged(isShow);

        // 2. [QUAN TRỌNG] Hiện các nút điều hướng
        if (btnSearchUp != null) {
            btnSearchUp.setVisible(isShow);
            btnSearchUp.setManaged(isShow); // <--- Dòng này giúp nút chiếm chỗ hiển thị
        }
        if (btnSearchDown != null) {
            btnSearchDown.setVisible(isShow);
            btnSearchDown.setManaged(isShow); // <--- Dòng này giúp nút chiếm chỗ hiển thị
        }
        if (lblSearchCount != null) {
            lblSearchCount.setVisible(isShow);
            lblSearchCount.setManaged(isShow); // <--- Dòng này giúp label chiếm chỗ hiển thị
        }

        if (isShow) {
            searchMsgField.requestFocus();
        } else {
            searchMsgField.clear();
            // Xóa highlight khi tắt
            for (Node node : msgContainer.getChildren()) {
                node.getStyleClass().remove("search-highlight");
            }
        }
    }

    // --- LOGIC CHUYỂN CHAT (GIỮ NGUYÊN) ---
    private void handleSwitchConversation(UserDTO newVal) {
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
                    this.activeConversationId = conversationId;
                    String themeColor = RmiClient.getMessageService().getConversationTheme(conversationId);
                    Platform.runLater(() -> applyThemeColor(themeColor));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

            if (mainBorderPane.getRight() != null && chatInfoController != null) {
                chatInfoController.setUserInfo(newVal);
            }
        }
    }

    // --- P2P & CÁC HÀM KHÁC (GIỮ NGUYÊN) ---
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

    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ ||
                    msg.getType() == MessageDTO.MessageType.CALL_ACCEPT ||
                    msg.getType() == MessageDTO.MessageType.CALL_DENY ||
                    msg.getType() == MessageDTO.MessageType.CALL_END) {
                callHandler.handleCallSignal(msg);
            } else {
                chatManager.handleIncomingMessage(msg);
                if (msg.getContent() != null && (msg.getContent().contains("đã đổi tên") || msg.getContent().contains("ảnh"))) {
                    loadFriendListToMaster();
                }
                if (msg.getContent() != null && msg.getContent().contains("đã giải tán nhóm")) {
                    handleGroupLeft(msg.getConversationId());
                }
            }
        });
    }

    // --- HELPERS ---
    public void applyThemeColor(String hexColor) {
        if (chatArea != null && hexColor != null && !hexColor.isEmpty()) {
            chatArea.setStyle("-fx-background-color: " + hexColor + ";");
        }
    }

    public void scrollToMessage(String uuid) {
        VBox bubble = messageUiMap.get(uuid);
        if (bubble != null) {
            Node row = bubble.getParent().getParent().getParent();
            if (row != null) {
                double contentHeight = msgContainer.getBoundsInLocal().getHeight();
                double nodeY = row.getBoundsInParent().getMinY();
                msgScrollPane.setVvalue(nodeY / contentHeight);
            }
            Platform.runLater(() -> {
                bubble.getStyleClass().add("highlight-bubble");
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (Exception e) {}
                    Platform.runLater(() -> bubble.getStyleClass().remove("highlight-bubble"));
                }).start();
            });
        }
    }

    // --- DELEGATE ACTIONS ---
    @FXML public void handleToggleInfo() {
        if (mainBorderPane.getRight() == null) {
            mainBorderPane.setRight(infoSidebarContainer);
            if (currentChatUser != null && chatInfoController != null) chatInfoController.setUserInfo(currentChatUser);
        } else {
            mainBorderPane.setRight(null);
        }
    }

    @FXML public void handleSend() { chatManager.handleSend(); }
    @FXML public void handleSendFile() { chatManager.handleSendFile(); }
    @FXML public void startRecording(MouseEvent event) { chatManager.startRecording(event); }
    @FXML public void stopAndSendAudio(MouseEvent event) { chatManager.stopAndSendAudio(event); }
    @FXML public void handleVoiceCall() { callHandler.handleVoiceCall(); }
    @FXML public void handleCreateGroup() { navigationHandler.handleCreateGroup(); }
    @FXML public void handleAddFriend() { navigationHandler.handleAddFriend(); }
    @FXML public void handleShowRequests() { navigationHandler.handleShowRequests(); }
    @FXML public void handleOpenProfile() { navigationHandler.handleOpenProfile(); }

    public void handleEditAction(MessageDTO msg) { chatManager.handleEditAction(msg); }
    public void handleRecallAction(MessageDTO msg) { chatManager.handleRecallAction(msg); }

    public void handleDeleteForMeAction(MessageDTO msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Xóa tin nhắn ở phía bạn?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        if (RmiClient.getMessageService().deleteMessageForUser(SessionStore.currentUser.getId(), msg.getId())) {
                            Platform.runLater(() -> {
                                VBox bubble = messageUiMap.get(msg.getUuid());
                                if (bubble != null) {
                                    Node nodeToDelete = bubble;
                                    while (nodeToDelete.getParent() != null && nodeToDelete.getParent() != msgContainer) {
                                        nodeToDelete = nodeToDelete.getParent();
                                    }
                                    if(nodeToDelete != null) {
                                        msgContainer.getChildren().remove(nodeToDelete);
                                        messageUiMap.remove(msg.getUuid());
                                    }
                                }
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    public void handlePinAction(MessageDTO msg) {
        new Thread(() -> {
            try {
                boolean newStatus = !msg.isPinned();
                if (RmiClient.getMessageService().pinMessage(msg.getId(), newStatus)) {
                    Platform.runLater(() -> {
                        msg.setPinned(newStatus);
                        new Alert(Alert.AlertType.INFORMATION, newStatus ? "Đã ghim!" : "Đã bỏ ghim!").show();
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void handleRemoteMessageUpdate(String uuid, String actionType) {
        VBox bubble = messageUiMap.get(uuid);
        if (bubble != null) {
            boolean isPinned = "PIN".equals(actionType);
            if (isPinned && !bubble.getStyleClass().contains("pinned-bubble")) {
                bubble.getStyleClass().add("pinned-bubble");
            } else if (!isPinned) {
                bubble.getStyleClass().remove("pinned-bubble");
            }
            if (bubble.getUserData() instanceof MessageDTO) {
                ((MessageDTO) bubble.getUserData()).setPinned(isPinned);
            }
        }
    }

    public void handleGroupLeft(long groupId) {
        Platform.runLater(() -> masterData.removeIf(u -> u.getId() == groupId));
        if (activeConversationId == groupId) {
            welcomeArea.setVisible(true);
            chatArea.setVisible(false);
            currentChatUser = null;
            activeConversationId = -1;
            mainBorderPane.setRight(null);
        }
    }

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
                        myAvatarView.setClip(new Circle(r, r, r));
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }
    public void updateFriendInList(UserDTO updatedFriend) {
        Platform.runLater(() -> {
            boolean found = false;
            for (int i = 0; i < masterData.size(); i++) {
                UserDTO current = masterData.get(i);
                if (current.getId() == updatedFriend.getId()) {
                    // 1. Cập nhật trạng thái mạng (Cái này luôn cần mới nhất)
                    current.setOnline(updatedFriend.isOnline());
                    current.setLastIp(updatedFriend.getLastIp());
                    current.setLastPort(updatedFriend.getLastPort());

                    // 2. Chỉ cập nhật Tên/Avatar nếu dữ liệu mới KHÔNG BỊ NULL
                    // (Tránh trường hợp gói tin báo offline chỉ gửi ID mà không gửi tên)
                    if (updatedFriend.getDisplayName() != null && !updatedFriend.getDisplayName().isEmpty()) {
                        current.setDisplayName(updatedFriend.getDisplayName());
                    }
                    if (updatedFriend.getAvatarUrl() != null && !updatedFriend.getAvatarUrl().isEmpty()) {
                        current.setAvatarUrl(updatedFriend.getAvatarUrl());
                    }
                    if (updatedFriend.getUsername() != null) { // Username thường dùng để check GROUP
                        current.setUsername(updatedFriend.getUsername());
                    }

                    // 3. Ghi đè lại chính nó vào vị trí cũ để kích hoạt ListView vẽ lại (Refresh)
                    masterData.set(i, current);
                    found = true;
                    break;
                }
            }

            // Nếu không tìm thấy (là bạn mới hoặc nhóm mới) thì thêm vào
            if (!found) {
                masterData.add(updatedFriend);
            }
        });
    }
    public void addFriendToListDirectly(UserDTO newFriend) {
        Platform.runLater(() -> {
            boolean exists = masterData.stream().anyMatch(u -> u.getId() == newFriend.getId());
            if (!exists) masterData.add(newFriend);
        });
    }
    public void updateSenderNameInUI(long userId, String newName) {
        Platform.runLater(() -> {
            // Duyệt qua tất cả các tin nhắn đang hiện
            for (Node row : msgContainer.getChildren()) {
                // Gọi hàm đệ quy để tìm cái Label tên nằm sâu bên trong
                updateNameLabelRecursive(row, userId, newName);
            }
        });
    }

    private void updateNameLabelRecursive(Node node, long userId, String newName) {
        if (node instanceof Label) {
            Label lbl = (Label) node;
            // Tìm đúng cái Label đã được đánh dấu trong ChatUIHelper
            if ("NAME_LABEL".equals(lbl.getProperties().get("TYPE")) &&
                    lbl.getProperties().get("USER_ID") != null &&
                    (long) lbl.getProperties().get("USER_ID") == userId) {

                lbl.setText(newName);
            }
        } else if (node instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                updateNameLabelRecursive(child, userId, newName);
            }
        }
    }
    public void updateCurrentChatName(long id, String newName) {
        Platform.runLater(() -> {
            // Chỉ cập nhật nếu đang mở đúng cuộc trò chuyện đó
            if (currentChatUser != null && currentChatUser.getId() == id) {
                // 1. Cập nhật dữ liệu đệm
                currentChatUser.setDisplayName(newName);

                // 2. Cập nhật Tiêu đề ở giữa
                if (currentChatTitle != null) {
                    currentChatTitle.setText(newName);
                }

                // 3. Cập nhật Sidebar bên phải (Info Panel)
                // Kiểm tra xem Sidebar có đang mở không
                if (mainBorderPane.getRight() != null && chatInfoController != null) {
                    // Gọi hàm nạp lại thông tin cho Sidebar
                    chatInfoController.setUserInfo(currentChatUser);
                }
            }
        });
    }
    public void handleEndCallSignal() { callHandler.handleEndCallSignal(); }
    public void handleRemoteThemeUpdate(long conversationId, String newColor) {
        if (this.activeConversationId == conversationId) applyThemeColor(newColor);
    }
    // Getters
    public ContactManager getContactManager() { return contactManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CallHandler getCallHandler() { return callHandler; }
    public NavigationHandler getNavigationHandler() { return navigationHandler; }
    public ObservableList<UserDTO> getMasterData() { return masterData; }
}