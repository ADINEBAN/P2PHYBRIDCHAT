package com.example.server.service;

import com.example.common.dto.MessageDTO;
import com.example.common.service.ClientCallback;
import com.example.common.service.MessageService;
import com.example.server.config.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageServiceImpl extends UnicastRemoteObject implements MessageService {
    private static final String STORAGE_DIR = "server_files";

    public MessageServiceImpl() throws RemoteException {
        super();
        new File(STORAGE_DIR).mkdirs();
    }

    // 1. LƯU TIN NHẮN (ĐÃ SỬA: LƯU CẢ message_type)
    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        // [FIX] Thêm message_type vào câu lệnh INSERT
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at, attachment_url, uuid, message_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, msg.getConversationId());
            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent() != null ? msg.getContent() : "");
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            ps.setString(5, msg.getAttachmentUrl());
            ps.setString(6, msg.getUuid());
            ps.setString(7, msg.getType().name()); // Lưu loại tin nhắn

            int rows = ps.executeUpdate();
            if (rows > 0) System.out.println(">> Server: Đã lưu tin nhắn của User " + msg.getSenderId());
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(">> Lỗi SQL khi lưu tin nhắn: " + e.getMessage());
        }
    }

    // 2. CẬP NHẬT (SỬA / THU HỒI)
    public void updateMessage(String uuid, String newContent, MessageDTO.MessageType type) throws RemoteException {
        String sql = "";
        if (type == MessageDTO.MessageType.RECALL) {
            // [FIX] Thêm is_pinned = FALSE (Bỏ ghim ngay khi thu hồi)
            sql = "UPDATE messages SET content = 'Tin nhắn đã thu hồi', attachment_url = NULL, is_pinned = FALSE WHERE uuid = ?";
        } else if (type == MessageDTO.MessageType.EDIT) {
            sql = "UPDATE messages SET content = ? WHERE uuid = ?";
        }
        // ... (phần executeUpdate giữ nguyên) ...
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (type == MessageDTO.MessageType.EDIT) {
                ps.setString(1, newContent);
                ps.setString(2, uuid);
            } else {
                ps.setString(1, uuid);
            }
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    // 3. LẤY TIN NHẮN (QUAN TRỌNG: FIX LỖI HIỆN 3 CHẤM KHI THU HỒI)
    @Override
    public List<MessageDTO> getMessagesInConversation(long conversationId, long requestUserId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();

        String sql = "SELECT m.* FROM messages m " +
                "LEFT JOIN hidden_messages h ON m.id = h.message_id AND h.user_id = ? " +
                "WHERE m.conversation_id = ? AND h.message_id IS NULL " +
                "ORDER BY m.created_at DESC LIMIT 50";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, requestUserId);
            ps.setLong(2, conversationId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageDTO msg = new MessageDTO();
                msg.setId(rs.getLong("id"));
                msg.setUuid(rs.getString("uuid"));
                msg.setSenderId(rs.getLong("sender_id"));

                String content = rs.getString("content");
                msg.setContent(content != null ? content : "");
                msg.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                msg.setAttachmentUrl(rs.getString("attachment_url"));

                // --- [FIX LOGIC TẠI ĐÂY] ---
                // Nếu nội dung là "Tin nhắn đã thu hồi" -> Ép kiểu về RECALL
                // Điều này giúp ChatUIHelper nhận diện đúng và ẩn nút 3 chấm
                if ("Tin nhắn đã thu hồi".equals(content)) {
                    msg.setType(MessageDTO.MessageType.RECALL);
                } else {
                    try {
                        msg.setType(MessageDTO.MessageType.valueOf(rs.getString("message_type")));
                    } catch (Exception e) {
                        msg.setType(MessageDTO.MessageType.TEXT);
                    }
                }
                // ---------------------------

                try { msg.setPinned(rs.getBoolean("is_pinned")); } catch (Exception e) {}

                list.add(msg);
            }
        } catch (SQLException e) { e.printStackTrace(); }

        Collections.reverse(list);
        return list;
    }

    // 4. XÓA TIN NHẮN PHÍA TÔI
    @Override
    public boolean deleteMessageForUser(long userId, long messageId) throws RemoteException {
        String sql = "INSERT INTO hidden_messages (user_id, message_id) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return true; }
    }

    // 5. GHIM TIN NHẮN
    @Override
    public boolean pinMessage(long messageId, boolean pin) throws RemoteException {
        // 1. Cập nhật Database
        String sqlUpdate = "UPDATE messages SET is_pinned = ? WHERE id = ?";
        // 2. Lấy thông tin để thông báo (Conversation ID và UUID)
        String sqlGetInfo = "SELECT conversation_id, uuid FROM messages WHERE id = ?";

        Connection conn = null;
        try {
            conn = Database.getConnection();

            // Bước A: Update trạng thái ghim
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setBoolean(1, pin);
                ps.setLong(2, messageId);
                if (ps.executeUpdate() <= 0) return false;
            }

            // Bước B: Lấy thông tin để gửi thông báo
            long conversationId = 0;
            String uuid = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlGetInfo)) {
                ps.setLong(1, messageId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    conversationId = rs.getLong("conversation_id");
                    uuid = rs.getString("uuid");
                }
            }

            // Bước C: Gửi thông báo Real-time cho các thành viên
            if (conversationId > 0 && uuid != null) {
                notifyMessageUpdate(conversationId, uuid, pin ? "PIN" : "UNPIN");
            }

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    // Hàm phụ trợ để gửi thông báo (Copy thêm hàm này vào class MessageServiceImpl)
    private void notifyMessageUpdate(long conversationId, String uuid, String type) {
        // Lấy danh sách thành viên trong cuộc hội thoại
        String sqlMembers = "SELECT user_id FROM conversation_members WHERE conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlMembers)) {
            ps.setLong(1, conversationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                // Tìm callback của user đang online
                ClientCallback cb = AuthServiceImpl.getClientCallback(userId);
                if (cb != null) {
                    new Thread(() -> {
                        try {
                            cb.onMessageUpdate(uuid, type);
                        } catch (RemoteException e) { e.printStackTrace(); }
                    }).start();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- CÁC HÀM TIỆN ÍCH KHÁC (GIỮ NGUYÊN) ---
    @Override
    public String uploadFile(byte[] fileData, String fileName) throws RemoteException {
        if (fileData == null || fileData.length == 0) return null;
        try {
            String savedName = UUID.randomUUID().toString() + "_" + fileName;
            File dest = new File(STORAGE_DIR, savedName);
            try (FileOutputStream fos = new FileOutputStream(dest)) { fos.write(fileData); }
            return savedName;
        } catch (IOException e) { throw new RemoteException("Lỗi: " + e.getMessage()); }
    }

    @Override
    public byte[] downloadFile(String serverPath) throws RemoteException {
        if (serverPath == null) return null;
        try {
            File file = new File(STORAGE_DIR, serverPath);
            if (!file.exists()) return null;
            try (FileInputStream fis = new FileInputStream(file)) { return fis.readAllBytes(); }
        } catch (IOException e) { return null; }
    }

    @Override
    public long getPrivateConversationId(long user1, long user2) throws RemoteException {
        String sqlFind = "SELECT c.id FROM conversations c JOIN conversation_members m1 ON c.id = m1.conversation_id JOIN conversation_members m2 ON c.id = m2.conversation_id WHERE c.is_group = FALSE AND m1.user_id = ? AND m2.user_id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setLong(1, user1); ps.setLong(2, user2);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        } catch (SQLException e) {}

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO conversations (is_group, created_at) VALUES (FALSE, NOW())", Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    long id = rs.getLong(1);
                    try (PreparedStatement psMem = conn.prepareStatement("INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)")) {
                        psMem.setLong(1, id); psMem.setLong(2, user1); psMem.addBatch();
                        psMem.setLong(1, id); psMem.setLong(2, user2); psMem.addBatch();
                        psMem.executeBatch();
                    }
                    conn.commit();
                    return id;
                }
            }
        } catch (SQLException e) {}
        return 0;
    }

    @Override
    public Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException {
        Map<Long, Integer> map = new java.util.HashMap<>();
        String sql = "SELECT c.id as conv_id, c.is_group, COUNT(m.id) as unread FROM conversation_members cm JOIN conversations c ON cm.conversation_id = c.id JOIN messages m ON c.id = m.conversation_id WHERE cm.user_id = ? AND m.created_at > cm.last_seen_at AND m.sender_id != ? GROUP BY c.id, c.is_group";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId); ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long convId = rs.getLong("conv_id");
                boolean isGroup = rs.getBoolean("is_group");
                int count = rs.getInt("unread");
                if (isGroup) map.put(convId, count);
                else {
                    long friendId = getFriendIdFromConv(convId, userId);
                    if (friendId != 0) map.put(friendId, count);
                }
            }
        } catch (SQLException e) {}
        return map;
    }

    @Override
    public void markAsRead(long userId, long conversationId) throws RemoteException {
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE conversation_members SET last_seen_at = NOW() WHERE conversation_id = ? AND user_id = ?")) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    private long getFriendIdFromConv(long convId, long myId) {
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ? AND user_id != ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convId); ps.setLong(2, myId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("user_id");
        } catch (SQLException e) {}
        return 0;
    }
    @Override
    public List<MessageDTO> getPinnedMessages(long conversationId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();
        // Chọn các tin nhắn trong cuộc hội thoại này VÀ có trạng thái is_pinned là TRUE
        String sql = "SELECT m.*, u.display_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE m.conversation_id = ? AND m.is_pinned = TRUE " +
                "ORDER BY m.created_at DESC"; // Tin mới ghim lên đầu (hoặc tin mới nhất lên đầu)

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageDTO msg = new MessageDTO();
                msg.setId(rs.getLong("id"));
                msg.setSenderId(rs.getLong("sender_id"));
                msg.setSenderName(rs.getString("display_name"));
                msg.setContent(rs.getString("content"));
                msg.setUuid(rs.getString("uuid"));
                msg.setAttachmentUrl(rs.getString("attachment_url"));
                msg.setPinned(true);

                try {
                    msg.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                } catch (Exception e) {
                    msg.setCreatedAt(java.time.LocalDateTime.now());
                }

                // Xác định loại tin nhắn (Logic cũ)
                if (msg.getAttachmentUrl() != null && !msg.getAttachmentUrl().isEmpty()) {
                    if (msg.getContent().contains("[Hình ảnh]")) msg.setType(MessageDTO.MessageType.IMAGE);
                    else if (msg.getContent().contains("[Tin nhắn thoại]")) msg.setType(MessageDTO.MessageType.AUDIO);
                    else msg.setType(MessageDTO.MessageType.FILE);
                } else {
                    msg.setType(MessageDTO.MessageType.TEXT);
                }

                list.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    @Override
    public boolean updateConversationTheme(long conversationId, String colorCode) throws RemoteException {
        // Cập nhật màu vào bảng conversations
        String sql = "UPDATE conversations SET theme_color = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, colorCode);
            ps.setLong(2, conversationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getConversationTheme(long conversationId) throws RemoteException {
        // Lấy màu từ bảng conversations
        String sql = "SELECT theme_color FROM conversations WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String color = rs.getString("theme_color");
                // Nếu chưa có màu (null) hoặc rỗng thì trả về trắng (#FFFFFF)
                return (color == null || color.isEmpty()) ? "#FFFFFF" : color;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "#FFFFFF"; // Mặc định trả về trắng nếu lỗi
    }
}