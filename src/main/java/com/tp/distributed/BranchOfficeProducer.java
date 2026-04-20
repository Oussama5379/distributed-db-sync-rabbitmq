package com.tp.distributed;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

public class BranchOfficeProducer {
	private static final Gson GSON = new Gson();
	private static final String RABBITMQ_HOST = "localhost";
	private static final String QUEUE_NAME = "sales_sync_queue";

	public static void main(String[] args) {
		Javalin app = Javalin.create(config ->
				config.staticFiles.add(staticFileConfig -> {
					staticFileConfig.hostedPath = "/";
					staticFileConfig.directory = "/public";
					staticFileConfig.location = Location.CLASSPATH;
				})
		).start(8081);

		app.post("/api/sales", ctx -> {
			try {
				Sale sale = GSON.fromJson(ctx.body(), Sale.class);
				if (sale == null || isBlank(sale.getRegion()) || isBlank(sale.getProduct())) {
					ctx.status(400).json(Map.of("error", "Donnees de vente invalides"));
					return;
				}

				int generatedId;
				String insertSql = "INSERT INTO Product_Sales "
						+ "(sale_date, region, product, qty, cost, amt, tax, total, is_synced) "
						+ "VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?, 0)";

				try (Connection dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/bo1_db", "root", "");
						 PreparedStatement insertStatement = dbConnection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

					insertStatement.setString(1, sale.getRegion());
					insertStatement.setString(2, sale.getProduct());
					insertStatement.setInt(3, sale.getQty());
					insertStatement.setDouble(4, sale.getCost());
					insertStatement.setDouble(5, sale.getAmt());
					insertStatement.setDouble(6, sale.getTax());
					insertStatement.setDouble(7, sale.getTotal());
					insertStatement.executeUpdate();

					try (ResultSet generatedKeys = insertStatement.getGeneratedKeys()) {
						if (!generatedKeys.next()) {
							throw new IllegalStateException("Impossible de recuperer l'ID de la vente");
						}
						generatedId = generatedKeys.getInt(1);
					}
				}

				sale.setId(generatedId);
				sale.setSale_date(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
				sale.setIs_synced(false);

				ConnectionFactory factory = new ConnectionFactory();
				factory.setHost(RABBITMQ_HOST);

				try (com.rabbitmq.client.Connection rabbitConnection = factory.newConnection();
						 Channel channel = rabbitConnection.createChannel()) {
					channel.queueDeclare(QUEUE_NAME, true, false, false, null);
					String saleJson = GSON.toJson(sale);
					channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN,
							saleJson.getBytes(StandardCharsets.UTF_8));
				}

				String updateSql = "UPDATE Product_Sales SET is_synced = 1 WHERE id = ?";
				try (Connection dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/bo1_db", "root", "");
						 PreparedStatement updateStatement = dbConnection.prepareStatement(updateSql)) {
					updateStatement.setInt(1, generatedId);
					updateStatement.executeUpdate();
				}
				String successJson = GSON.toJson(Map.of(
                        "message", "Vente synchronisee avec succes !",
                        "id", generatedId
                ));
                ctx.status(200).result(successJson).contentType("application/json");

			
			} catch (Exception e) {
				e.printStackTrace();
				String errorJson = GSON.toJson(Map.of(
                        "error", "Erreur lors de la synchronisation de la vente"
                ));
                ctx.status(500).result(errorJson).contentType("application/json");	
			
			}
		});

		System.out.println("Branch Office web server running on http://localhost:8081");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
