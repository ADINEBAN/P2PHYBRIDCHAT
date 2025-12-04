package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.application.Platform; // Nhớ import cái này
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.List;

public class AddFriendController {
    @FXML private TextField searchField;
    @FXML private ListView<UserDTO> resultList;

    @FXML
    public void initialize() {
        resultList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<UserDTO> call(ListView<UserDTO> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(UserDTO item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            HBox box = new HBox(10);
                            Label nameLabel = new Label(item.getDisplayName() + " (" + item.getUsername() + ")");
                            Button addButton = new Button("Gửi lời mời"); // Đổi tên nút cho đúng nghĩa

                            // Bấm nút thì gọi hàm xử lý gửi lời mời
                            addButton.setOnAction(e -> sendRequest(item));

                            box.getChildren().addAll(nameLabel, addButton);
                            setGraphic(box);
                        }
                    }
                };
            }
        });
    }

    @FXML
    public void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        new Thread(() -> {
            try {
                List<UserDTO> results = RmiClient.getFriendService().searchUsers(query);

                long myId = SessionStore.currentUser.getId();
                results.removeIf(u -> u.getId() == myId);

                Platform.runLater(() -> {
                    resultList.getItems().clear();
                    resultList.getItems().addAll(results);
                    if (results.isEmpty()) {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Không tìm thấy ai!");
                        a.show();
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // [SỬA LẠI TÊN HÀM VÀ LOGIC GỌI SERVER]
    private void sendRequest(UserDTO target) {
        new Thread(() -> {
            try {
                long myId = SessionStore.currentUser.getId();

                // GỌI HÀM sendFriendRequest (Thay vì addFriend)
                boolean ok = RmiClient.getFriendService().sendFriendRequest(myId, target.getId());

                Platform.runLater(() -> {
                    if (ok) {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Đã gửi lời mời tới " + target.getDisplayName());
                        a.show();
                        resultList.getItems().remove(target); // Ẩn người đó đi sau khi gửi
                    } else {
                        Alert a = new Alert(Alert.AlertType.ERROR, "Không thể gửi (Đã là bạn hoặc đã gửi rồi)!");
                        a.show();
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML void handleClose() {
        ((Stage) searchField.getScene().getWindow()).close();
    }
}