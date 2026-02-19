package com.example.mycar;

import android.annotation.SuppressLint;
import android.os.StrictMode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.net.Socket;

public class ConnectionHelper {

    @SuppressLint("NewApi")
    public Connection connectionclass() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
        StrictMode.setThreadPolicy(policy);

        ServerConfig[] servers = {
                new ServerConfig("192.168.0.180", "1433",
                        "AutoSpendingDB", "user3", "123456", "Сервер 1 (основной)"),
                new ServerConfig("172.20.10.8", "1433",
                        "AutoSpendingDB", "user4", "user123456", "Сервер 2 (ноутбук)")
        };

        for (ServerConfig server : servers) {
            if (!isHostAvailable(server.ip, Integer.parseInt(server.port))) {
                continue;
            }

            Connection conn = tryDatabaseConnection(server);
            if (conn != null) {
                return conn;
            }
        }

        return null;
    }

    private boolean isHostAvailable(String ip, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(ip, port), 3000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }

    private Connection tryDatabaseConnection(ServerConfig server) {
        Connection connection = null;

        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            String connectionURL = "jdbc:jtds:sqlserver://" + server.ip + ":" + server.port + ";" +
                    "databasename=" + server.database + ";" +
                    "user=" + server.username + ";" +
                    "password=" + server.password + ";" +
                    "socketTimeout=5000;" +
                    "loginTimeout=5";

            connection = DriverManager.getConnection(connectionURL);

            if (connection != null && !connection.isClosed()) {
                return connection;
            }

        } catch (Exception ex) {
            return null;
        }

        return null;
    }

    @SuppressLint("NewApi")
    public Connection connectToSpecificServer(String serverType) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
        StrictMode.setThreadPolicy(policy);

        ServerConfig server;
        if ("notebook".equals(serverType)) {
            server = new ServerConfig("172.20.10.6", "1433",
                    "AutoSpendingDB", "user2", "user123456", "Сервер ноутбука");
        } else {
            server = new ServerConfig("192.168.0.180", "1433",
                    "AutoSpendingDB", "user3", "123456", "Основной сервер");
        }

        return tryDatabaseConnection(server);
    }

    private static class ServerConfig {
        String ip;
        String port;
        String database;
        String username;
        String password;
        String name;

        ServerConfig(String ip, String port, String database,
                     String username, String password, String name) {
            this.ip = ip;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
            this.name = name;
        }
    }
}