import com.google.gson.Gson;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.tp.distributed.Sale;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;

public class HeadOfficeConsumer {
	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

		String rabbitmqHost = dotenv.get("RABBITMQ_HOST");
		String queueName = dotenv.get("QUEUE_NAME");
		String dbUrl = dotenv.get("HO_DB_URL");
		String dbUser = dotenv.get("DB_USER");
		String dbPassword = dotenv.get("DB_PASSWORD");

		if (rabbitmqHost == null || queueName == null || dbUrl == null || dbUser == null || dbPassword == null) {
			throw new IllegalStateException("Missing required environment variables in .env file.");
		}

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(rabbitmqHost);
		Gson gson = new Gson();

		try (Connection rabbitConnection = factory.newConnection();
			 Channel channel = rabbitConnection.createChannel()) {

			channel.queueDeclare(queueName, true, false, false, null);
			channel.basicQos(1);

			String insertSql = "INSERT INTO Product_Sales "
					+ "(id, sale_date, region, product, qty, cost, amt, tax, total, is_synced) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

			DeliverCallback deliverCallback = (consumerTag, delivery) -> {
				long deliveryTag = delivery.getEnvelope().getDeliveryTag();
				String json = new String(delivery.getBody(), StandardCharsets.UTF_8);

				try {
					Sale sale = gson.fromJson(json, Sale.class);

					try (java.sql.Connection dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
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

			CancelCallback cancelCallback = consumerTag -> {
			};

			channel.basicConsume(queueName, false, deliverCallback, cancelCallback);
			System.out.println("HeadOfficeConsumer is listening on queue: " + queueName);

			new CountDownLatch(1).await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
