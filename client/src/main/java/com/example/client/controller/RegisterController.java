package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.util.Navigation;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;

public class RegisterController {
    @FXML private TextField userField, nameField, emailField, passField;

    @FXML
    public void handleRegister(ActionEvent event) {
        try {
            String u = userField.getText();
            String p = passField.getText();
            String n = nameField.getText();
            String e = emailField.getText();

            if (u.isEmpty() || p.isEmpty() || n.isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập đủ các trường bắt buộc.");
                return;
            }

            // Gọi Server đăng ký
            boolean ok = RmiClient.getAuthService().register(u, p, n, e);

            if (ok) {
                showAlert("Thành công", "Đăng ký thành công! Hãy đăng nhập.");
                // Chuyển về màn hình đăng nhập
                Navigation.switchScene(event, "/view/login-view.fxml", "Hybrid Messenger - Login");
            } else {
                showAlert("Thất bại", "Tên đăng nhập đã tồn tại hoặc lỗi Server.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void backToLogin(ActionEvent event) {
        Navigation.switchScene(event, "/view/login-view.fxml", "Hybrid Messenger - Login");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}