package com.example.server.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    private static final Properties props = new Properties();

    static {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in == null) {
                System.err.println("Không tìm thấy file server.properties!");
            } else {
                props.load(in);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password")
        );
    }

    public static int getRmiPort() {
        return Integer.parseInt(props.getProperty("server.rmi.port", "1099"));
    }
}