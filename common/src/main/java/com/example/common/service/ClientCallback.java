package com.example.common.service;

import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientCallback extends Remote {
    // 1. Nhận tin nhắn
    void onMessageReceived(MessageDTO message) throws RemoteException;

    // 2. Trạng thái bạn bè
    void onFriendStatusChange(UserDTO friend) throws RemoteException;
    void onNewFriendRequest(UserDTO sender) throws RemoteException;
    void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException;

    // 3. Nhóm (Group)
    void onAddedToGroup(UserDTO newGroup) throws RemoteException;

    // [THÊM] Hàm bị thiếu gây lỗi override
    void onRemovedFromGroup(long groupId, String groupName) throws RemoteException;

    // 4. Cập nhật tin nhắn (Ghim/Bỏ ghim)
    // [THÊM] Hàm bị thiếu gây lỗi override
    void onMessageUpdate(String msgUuid, String actionType) throws RemoteException;

    // 5. Đổi màu nền (Theme)
    // [THÊM] Hàm bị thiếu gây lỗi override
    void onThemeUpdate(long conversationId, String newColor) throws RemoteException;
}