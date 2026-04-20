package com.tp.distributed;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class HeadOfficeConsumer {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String rabbitmqHost = requireEnv(dotenv, "RABBITMQ_HOST");
        String queueName = requireEnv(dotenv, "QUEUE_NAME");
        String hoDbUrl = requireEnv(dotenv, "HO_DB_URL");
        String dbUser = requireEnv(dotenv, "DB_USER");
        
        final String dbPassword = (dotenv.get("DB_PASSWORD") == null || dotenv.get("DB_PASSWORD").trim().isEmpty()) ? null : dotenv.get("DB_PASSWORD");

        Thread consumerThread = new Thread(
                () -> runRabbitConsumer(rabbitmqHost, queueName, hoDbUrl, dbUser, dbPassword),
                "head-office-rabbitmq-consumer"
        );
        consumerThread.start();

        Javalin app = Javalin.create(config ->
                config.staticFiles.add(staticFileConfig -> {
                    staticFileConfig.hostedPath = "/";
                    staticFileConfig.directory = "/public";
                    staticFileConfig.location = Location.CLASSPATH;
                })
        ).start(8080);

        app.get("/api/sales", ctx -> {
            String query = "SELECT region, SUM(total) as total_sales FROM Product_Sales GROUP BY region";
            List<Map<String, Object>> salesSummary = new ArrayList<>();

            try (Connection connection = DriverManager.getConnection(hoDbUrl, dbUser, "");
                 PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("region", resultSet.getString("region"));
                    row.put("total_sales", resultSet.getDouble("total_sales"));
                    salesSummary.add(row);
                }

                String successJson = new com.google.gson.Gson().toJson(salesSummary);
                ctx.result(successJson).contentType("application/json");
            } catch (SQLException e) {
                e.printStackTrace();
                
                String errorJson = new com.google.gson.Gson().toJson(Map.of("error", "Unable to fetch sales totals"));
                ctx.status(500).result(errorJson).contentType("application/json");
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            consumerThread.interrupt();
            app.stop();
        }));
    }

    private static String requireEnv(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static void runRabbitConsumer(String rabbitmqHost, String queueName, String hoDbUrl, String dbUser,
                                          String dbPassword) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmqHost);

        String insertSql = "INSERT INTO Product_Sales "
                + "(id, sale_date, region, product, qty, cost, amt, tax, total, is_synced) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (com.rabbitmq.client.Connection rabbitConnection = factory.newConnection();
             Channel channel = rabbitConnection.createChannel()) {

            channel.queueDeclare(queueName, true, false, false, null);
            channel.basicQos(1);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    Sale sale = GSON.fromJson(payload, Sale.class);
                    if (sale == null) {
                        throw new IllegalArgumentException("Invalid payload: unable to parse sale message");
                    }

                    try (Connection dbConnection = DriverManager.getConnection(hoDbUrl, dbUser, "");
                         PreparedStatement insertStatement = dbConnection.prepareStatement(insertSql)) {

                        insertStatement.setInt(1, sale.getId());
                        insertStatement.setString(2, sale.getSale_date());
                        insertStatement.setString(3, sale.getRegion());
                        insertStatement.setString(4, sale.getProduct());
                        insertStatement.setInt(5, sale.getQty());
                        insertStatement.setDouble(6, sale.getCost());
                        insertStatement.setDouble(7, sale.getAmt());
                        insertStatement.setDouble(8, sale.getTax());
                        insertStatement.setDouble(9, sale.getTotal());
                        insertStatement.setBoolean(10, sale.getIs_synced());
                        insertStatement.executeUpdate();
                    }

                    channel.basicAck(deliveryTag, false);
                } catch (Exception e) {
                    e.printStackTrace();
                    channel.basicNack(deliveryTag, false, true);
                }
            };

            channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {
            });
            System.out.println("Head Office RabbitMQ consumer started on queue: " + queueName);

            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
