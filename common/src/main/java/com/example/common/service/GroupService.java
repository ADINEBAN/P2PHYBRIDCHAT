package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface GroupService extends Remote {

    // Tạo nhóm (Có avatar)
    long createGroup(String groupName, List<Long> memberIds, String avatarUrl) throws RemoteException;

    // Lấy danh sách ID thành viên
    List<Long> getGroupMemberIds(long conversationId) throws RemoteException;

    // Lấy danh sách các nhóm của tôi
    List<UserDTO> getMyGroups(long userId) throws RemoteException;

    // Rời nhóm
    boolean leaveGroup(long userId, long groupId) throws RemoteException;

    // Lấy danh sách thành viên đầy đủ (Bao gồm cả biệt danh)
    List<UserDTO> getGroupMembers(long groupId) throws RemoteException;

    // Thêm thành viên
    boolean addMemberToGroup(long groupId, long newMemberId) throws RemoteException;

    // Mời thành viên ra khỏi nhóm
    boolean removeMemberFromGroup(long requesterId, long groupId, long targetId) throws RemoteException;

    // Cập nhật thông tin nhóm (Tên, Ảnh)
    boolean updateGroupInfo(long requesterId, long groupId, String newName, String avatarUrl) throws RemoteException;

    // Giải tán nhóm
    boolean dissolveGroup(long requesterId, long groupId) throws RemoteException;

    // [MỚI] Cập nhật biệt danh cho thành viên
    boolean updateNickname(long groupId, long userId, String newNickname) throws RemoteException;
}