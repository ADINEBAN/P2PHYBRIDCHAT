package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FriendService extends Remote {
    List<UserDTO> getFriendList(long userId) throws RemoteException;
    List<UserDTO> searchUsers(String query) throws RemoteException;

    // [SỬA] Đổi addFriend thành gửi lời mời
    boolean sendFriendRequest(long senderId, long receiverId) throws RemoteException;

    // [MỚI] Lấy danh sách lời mời đang chờ
    List<UserDTO> getPendingRequests(long userId) throws RemoteException;

    // [MỚI] Chấp nhận lời mời
    boolean acceptFriendRequest(long userId, long senderId) throws RemoteException;
}