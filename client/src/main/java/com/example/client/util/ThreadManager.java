package com.example.client.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadManager {
    // Chỉ cho phép tối đa 4 luồng tải ảnh cùng lúc (để không đơ máy)
    public static final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);

    // Luồng cho các tác vụ mạng nhẹ (gửi tin, database)
    public static final ExecutorService networkExecutor = Executors.newCachedThreadPool();
}