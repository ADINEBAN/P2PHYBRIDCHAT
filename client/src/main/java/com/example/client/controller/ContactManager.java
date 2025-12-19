package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;

public class ContactManager {
    private final MainController mc;

    public ContactManager(MainController mc) {
        this.mc = mc;
    }

    public void loadFriendListInitial() {
        new Thread(() -> {
            try {
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());
                for (UserDTO u : friends) {
                    if (unreadMap.containsKey(u.getId())) {
                        u.setUnreadCount(unreadMap.get(u.getId()));
                    }
                }
                Platform.runLater(() -> {
                    // Thao tác trên masterData
                    mc.getMasterData().clear();
                    mc.getMasterData().addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void updateFriendInList(UserDTO updatedFriend) {
        Platform.runLater(() -> {
            boolean found = false;
            // [FIX] Dùng masterData
            ObservableList<UserDTO> masterList = mc.getMasterData();

            for (int i = 0; i < masterList.size(); i++) {
                UserDTO u = masterList.get(i);
                if (u.getId() == updatedFriend.getId()) {
                    u.setOnline(updatedFriend.isOnline());
                    u.setLastIp(updatedFriend.getLastIp());
                    u.setLastPort(updatedFriend.getLastPort());
                    if (updatedFriend.getAvatarUrl() != null) u.setAvatarUrl(updatedFriend.getAvatarUrl());
                    if (updatedFriend.getDisplayName() != null) u.setDisplayName(updatedFriend.getDisplayName());
                    if (updatedFriend.getStatusMsg() != null) u.setStatusMsg(updatedFriend.getStatusMsg());

                    found = true;
                    masterList.set(i, u);

                    if (mc.currentChatUser != null && mc.currentChatUser.getId() == updatedFriend.getId()) {
                        mc.currentChatUser = u;
                    }
                    break;
                }
            }
            if (!found) masterList.add(0, updatedFriend);
        });
    }

    public void addFriendToListDirectly(UserDTO newFriend) {
        updateFriendInList(newFriend);
    }

    // [HÀM GÂY LỖI - ĐÃ SỬA]
    public void moveUserToTop(MessageDTO msg) {
        Platform.runLater(() -> {
            // [FIX QUAN TRỌNG] Lấy danh sách gốc (masterData) để thao tác xóa/thêm
            // Tuyệt đối không dùng mc.conversationList.getItems() ở đây vì nó là FilteredList
            ObservableList<UserDTO> masterList = mc.getMasterData();

            UserDTO targetUser = null;
            UserDTO currentSelection = mc.conversationList.getSelectionModel().getSelectedItem();
            int indexToRemove = -1;

            // 1. Tìm vị trí trong masterList
            for (int i = 0; i < masterList.size(); i++) {
                UserDTO u = masterList.get(i);
                boolean match = false;
                if ("GROUP".equals(u.getUsername())) {
                    if (u.getId() == msg.getConversationId()) match = true;
                } else {
                    long partnerId = (msg.getSenderId() == SessionStore.currentUser.getId())
                            ? msg.getConversationId()
                            : msg.getSenderId();
                    if (u.getId() == partnerId) match = true;
                    // Fallback check
                    if (!match && (u.getId() == msg.getSenderId() || u.getId() == msg.getConversationId())) {
                        if (!"GROUP".equals(u.getUsername())) match = true;
                    }
                }

                if (match) {
                    targetUser = u;
                    indexToRemove = i; // Lưu lại index để xóa
                    break;
                }
            }

            // 2. Thực hiện xóa và thêm lại vào đầu
            if (targetUser != null && indexToRemove != -1) {
                boolean isChattingWithThis = (mc.currentChatUser != null && mc.currentChatUser.getId() == targetUser.getId());
                if (!isChattingWithThis && msg.getSenderId() != SessionStore.currentUser.getId()) {
                    targetUser.setUnreadCount(targetUser.getUnreadCount() + 1);
                }

                // Cập nhật preview tin nhắn
                if (msg.getType() == MessageDTO.MessageType.IMAGE) targetUser.setLastMessage("[Hình ảnh]");
                else if (msg.getType() == MessageDTO.MessageType.FILE) targetUser.setLastMessage("[Tập tin]");
                else if (msg.getType() == MessageDTO.MessageType.AUDIO) targetUser.setLastMessage("[Tin nhắn thoại]");
                else targetUser.setLastMessage(msg.getContent());

                mc.isUpdatingList = true;
                try {
                    // [FIX] Xóa trên masterList -> Hết lỗi UnsupportedOperationException
                    masterList.remove(indexToRemove);
                    masterList.add(0, targetUser);

                    // Khôi phục vùng chọn
                    if (currentSelection != null) {
                        if(mc.currentChatUser != null && mc.currentChatUser.getId() == currentSelection.getId()) {
                            mc.conversationList.getSelectionModel().select(targetUser);
                        } else {
                            mc.conversationList.getSelectionModel().select(currentSelection);
                        }
                    } else {
                        mc.conversationList.getSelectionModel().clearSelection();
                    }
                } finally {
                    mc.isUpdatingList = false;
                }

                if(mc.conversationList != null) mc.conversationList.scrollTo(0);
            }
        });
    }

    public void removeConversation(long id) {
        Platform.runLater(() -> {
            // [FIX] Dùng masterData
            mc.getMasterData().removeIf(u -> u.getId() == id);
        });
    }

    public UserDTO findUserInList(long userId) {
        // [FIX] Dùng masterData
        for (UserDTO u : mc.getMasterData()) {
            if (u.getId() == userId) return u;
        }
        return null;
    }
}