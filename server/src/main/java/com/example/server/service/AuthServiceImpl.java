package com.example.server.service;

import com.example.common.dto.UserDTO;
import com.example.common.service.AuthService;
import com.example.common.service.ClientCallback;
import com.example.server.config.Database;
import com.example.server.util.PasswordHasher;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    // Danh sách lưu "cái loa" (Callback) của các user đang online
    // Key: UserId, Value: Callback của Client đó
    // Đổi private thành public static hoặc thêm getter
    private static final Map<Long, ClientCallback> onlineClients = new ConcurrentHashMap<>();

    public AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public UserDTO login(String username, String password, String clientIp, int p2pPort) throws RemoteException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String dbHash = rs.getString("password_hash");

                // Kiểm tra mật khẩu
                if (PasswordHasher.check(password, dbHash)) {
                    long userId = rs.getLong("id");
                    String displayName = rs.getString("display_name");

                    // Cập nhật trạng thái vào DB (để lưu IP/Port mới nhất)
                    updateUserStatus(userId, true, clientIp, p2pPort);

                    // Trả về thông tin User cho Client
                    UserDTO user = new UserDTO(userId, username, displayName);
                    user.setOnline(true);
                    user.setLastIp(clientIp);
                    user.setLastPort(p2pPort);
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Lỗi Database: " + e.getMessage());
        }
        return null; // Đăng nhập thất bại
    }

    // --- LOGIC REAL-TIME (QUAN TRỌNG) ---

    @Override
    public void registerNotification(long userId, ClientCallback callback) throws RemoteException {
        // 1. Lưu callback của người này lại
        onlineClients.put(userId, callback);
        System.out.println("User ID " + userId + " đã online và đăng ký nhận thông báo.");

        // 2. Lấy thông tin đầy đủ của người vừa online từ DB
        UserDTO me = getUserInfoFromDB(userId);

        // 3. Thông báo cho TẤT CẢ người khác biết là "Tôi vừa online"
        if (me != null) {
            notifyAllOnlineUsers(me);
        }
    }

    @Override
    public void logout(long userId) throws RemoteException {
        // 1. Xóa khỏi danh sách nhận tin
        onlineClients.remove(userId);

        // 2. Cập nhật DB thành offline
        updateUserStatus(userId, false, null, 0);

        // 3. Báo cho mọi người biết là "Tôi đã offline"
        UserDTO meOffline = new UserDTO();
        meOffline.setId(userId);
        meOffline.setOnline(false); // Quan trọng: set false để Client kia biết mà hiện chấm xám

        notifyAllOnlineUsers(meOffline);

        System.out.println("User ID " + userId + " đã đăng xuất.");
    }

    // Hàm phụ trợ: Gửi thông báo cho tất cả user khác đang online
    private void notifyAllOnlineUsers(UserDTO changeUser) {
        onlineClients.forEach((id, clientCb) -> {
            // Không báo cho chính mình (vì mình tự biết mình online rồi)
            if (id != changeUser.getId()) {
                // Chạy luồng riêng để việc gửi tin không làm chậm Server
                new Thread(() -> {
                    try {
                        clientCb.onFriendStatusChange(changeUser);
                    } catch (RemoteException e) {
                        // Nếu lỗi (Client bị tắt đột ngột/rớt mạng), xóa luôn khỏi danh sách
                        onlineClients.remove(id);
                    }
                }).start();
            }
        });
    }

    // --- CÁC HÀM HỖ TRỢ DB ---

    private void updateUserStatus(long userId, boolean online, String ip, int port) {
        String sql = "UPDATE users SET is_online = ?, last_ip = ?, last_port = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, online);
            ps.setString(2, ip);
            ps.setInt(3, port);
            ps.setLong(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private UserDTO getUserInfoFromDB(long userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"));
                u.setOnline(rs.getBoolean("is_online"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                return u;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // (Các hàm khác giữ nguyên logic cũ hoặc trả về false nếu chưa implement)
    @Override
    public boolean register(String username, String password, String displayName, String email) throws RemoteException {
        // Logic đăng ký cũ của bạn... (Copy lại nếu cần, hoặc để code cũ)
        String sql = "INSERT INTO users (username, password_hash, display_name, email) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hash(password));
            ps.setString(3, displayName);
            ps.setString(4, email);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public boolean resetPassword(String username, String email, String newPassword) throws RemoteException {
        return false; // Tạm thời chưa cần
    }
    // [MỚI] Hàm tiện ích để các Service khác lấy "cái loa" của User
    public static ClientCallback getClientCallback(long userId) {
        return onlineClients.get(userId);
    }
}