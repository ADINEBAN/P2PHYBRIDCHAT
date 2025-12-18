package com.example.common.service;

import com.example.common.dto.MessageDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface MessageService extends Remote {
    // Lưu tin nhắn mới
    void saveMessage(MessageDTO msg) throws RemoteException;

    // Cập nhật nội dung (Sửa / Thu hồi)
    void updateMessage(String uuid, String newContent, MessageDTO.MessageType type) throws RemoteException;

    // [QUAN TRỌNG] Hàm mới lấy tin nhắn (Thay thế getHistory cũ)
    List<MessageDTO> getMessagesInConversation(long conversationId, long requestUserId) throws RemoteException;

    // Upload / Download File
    String uploadFile(byte[] fileData, String fileName) throws RemoteException;
    byte[] downloadFile(String serverPath) throws RemoteException;

    // Lấy ID chat riêng tư
    long getPrivateConversationId(long user1, long user2) throws RemoteException;

    // Đếm tin nhắn chưa đọc
    Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException;

    // Đánh dấu đã đọc
    void markAsRead(long userId, long conversationId) throws RemoteException;

    // [MỚI] Ghim tin nhắn
    boolean pinMessage(long messageId, boolean pin) throws RemoteException;

    // [MỚI] Xóa tin nhắn phía tôi
    boolean deleteMessageForUser(long userId, long messageId) throws RemoteException;

    // Lấy danh sách tin nhắn đã ghim trong một cuộc trò chuyện
    List<MessageDTO> getPinnedMessages(long conversationId) throws RemoteException;
    //đổi nền
    void onThemeUpdate(long conversationId, String newColor) throws RemoteException;
    boolean updateConversationTheme(long conversationId, String colorCode) throws RemoteException;
    String getConversationTheme(long conversationId) throws RemoteException;

}