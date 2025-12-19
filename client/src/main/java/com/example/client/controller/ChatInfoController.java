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

public class ChatInfoController {

    // --- UI HEADER ---
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;
    @FXML private Button changeAvatarBtn;

    // --- UI CẤU TRÚC (Accordion) ---
    @FXML private Accordion infoAccordion;
    @FXML private TitledPane membersPane;

    // --- UI THÀNH VIÊN & ADMIN ---
    @FXML private Button addMemberBtn;
    @FXML private ListView<UserDTO> memberListView;
    @FXML private VBox adminControls;
    @FXML private Button editGroupBtn;
    @FXML private Button dissolveGroupBtn;
    @FXML private Button leaveGroupBtn;

    // --- UI KHÁC ---
    @FXML private VBox personalProfileBox;

    private boolean amIAdmin = false;
    private MainController mainController;
    private UserDTO currentUser;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setUserInfo(UserDTO groupOrUser) {
        this.currentUser = groupOrUser;
        if (currentUser == null) return;
        nameLabel.setText(currentUser.getDisplayName());
        loadAvatar(currentUser.getAvatarUrl());

        if ("GROUP".equals(currentUser.getUsername())) {
            setupGroupUI();
        } else {
            setupP2PUI();
        }
    }

    private void setupP2PUI() {
        if (infoAccordion != null && membersPane != null) infoAccordion.getPanes().remove(membersPane);
        if (adminControls != null) { adminControls.setVisible(false); adminControls.setManaged(false); }
        if (leaveGroupBtn != null) { leaveGroupBtn.setVisible(false); leaveGroupBtn.setManaged(false); }
        if (changeAvatarBtn != null) changeAvatarBtn.setVisible(false);
        if (personalProfileBox != null) { personalProfileBox.setVisible(true); personalProfileBox.setManaged(true); }
    }

    private void setupGroupUI() {
        if (infoAccordion != null && membersPane != null && !infoAccordion.getPanes().contains(membersPane)) {
            infoAccordion.getPanes().add(0, membersPane);
            infoAccordion.setExpandedPane(membersPane);
        }
        if (personalProfileBox != null) { personalProfileBox.setVisible(false); personalProfileBox.setManaged(false); }
        if (leaveGroupBtn != null) { leaveGroupBtn.setVisible(true); leaveGroupBtn.setManaged(true); }
        if (memberListView != null) memberListView.setVisible(true);
        if (addMemberBtn != null) addMemberBtn.setVisible(true);
        checkAdminStatus();
    }

    // --- [SỬA LẠI] XỬ LÝ KHO LƯU TRỮ (MEDIA & FILES) ---
    // Sử dụng luôn onAction đã khai báo trong FXML

    @FXML
    public void handleViewMedia() {
        if (mainController != null && mainController.getChatManager() != null) {
            // Gọi hàm xử lý thật sự bên ChatManager (Hàm bạn đã thêm ở bước trước)
            mainController.getChatManager().openImageRepository();
        } else {
            System.err.println("Chưa kết nối MainController!");
        }
    }

    @FXML
    public void handleViewFiles() {
        if (mainController != null && mainController.getChatManager() != null) {
            // Gọi hàm xử lý thật sự bên ChatManager
            mainController.getChatManager().openFileRepository();
        } else {
            System.err.println("Chưa kết nối MainController!");
        }
    }

    // --- CÁC TÍNH NĂNG KHÁC (GIỮ NGUYÊN) ---

    @FXML
    public void handleChangeTheme() {
        ColorPicker colorPicker = new ColorPicker(Color.WHITE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đổi chủ đề");
        dialog.setHeaderText("Chọn màu nền cho cuộc trò chuyện:");
        dialog.getDialogPane().setContent(new VBox(10, new Label("Màu sắc:"), colorPicker));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                Color c = colorPicker.getValue();
                String webColor = String.format("#%02X%02X%02X", (int)(c.getRed() * 255), (int)(c.getGreen() * 255), (int)(c.getBlue() * 255));
                new Thread(() -> {
                    try {
                        long targetConvId;
                        if ("GROUP".equals(currentUser.getUsername())) targetConvId = currentUser.getId();
                        else targetConvId = RmiClient.getMessageService().getPrivateConversationId(SessionStore.currentUser.getId(), currentUser.getId());
                        boolean ok = RmiClient.getMessageService().updateConversationTheme(targetConvId, webColor);
                        if (ok) {
                            Platform.runLater(() -> {
                                if (mainController != null) mainController.applyThemeColor(webColor);
                                sendSystemNotification("đã đổi màu chủ đề.");
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    @FXML
    public void handleEditNickname() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Chỉnh sửa biệt danh");
        boolean isGroup = "GROUP".equals(currentUser.getUsername());
        if (isGroup) dialog.setHeaderText("Đặt biệt danh cho BẠN trong nhóm này:");
        else dialog.setHeaderText("Đặt biệt danh cho " + currentUser.getDisplayName() + ":");
        dialog.setContentText("Biệt danh mới:");

        dialog.showAndWait().ifPresent(nickname -> {
            if (!nickname.trim().isEmpty()) {
                new Thread(() -> {
                    try {
                        long myId = SessionStore.currentUser.getId();
                        long targetId = currentUser.getId();
                        boolean ok;
                        if (isGroup) ok = RmiClient.getGroupService().updateNickname(targetId, myId, nickname);
                        else ok = RmiClient.getFriendService().updateFriendNickname(myId, targetId, nickname);

                        if (ok) {
                            if (isGroup) sendSystemNotification("đã đổi biệt danh thành: " + nickname);
                            Platform.runLater(() -> {
                                if (!isGroup) {
                                    currentUser.setDisplayName(nickname);
                                    nameLabel.setText(nickname);
                                    if (mainController != null) {
                                        mainController.currentChatTitle.setText(nickname);
                                        mainController.getContactManager().loadFriendListInitial();
                                    }
                                } else checkAdminStatus();
                                new Alert(Alert.AlertType.INFORMATION, "Đổi biệt danh thành công!").show();
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    @FXML
    public void handleViewPinnedMessages() {
        if (currentUser == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Tin nhắn đã ghim");
        alert.setHeaderText("Danh sách tin nhắn quan trọng");
        ListView<MessageDTO> listView = new ListView<>();
        listView.setPrefSize(400, 300);
        listView.setPlaceholder(new Label("Đang tải dữ liệu..."));
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(MessageDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else {
                    VBox vBox = new VBox(3);
                    Label sender = new Label(item.getSenderName());
                    sender.setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9;");
                    Label content = new Label(item.getContent());
                    content.setStyle("-fx-text-fill: #333;");
                    content.setWrapText(true);
                    content.setMaxWidth(360);
                    vBox.getChildren().addAll(sender, content);
                    setGraphic(vBox);
                }
            }
        });
        listView.setOnMouseClicked(e -> {
            MessageDTO selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && mainController != null) mainController.scrollToMessage(selected.getUuid());
        });
        alert.getDialogPane().setContent(listView);
        alert.show();
        new Thread(() -> {
            try {
                long targetConversationId;
                if ("GROUP".equals(currentUser.getUsername())) targetConversationId = currentUser.getId();
                else targetConversationId = RmiClient.getMessageService().getPrivateConversationId(SessionStore.currentUser.getId(), currentUser.getId());
                List<MessageDTO> pinnedMsgs = RmiClient.getMessageService().getPinnedMessages(targetConversationId);
                Platform.runLater(() -> {
                    if (pinnedMsgs.isEmpty()) listView.setPlaceholder(new Label("Chưa có tin nhắn nào được ghim."));
                    else listView.getItems().setAll(pinnedMsgs);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

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
        if (adminControls != null) { adminControls.setVisible(amIAdmin); adminControls.setManaged(amIAdmin); }
        if (changeAvatarBtn != null) { changeAvatarBtn.setVisible(amIAdmin); changeAvatarBtn.setManaged(amIAdmin); }
    }

    private class MemberListCell extends ListCell<UserDTO> {
        @Override
        protected void updateItem(UserDTO item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) setGraphic(null);
            else {
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
                if (amIAdmin && item.getId() != SessionStore.currentUser.getId()) {
                    Button kickBtn = new Button("❌");
                    kickBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-cursor: hand; -fx-font-size: 10px;");
                    kickBtn.setTooltip(new Tooltip("Mời ra khỏi nhóm"));
                    kickBtn.setOnAction(e -> handleKickMember(item));
                    box.getChildren().add(kickBtn);
                }
                if (item.getId() == SessionStore.currentUser.getId()) name.setText(name.getText() + " (Bạn)");
                setGraphic(box);
            }
        }
    }

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
                                checkAdminStatus();
                                new Alert(Alert.AlertType.INFORMATION, "Đã thêm thành công!").show();
                            } else new Alert(Alert.AlertType.ERROR, "Người này đã ở trong nhóm hoặc có lỗi xảy ra!").show();
                        });
                    } else Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Không tìm thấy người dùng: " + username).show());
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
                        boolean ok = RmiClient.getGroupService().removeMemberFromGroup(SessionStore.currentUser.getId(), currentUser.getId(), target.getId());
                        if (ok) {
                            sendSystemNotification("đã mời " + target.getDisplayName() + " ra khỏi nhóm.");
                            checkAdminStatus();
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
                        boolean ok = RmiClient.getGroupService().updateGroupInfo(SessionStore.currentUser.getId(), currentUser.getId(), newName, null);
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
                    boolean ok = RmiClient.getGroupService().updateGroupInfo(SessionStore.currentUser.getId(), currentUser.getId(), null, serverPath);
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
                            if (ok && mainController != null) mainController.handleGroupLeft(currentUser.getId());
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
                            if (ok && mainController != null) mainController.handleGroupLeft(currentUser.getId());
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

    @FXML
    public void handleSearchMessage() {
        if (mainController != null) {
            mainController.toggleSearchMessage();
        }
    }
}