package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthService extends Remote {
    UserDTO login(String username, String password, String clientIp, int p2pPort) throws RemoteException;
    boolean register(String username, String password, String displayName, String email) throws RemoteException;
    boolean resetPassword(String username, String email, String newPassword) throws RemoteException;
    void logout(long userId) throws RemoteException;

    // --- THÊM HÀM NÀY ---
    // Hàm để Client đăng ký nhận thông báo Real-time
    void registerNotification(long userId, ClientCallback callback) throws RemoteException;
}