package com.example.server.service;

import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import com.example.common.service.ChatService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {

    public ChatServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public List<UserDTO> getFriendList(long userId) throws RemoteException {
        List<UserDTO> friends = new ArrayList<>();
        // Lấy danh sách bạn bè (đang đơn giản hóa: lấy tất cả user khác mình để test cho dễ)
        // Trong thực tế sẽ JOIN với bảng friendships
        String sql = "SELECT id, username, display_name, is_online, last_ip, last_port FROM users WHERE id != ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));
                u.setOnline(rs.getBoolean("is_online"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                friends.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }

    @Override
    public List<UserDTO> searchUsers(String query) throws RemoteException {
        return new ArrayList<>(); // TODO: Làm sau
    }

    @Override
    public boolean addFriend(long userId, long friendId) throws RemoteException {
        return true; // TODO: Làm sau
    }

    @Override
    public long createGroup(String groupName, List<Long> memberIds) throws RemoteException {
        // Tạo conversation mới (Logic này hơi dài, tạm thời bỏ qua để test chat 1-1 trước)
        return 0;
    }

    @Override
    public List<Long> getGroupMemberIds(long conversationId) throws RemoteException {
        return new ArrayList<>();
    }

    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        // Lưu tin nhắn vào DB để sau này load lại lịch sử
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // Tạm thời fix conversation_id = 1 cho demo chat chung
            ps.setLong(1, 1);
            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            ps.executeUpdate();
            System.out.println("Đã lưu tin nhắn từ: " + msg.getSenderId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<MessageDTO> getHistory(long conversationId) throws RemoteException {
        // Tải tin nhắn cũ
        List<MessageDTO> list = new ArrayList<>();
        String sql = "SELECT m.*, u.display_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE conversation_id = ? ORDER BY created_at ASC LIMIT 50";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, 1); // Tạm fix conversation_id = 1
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageDTO msg = new MessageDTO();
                msg.setId(rs.getLong("id"));
                msg.setSenderId(rs.getLong("sender_id"));
                msg.setSenderName(rs.getString("display_name"));
                msg.setContent(rs.getString("content"));
                msg.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                list.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public UserDTO getUserInfo(long userId) throws RemoteException {
        // Lấy IP/Port mới nhất của bạn bè để gọi P2P
        String sql = "SELECT id, last_ip, last_port, is_online FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                u.setOnline(rs.getBoolean("is_online"));
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}