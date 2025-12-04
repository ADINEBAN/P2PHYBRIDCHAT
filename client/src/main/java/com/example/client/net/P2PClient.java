package com.example.client.net;

import com.example.common.dto.MessageDTO;
import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class P2PClient {
    private int port;
    private Consumer<MessageDTO> onMessageReceived;
    private boolean isRunning = true;

    public P2PClient(int port, Consumer<MessageDTO> onMessageReceived) {
        this.port = port;
        this.onMessageReceived = onMessageReceived;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("P2P Server đang lắng nghe tại port: " + port);
                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleConnection(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleConnection(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            // Đọc tin nhắn dạng Object
            MessageDTO msg = (MessageDTO) in.readObject();

            // Đẩy về giao diện (JavaFX Thread)
            Platform.runLater(() -> onMessageReceived.accept(msg));

        } catch (Exception e) {
            System.err.println("Lỗi nhận tin nhắn P2P: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    public void send(String ip, int targetPort, MessageDTO msg) {
        new Thread(() -> {
            try (Socket socket = new Socket(ip, targetPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                out.writeObject(msg);
                out.flush();
                System.out.println("Đã gửi P2P tới " + ip + ":" + targetPort);

            } catch (IOException e) {
                System.err.println("Không thể gửi tin tới " + ip + ":" + targetPort);
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
    }
}