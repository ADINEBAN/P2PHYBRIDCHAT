package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class ChatInfoController {

    // --- UI HEADER ---
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;
    @FXML private Button changeAvatarBtn; // Nút camera trên avatar (Chỉ hiện khi là Admin nhóm)

    // --- UI CẤU TRÚC (Accordion) ---
    @FXML private Accordion infoAccordion; // Để điều khiển các Tab
    @FXML private TitledPane membersPane;  // Tab "Thành viên nhóm" (Sẽ ẩn đi khi chat 1-1)

    // --- UI THÀNH VIÊN & ADMIN ---
    @FXML private Button addMemberBtn;
    @FXML private ListView<UserDTO> memberListView;
    @FXML private VBox adminControls; // Vùng chứa nút Đổi tên/Giải tán
    @FXML private Button editGroupBtn;
    @FXML private Button dissolveGroupBtn;
    @FXML private Button leaveGroupBtn;

    // --- UI KHÁC ---
    // Hộp chứa nút Trang cá nhân (Dành cho chat 1-1)
    @FXML private VBox personalProfileBox;

    private boolean amIAdmin = false;
    private MainController mainController;
    private UserDTO currentUser; // Đây là đối tượng đang chat (User hoặc Group)

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Hàm quan trọng nhất: Nhận thông tin và quyết định hiển thị giao diện 1-1 hay Nhóm
     */
    public void setUserInfo(UserDTO groupOrUser) {
        this.currentUser = groupOrUser;
        if (currentUser == null) return;

        // 1. Load thông tin cơ bản
        nameLabel.setText(currentUser.getDisplayName());
        loadAvatar(currentUser.getAvatarUrl());

        // 2. Phân loại giao diện
        if ("GROUP".equals(currentUser.getUsername())) {
            setupGroupUI();
        } else {
            setupP2PUI();
        }
    }

    // --- LOGIC GIAO DIỆN CHAT 1-1 ---
    private void setupP2PUI() {
        // 1. Ẩn Tab thành viên khỏi Accordion
        if (infoAccordion != null && membersPane != null) {
            infoAccordion.getPanes().remove(membersPane);
        }

        // 2. Ẩn các nút Admin & Rời nhóm
        if (adminControls != null) {
            adminControls.setVisible(false);
            adminControls.setManaged(false);
        }
        if (leaveGroupBtn != null) {
            leaveGroupBtn.setVisible(false);
            leaveGroupBtn.setManaged(false);
        }
        if (changeAvatarBtn != null) {
            changeAvatarBtn.setVisible(false);
        }

        // 3. Hiện nút Trang cá nhân (Nếu có)
        if (personalProfileBox != null) {
            personalProfileBox.setVisible(true);
            personalProfileBox.setManaged(true);
        }
    }

    // --- LOGIC GIAO DIỆN NHÓM ---
    private void setupGroupUI() {
        // 1. Thêm lại Tab thành viên vào Accordion (nếu chưa có)
        if (infoAccordion != null && membersPane != null) {
            if (!infoAccordion.getPanes().contains(membersPane)) {
                infoAccordion.getPanes().add(0, membersPane); // Thêm vào vị trí đầu
                infoAccordion.setExpandedPane(membersPane); // Mở sẵn tab này
            }
        }

        // 2. Ẩn nút Trang cá nhân
        if (personalProfileBox != null) {
            personalProfileBox.setVisible(false);
            personalProfileBox.setManaged(false);
        }

        // 3. Hiện nút Rời nhóm và List thành viên
        if (leaveGroupBtn != null) {
            leaveGroupBtn.setVisible(true);
            leaveGroupBtn.setManaged(true);
        }
        if (memberListView != null) {
            memberListView.setVisible(true);
        }
        if (addMemberBtn != null) {
            addMemberBtn.setVisible(true); // Mặc định hiện, có thể ẩn nếu muốn chỉ Admin mới được mời
        }

        // 4. Kiểm tra quyền Admin để hiện các nút nâng cao
        checkAdminStatus();
    }

    // --- CÁC TÍNH NĂNG MỚI (THEME, NICKNAME, PINNED) ---

    @FXML
    public void handleChangeTheme() {
        // Mở hộp thoại chọn màu
        ColorPicker colorPicker = new ColorPicker(Color.WHITE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đổi chủ đề");
        dialog.setHeaderText("Chọn màu nền cho cuộc trò chuyện:");
        dialog.getDialogPane().setContent(new VBox(10, new Label("Màu sắc:"), colorPicker));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                Color c = colorPicker.getValue();
                String webColor = String.format("#%02X%02X%02X",
                        (int)(c.getRed() * 255),
                        (int)(c.getGreen() * 255),
                        (int)(c.getBlue() * 255));

                System.out.println("Đã chọn màu: " + webColor);
                // TODO: Gọi API lưu màu này vào Database (bảng conversations -> theme_color)
                // RmiClient.getGroupService().updateTheme(currentUser.getId(), webColor);

                // Demo: Thông báo
                sendSystemNotification("đã đổi chủ đề cuộc trò chuyện.");
            }
        });
    }

    @FXML
    public void handleEditNickname() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Chỉnh sửa biệt danh");

        // Kiểm tra xem đang ở Nhóm hay Chat 1-1
        boolean isGroup = "GROUP".equals(currentUser.getUsername());

        if (isGroup) {
            dialog.setHeaderText("Đặt biệt danh cho BẠN trong nhóm này:");
        } else {
            dialog.setHeaderText("Đặt biệt danh cho " + currentUser.getDisplayName() + ":");
        }

        dialog.setContentText("Biệt danh mới:");

        dialog.showAndWait().ifPresent(nickname -> {
            if (!nickname.trim().isEmpty()) {
                new Thread(() -> {
                    try {
                        long myId = SessionStore.currentUser.getId();
                        long targetId = currentUser.getId(); // ID của Nhóm hoặc ID của Bạn bè
                        boolean ok = false;

                        if (isGroup) {
                            // --- TRƯỜNG HỢP NHÓM: Đổi nickname của CHÍNH MÌNH ---
                            ok = RmiClient.getGroupService().updateNickname(targetId, myId, nickname);
                        } else {
                            // --- TRƯỜNG HỢP 1-1: Đổi nickname của BẠN BÈ ---
                            // Lưu ý: targetId lúc này là ID người bạn
                            ok = RmiClient.getFriendService().updateFriendNickname(myId, targetId, nickname);
                        }

                        if (ok) {
                            // 1. Gửi thông báo hệ thống (Chỉ cần thiết cho nhóm)
                            if (isGroup) {
                                sendSystemNotification("đã đổi biệt danh thành: " + nickname);
                            }

                            // 2. Cập nhật giao diện Client ngay lập tức
                            Platform.runLater(() -> {
                                // Nếu là 1-1, cập nhật tên hiển thị của người đang chat
                                if (!isGroup) {
                                    currentUser.setDisplayName(nickname);
                                    nameLabel.setText(nickname); // Cập nhật Label tên bên phải

                                    // Cập nhật Header ở giữa (MainController)
                                    if (mainController != null) {
                                        mainController.currentChatTitle.setText(nickname);
                                        // Force refresh list bên trái để hiện tên mới
                                        mainController.getContactManager().loadFriendListInitial();
                                    }
                                } else {
                                    // Nếu là nhóm, refresh list thành viên
                                    checkAdminStatus();
                                }

                                new Alert(Alert.AlertType.INFORMATION, "Đổi biệt danh thành công!").show();
                            });
                        } else {
                            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Lỗi lưu biệt danh!").show());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }

    @FXML
    public void handleViewPinnedMessages() {
        // Demo hiển thị danh sách
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Tin nhắn đã ghim");
        alert.setHeaderText("Danh sách tin nhắn quan trọng");

        ListView<String> pinnedList = new ListView<>();
        pinnedList.getItems().addAll("📌 Nội quy nhóm", "📌 Link họp online", "📌 Deadline nộp bài");
        pinnedList.setPrefHeight(150);

        alert.getDialogPane().setContent(pinnedList);
        alert.show();

        // TODO: Gọi API lấy list tin nhắn có is_pinned = true
    }

    // --- CÁC HÀM HELPER VÀ XỬ LÝ SỰ KIỆN CŨ ---

    private void loadAvatar(String url) {
        avatarView.setImage(null);
        if (url != null && !url.isEmpty()) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(url);
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data));
                        Platform.runLater(() -> {
                            avatarView.setImage(img);
                            double r = avatarView.getFitWidth() / 2;
                            Circle clip = new Circle(r, r, r);
                            avatarView.setClip(clip);
                        });
                    }
                } catch (Exception e) {}
            }).start();
        }
    }

    private void checkAdminStatus() {
        new Thread(() -> {
            try {
                List<UserDTO> members = RmiClient.getGroupService().getGroupMembers(currentUser.getId());
                long myId = SessionStore.currentUser.getId();
                UserDTO meInGroup = members.stream().filter(u -> u.getId() == myId).findFirst().orElse(null);

                amIAdmin = (meInGroup != null && meInGroup.isAdmin());

                Platform.runLater(() -> updateAdminUI(members));
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateAdminUI(List<UserDTO> members) {
        if (memberListView != null) {
            memberListView.getItems().setAll(members);
            memberListView.setCellFactory(param -> new MemberListCell());
        }

        // Hiện/Ẩn vùng Admin Controls
        if (adminControls != null) {
            adminControls.setVisible(amIAdmin);
            adminControls.setManaged(amIAdmin);
        }

        // Nút đổi ảnh trên Avatar
        if (changeAvatarBtn != null) {
            changeAvatarBtn.setVisible(amIAdmin);
            changeAvatarBtn.setManaged(amIAdmin);
        }
    }

    // Class cell tùy chỉnh để hiển thị thành viên đẹp hơn
    private class MemberListCell extends ListCell<UserDTO> {
        @Override
        protected void updateItem(UserDTO item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                HBox box = new HBox(10);
                box.setPadding(new Insets(5));
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                String role = item.isAdmin() ? " (Trưởng nhóm)" : "";
                Label name = new Label(item.getDisplayName() + role);

                if (item.isAdmin()) name.setStyle("-fx-font-weight: bold; -fx-text-fill: #0084ff;");
                else name.setStyle("-fx-text-fill: #333;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                box.getChildren().addAll(name, spacer);

                // Nút kick (Chỉ hiện nếu mình là Admin và không kick chính mình)
                if (amIAdmin && item.getId() != SessionStore.currentUser.getId()) {
                    Button kickBtn = new Button("❌");
                    kickBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-cursor: hand; -fx-font-size: 10px;");
                    kickBtn.setTooltip(new Tooltip("Mời ra khỏi nhóm"));
                    kickBtn.setOnAction(e -> handleKickMember(item));
                    box.getChildren().add(kickBtn);
                }

                if (item.getId() == SessionStore.currentUser.getId()) {
                    name.setText(name.getText() + " (Bạn)");
                }
                setGraphic(box);
            }
        }
    }

    // --- CÁC HÀM XỬ LÝ HÀNH ĐỘNG (ADD, KICK, LEAVE, RENAME...) ---

    @FXML
    public void handleAddMember() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Nhập Username người cần thêm:");
        dialog.setContentText("Username:");

        dialog.showAndWait().ifPresent(username -> {
            new Thread(() -> {
                try {
                    List<UserDTO> searchResult = RmiClient.getFriendService().searchUsers(username);
                    UserDTO target = searchResult.stream().filter(u -> u.getUsername().equals(username)).findFirst().orElse(null);

                    if (target != null) {
                        boolean ok = RmiClient.getGroupService().addMemberToGroup(currentUser.getId(), target.getId());
                        Platform.runLater(() -> {
                            if (ok) {
                                sendSystemNotification("đã thêm " + target.getDisplayName() + " vào nhóm.");
                                checkAdminStatus(); // Refresh list
                                new Alert(Alert.AlertType.INFORMATION, "Đã thêm thành công!").show();
                            } else {
                                new Alert(Alert.AlertType.ERROR, "Người này đã ở trong nhóm hoặc có lỗi xảy ra!").show();
                            }
                        });
                    } else {
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Không tìm thấy người dùng: " + username).show());
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    private void handleKickMember(UserDTO target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Mời " + target.getDisplayName() + " ra khỏi nhóm?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        boolean ok = RmiClient.getGroupService().removeMemberFromGroup(
                                SessionStore.currentUser.getId(),
                                currentUser.getId(),
                                target.getId()
                        );
                        if (ok) {
                            sendSystemNotification("đã mời " + target.getDisplayName() + " ra khỏi nhóm.");
                            checkAdminStatus(); // Refresh list
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    @FXML
    public void handleEditGroupName() {
        String currentName = currentUser.getDisplayName().replace("[Nhóm] ", "");
        TextInputDialog dialog = new TextInputDialog(currentName);
        dialog.setTitle("Đổi tên nhóm");
        dialog.setHeaderText("Nhập tên nhóm mới:");

        dialog.showAndWait().ifPresent(newName -> {
            if(!newName.trim().isEmpty()){
                new Thread(() -> {
                    try {
                        boolean ok = RmiClient.getGroupService().updateGroupInfo(
                                SessionStore.currentUser.getId(), currentUser.getId(), newName, null
                        );
                        if (ok) {
                            sendSystemNotification("đã đổi tên nhóm thành: " + newName);
                            Platform.runLater(() -> nameLabel.setText("[Nhóm] " + newName));
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    @FXML
    public void handleChangeGroupAvatar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh nhóm mới");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.png", "*.jpeg"));
        File file = fc.showOpenDialog(nameLabel.getScene().getWindow());

        if (file != null) {
            new Thread(() -> {
                try {
                    byte[] fileData = Files.readAllBytes(file.toPath());
                    String serverPath = RmiClient.getMessageService().uploadFile(fileData, file.getName());
                    boolean ok = RmiClient.getGroupService().updateGroupInfo(
                            SessionStore.currentUser.getId(), currentUser.getId(), null, serverPath
                    );
                    if (ok) {
                        sendSystemNotification("đã thay đổi ảnh nhóm.");
                        loadAvatar(serverPath);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    @FXML
    public void handleDissolveGroup() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Hành động này sẽ xóa nhóm vĩnh viễn và không thể hoàn tác.\nBạn chắc chắn chứ?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        sendSystemNotification("đã giải tán nhóm.");
                        boolean ok = RmiClient.getGroupService().dissolveGroup(SessionStore.currentUser.getId(), currentUser.getId());
                        Platform.runLater(() -> {
                            if (ok && mainController != null) {
                                mainController.handleGroupLeft(currentUser.getId());
                            }
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    @FXML
    public void handleLeaveGroup() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Rời nhóm");
        alert.setHeaderText("Bạn có chắc muốn rời nhóm?");
        alert.setContentText("Bạn sẽ không nhận được tin nhắn mới từ nhóm này.");

        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        sendSystemNotification("đã rời khỏi nhóm.");
                        boolean ok = RmiClient.getGroupService().leaveGroup(SessionStore.currentUser.getId(), currentUser.getId());
                        Platform.runLater(() -> {
                            if (ok && mainController != null) {
                                mainController.handleGroupLeft(currentUser.getId());
                            }
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    private void sendSystemNotification(String actionText) {
        if (mainController == null) return;
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.NOTIFICATION);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setConversationId(currentUser.getId());
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent(SessionStore.currentUser.getDisplayName() + " " + actionText);
        mainController.getChatManager().sendP2PMessage(msg);
    }
    // --- [MỚI] XỬ LÝ KHO LƯU TRỮ (MEDIA & FILES) ---

    @FXML
    public void handleViewMedia() {
        // Mở popup hiển thị danh sách ảnh đã gửi trong nhóm
        showStoragePopup("Kho Ảnh", MessageDTO.MessageType.IMAGE);
    }

    @FXML
    public void handleViewFiles() {
        // Mở popup hiển thị danh sách tài liệu đã gửi
        showStoragePopup("Kho Tài Liệu", MessageDTO.MessageType.FILE);
    }

    private void showStoragePopup(String title, MessageDTO.MessageType type) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("Danh sách " + title);

        ListView<String> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.setPrefWidth(300);

        // Tạo label thông báo đang tải
        listView.setPlaceholder(new Label("Đang tải dữ liệu..."));

        alert.getDialogPane().setContent(listView);
        alert.show(); // Hiển thị khung trước

        // Gọi Server lấy danh sách file chạy ngầm
        new Thread(() -> {
            try {
                // Giả định bạn sẽ viết thêm hàm getSharedFiles ở Server
                // List<MessageDTO> files = RmiClient.getMessageService().getSharedFiles(currentUser.getId(), type);

                // [DEMO] Tạm thời hiển thị dữ liệu giả để bạn test giao diện
                Platform.runLater(() -> {
                    if (type == MessageDTO.MessageType.IMAGE) {
                        listView.getItems().addAll("📷 ảnh_đi_chơi.png", "📷 screenshot_lỗi.jpg", "📷 avatar_cũ.jpg");
                    } else {
                        listView.getItems().addAll("📄 bao_cao_nhom.pdf", "📄 do_an_java.docx", "📄 slide_thuyet_trinh.pptx");
                    }

                    // Logic click vào item để tải xuống
                    listView.setOnMouseClicked(e -> {
                        String selected = listView.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            // Gọi hàm tải file (đã có logic trong ChatUIHelper)
                            System.out.println("Đang tải: " + selected);
                        }
                    });
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    @FXML
    public void handleSearchMessage() {
        if (mainController != null) {
            // Gọi sang MainController để bật thanh tìm kiếm
            mainController.toggleSearchMessage();
        }
    }
}