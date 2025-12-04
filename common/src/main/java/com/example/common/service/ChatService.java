package com.example.common.service;

import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ChatService extends Remote {
    // --- Phần Bạn bè ---
    List<UserDTO> getFriendList(long userId) throws RemoteException;
    List<UserDTO> searchUsers(String query) throws RemoteException;
    boolean addFriend(long userId, long friendId) throws RemoteException;

    // --- Phần Nhóm Chat ---
    // Tạo nhóm mới và trả về conversationId
    long createGroup(String groupName, List<Long> memberIds) throws RemoteException;

    // Lấy danh sách ID thành viên trong nhóm (để Client biết phải gửi P2P cho ai)
    List<Long> getGroupMemberIds(long conversationId) throws RemoteException;

    // --- Phần Tin nhắn (Lưu & Tải lịch sử) ---
    void saveMessage(MessageDTO msg) throws RemoteException;
    List<MessageDTO> getHistory(long conversationId) throws RemoteException;

    // --- Phần Directory (Tìm địa chỉ P2P) ---
    UserDTO getUserInfo(long userId) throws RemoteException;
}