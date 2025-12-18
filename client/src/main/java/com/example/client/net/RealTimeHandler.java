package com.example.client.net;

import com.example.client.controller.MainController;
import com.example.common.dto.MessageDTO; // [QUAN TRỌNG] Đừng quên import này
import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RealTimeHandler extends UnicastRemoteObject implements ClientCallback {

    private final MainController mainController;

    public RealTimeHandler(MainController mainController) throws RemoteException {
        super();
        this.mainController = mainController;
    }

    // --- [1. QUAN TRỌNG: HÀM NHẬN TIN NHẮN (BẠN ĐANG THIẾU CÁI NÀY)] ---
    @Override
    public void onMessageReceived(MessageDTO message) throws RemoteException {
        // Chuyển tiếp tin nhắn sang MainController để hiển thị lên giao diện
        Platform.runLater(() -> mainController.onMessageReceived(message));
    }

    // --- 2. CÁC HÀM XỬ LÝ BẠN BÈ & NHÓM ---
    @Override
    public void onFriendStatusChange(UserDTO friend) throws RemoteException {
        // Cập nhật trạng thái Online/Offline
        Platform.runLater(() -> mainController.updateFriendInList(friend));
    }

    @Override
    public void onNewFriendRequest(UserDTO sender) throws RemoteException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText("Lời mời kết bạn mới!");
            alert.setContentText(sender.getDisplayName() + " muốn kết bạn.");
            alert.show();
        });
    }

    @Override
    public void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Tin vui");
            alert.setHeaderText(null);
            alert.setContentText(newFriend.getDisplayName() + " đã chấp nhận lời mời!");
            alert.show();

            // Thêm bạn mới vào danh sách ngay lập tức
            newFriend.setOnline(true);
            mainController.addFriendToListDirectly(newFriend);
        });
    }

    @Override
    public void onAddedToGroup(UserDTO newGroup) throws RemoteException {
        // Khi được thêm vào nhóm, gọi hàm này để hiển thị nhóm lên Sidebar ngay lập tức
        Platform.runLater(() -> mainController.updateFriendInList(newGroup));
    }

    @Override
    public void onRemovedFromGroup(long groupId, String groupName) throws RemoteException {
        Platform.runLater(() -> {
            // 1. Hiện thông báo cho người bị kick biết
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thông báo");
            alert.setHeaderText("Bạn đã bị mời ra khỏi nhóm!");
            alert.setContentText("Bạn không còn là thành viên của nhóm: " + groupName);
            alert.show();

            // 2. Gọi hàm xử lý giao diện bên MainController
            if (mainController != null) {
                mainController.handleGroupLeft(groupId);
            }
        });
    }

    // --- 3. CÁC HÀM CẬP NHẬT GIAO DIỆN (GHIM & THEME) ---

    @Override
    public void onMessageUpdate(String msgUuid, String actionType) throws RemoteException {
        Platform.runLater(() -> {
            if (mainController != null) {
                // Gọi hàm xử lý giao diện bên MainController (Ghim/Bỏ ghim)
                mainController.handleRemoteMessageUpdate(msgUuid, actionType);
            }
        });
    }

    @Override
    public void onThemeUpdate(long conversationId, String newColor) throws RemoteException {
        Platform.runLater(() -> {
            if (mainController != null) {
                // Gọi hàm đổi màu nền ngay lập tức
                mainController.handleRemoteThemeUpdate(conversationId, newColor);
            }
        });
    }
}