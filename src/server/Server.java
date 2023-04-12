package server;


import com.mysql.cj.jdbc.Driver;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Objects;

public class Server {
    static String db_url = "jdbc:mysql://127.0.0.1:3306/chat37";
    static String db_login = "root";
    static String db_pass = "";
    static Connection connection;

    public static void main(String[] args) {
        ArrayList<Socket> sockets = new ArrayList<>();
        ArrayList<User> users = new ArrayList<>();
        // 0.0.0.0 - 255.255.255.255 NAT
        try {
            ServerSocket serverSocket = new ServerSocket(9123);
            System.out.println("Сервер запущен");
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Клиент подключился");
                User user = new User(socket);
                users.add(user);
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject jsonObject = new JSONObject();
                            JSONParser jsonParser = new JSONParser();
                            while (true) {
                                jsonObject.put("msg", "Для регистрации /reg, \n" +
                                        "для авторизации /login");
                                user.getOut().writeUTF(jsonObject.toJSONString());
                                jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                String command = jsonObject.get("msg").toString();
                                connection = DriverManager.getConnection(db_url, db_login, db_pass);
                                Statement statement = connection.createStatement();
                                if (command.equals("/reg")) {
                                    jsonObject.put("msg", "Введите имя: ");
                                    user.getOut().writeUTF(jsonObject.toJSONString());
                                    jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                    String name = jsonObject.get("msg").toString();
                                    jsonObject.put("msg", "Введите login: ");
                                    user.getOut().writeUTF(jsonObject.toJSONString());
                                    jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                    String login = jsonObject.get("msg").toString();
                                    jsonObject.put("msg", "Введите password: ");
                                    user.getOut().writeUTF(jsonObject.toJSONString());
                                    jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                    String pass = jsonObject.get("msg").toString();
                                    statement.executeUpdate("INSERT INTO `users` (`name`, `login`, `pass`) VALUES ('"+name+"', '"+login+"', '"+pass+"')");
                                    statement.close();
                                    break;
                                } else if (command.equals("/login")) {
                                    boolean isTruePasswordAndLogin = false;
                                    while (!isTruePasswordAndLogin) {
                                        jsonObject.put("msg", "Введите login: ");
                                        user.getOut().writeUTF(jsonObject.toJSONString());
                                        jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                        String login = jsonObject.get("msg").toString();
                                        jsonObject.put("msg", "Введите пароль: ");
                                        user.getOut().writeUTF(jsonObject.toJSONString());
                                        jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                        String pass = jsonObject.get("msg").toString();
                                        if(login != null || pass != null) {
                                            ResultSet resultSet =
                                                    statement.executeQuery("SELECT `login`, `pass` FROM users WHERE `login` = '" + login + "' AND `pass` " +
                                                            "='" + pass + "' ");

                                            while (resultSet.next()) {
                                                String l = resultSet.getNString(1);
                                                String p = resultSet.getNString(2);
                                                if (Objects.equals(login, l) && Objects.equals(pass, p)) {
                                                    isTruePasswordAndLogin = true;
                                                    jsonObject.put("msg", "Добро пожаловать на сервер!");
                                                } else {
                                                    jsonObject.put("msg", "Вы ввели не правильные данные");
                                                }
                                                user.getOut().writeUTF(jsonObject.toJSONString());
                                                jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                            }
                                        }
                                    }


                                    statement.close();
                                    break;
                                }
                            }

                            jsonObject.put("msg", "Введите имя: ");
                            user.getOut().writeUTF(jsonObject.toJSONString());


                            String name = jsonObject.get("msg").toString();
                            boolean uniqueName = false;
                            while (!uniqueName) { // до тех пор пока имя не уникальное
                                uniqueName = true; // наверное имя уникально
                                for (User user1 : users) { // но мы проверим
                                    if (name.equals(user1.getName())) { // если нашли такое же имя, то
                                        user.getOut().writeUTF("Имя занято, выберите другое");
                                        jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                        name = jsonObject.get("msg").toString();
                                        uniqueName = false; // имя было не уникально, нужно проверить ещё раз
                                        break;
                                    }
                                }
                            }

                            user.setName(name);
                            sendUserList(users);
                            jsonObject.put("msg", user.getName() + " добро пожаловать на сервер!");
                            user.getOut().writeUTF(jsonObject.toJSONString());
                            String clientMessage;
                            while (true) {
                                jsonObject = (JSONObject) jsonParser.parse(user.getIn().readUTF());
                                clientMessage = jsonObject.get("msg").toString();
                                System.out.println(clientMessage);
                                if ((boolean) jsonObject.get("public"))
                                    for (User user1 : users) {
                                        if (name.equals(user1.getName())) continue;
                                        jsonObject.remove("msg");
                                        jsonObject.put("msg", user.getName() + ": " + clientMessage);
                                        jsonObject.put("user", user.getName());
                                        user1.getOut().writeUTF(jsonObject.toJSONString());
                                    }
                                else {
                                    // Получаем имя получателя
                                    String toName = jsonObject.get("name").toString();
                                    for (User user1 : users) { // Перебираем всех, чтобы найти нужного
                                        if (user1.getName().equals(toName)) {
                                            user1.getOut().writeUTF(user.getName() + ": " + clientMessage);
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Клиент отключился");
                            users.remove(user);
                            sendUserList(users);
                        }
                    }
                });
                thread.start();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendUserList(ArrayList<User> users) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        users.forEach(user -> {
            String username = user.getName();
            jsonArray.add(username);
        });
        jsonObject.put("onlineUsers", jsonArray);
        users.forEach(user -> {
            try {
                user.getOut().writeUTF(jsonObject.toJSONString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}