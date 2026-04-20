import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.tp.distributed.Sale;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class BranchOfficeProducer {
	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

		String dbUrl = dotenv.get("BO1_DB_URL");
		String dbUser = dotenv.get("DB_USER");
		String dbPassword = dotenv.get("DB_PASSWORD");
		String rabbitmqHost = dotenv.get("RABBITMQ_HOST");
		String queueName = dotenv.get("QUEUE_NAME");

		if (dbUrl == null || dbUser == null || dbPassword == null || rabbitmqHost == null || queueName == null) {
			throw new IllegalStateException("Missing required environment variables in .env file.");
		}

		String selectSql = "SELECT id, sale_date, region, product, qty, cost, amt, tax, total, is_synced "
				+ "FROM Product_Sales WHERE is_synced = false";
		String updateSql = "UPDATE Product_Sales SET is_synced = true WHERE id = ?";

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(rabbitmqHost);
		Gson gson = new Gson();

		try (java.sql.Connection dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
			 PreparedStatement selectStatement = dbConnection.prepareStatement(selectSql);
			 PreparedStatement updateStatement = dbConnection.prepareStatement(updateSql);
			 Connection rabbitConnection = factory.newConnection();
			 Channel channel = rabbitConnection.createChannel();
			 ResultSet resultSet = selectStatement.executeQuery()) {

			channel.queueDeclare(queueName, true, false, false, null);

			while (resultSet.next()) {
				Sale sale = new Sale(
						resultSet.getInt("id"),
						resultSet.getString("sale_date"),
						resultSet.getString("region"),
						resultSet.getString("product"),
						resultSet.getInt("qty"),
						resultSet.getDouble("cost"),
						resultSet.getDouble("amt"),
						resultSet.getDouble("tax"),
						resultSet.getDouble("total"),
						resultSet.getBoolean("is_synced")
				);

				String json = gson.toJson(sale);
				channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN,
						json.getBytes(StandardCharsets.UTF_8));

				updateStatement.setInt(1, sale.getId());
				updateStatement.executeUpdate();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
