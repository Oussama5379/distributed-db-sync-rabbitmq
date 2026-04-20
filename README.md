# 🌐 Distributed Sales Synchronization System with RabbitMQ

An end-to-end distributed application demonstrating an Event-Driven Architecture using **RabbitMQ**, **Java (Javalin)**, and **MySQL**.

This project simulates a corporate network where multiple Point of Sale locations (Branch Offices) synchronize their sales asynchronously and resiliently with a central server (Head Office). The data flow is entirely decoupled and visualized on a real-time analytical dashboard.

## 🚀 Key Features

- **Fault Tolerance:** Branch offices save sales locally (`is_synced = 0`) before attempting to transmit them. If the network goes down, no data is lost.
- **Asynchronous Decoupling:** Branches and the Head Office never communicate directly. RabbitMQ acts as a Message Broker to guarantee delivery.
- **Horizontal Scalability:** Easily add new branches (bo2, bo3...) without making any modifications to the central server's code.
- **Micro-Framework API:** Utilizes Javalin to expose lightweight, fast RESTful APIs.
- **Real-Time Web Interfaces (Bonus):** Features web-based POS terminals for the branches and a dynamically updated analytical dashboard for the Head Office (built with Vanilla JS & Chart.js).

---

## 🏗️ System Architecture

1. **Branch Office (Producer):** - Web API running on port `8081` (and `8082` for Branch 2).
   - Local MySQL database (`bo1_db`).
   - Receives the sale, saves it locally, converts it to JSON (via Gson), and publishes it to the RabbitMQ `sales_sync_queue`.

2. **RabbitMQ (Message Broker):** - Hosts the persistent `sales_sync_queue`. Stores messages safely until the Head Office is ready to consume them.

3. **Head Office (Consumer):**
   - Web API running on port `8080`.
   - Listens to the RabbitMQ queue in the background.
   - Deserializes messages, inserts them into the global MySQL database (`ho_db`), and sends a manual acknowledgment (`basicAck`).
   - Exposes an `/api/sales` route to feed the web dashboard.

---

## 🛠️ Technologies Used

- **Backend:** Java 21, JDBC
- **Web Framework:** Javalin (Lightweight micro-framework based on Jetty)
- **Message Broker:** RabbitMQ (AMQP)
- **Database:** MySQL (via XAMPP)
- **Frontend:** HTML5, CSS3, Vanilla JavaScript, Chart.js
- **Utilities:** Gson (JSON Serialization), Maven

---

## ⚙️ Prerequisites and Setup

To run this project on your local machine, you will need:

- [Java Development Kit (JDK) 11+](https://adoptium.net/)
- [Apache Maven](https://maven.apache.org/)
- [XAMPP](https://www.apachefriends.org/) (or a local MySQL server)
- [RabbitMQ Server](https://www.rabbitmq.com/download.html) (with Erlang) running on the default port (5672).

### 1. Database Configuration

Open phpMyAdmin (`http://localhost/phpmyadmin`) or your MySQL terminal and execute the following SQL script to create the necessary databases and tables:

```sql
-- Global Database (Head Office)
CREATE DATABASE ho_db;
USE ho_db;
CREATE TABLE Product_Sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    region VARCHAR(50),
    product VARCHAR(50),
    qty INT,
    cost DECIMAL(10,2),
    amt DECIMAL(10,2),
    tax DECIMAL(10,2),
    total DECIMAL(10,2)
);

-- Branch Office 1 Database
CREATE DATABASE bo1_db;
USE bo1_db;
CREATE TABLE Product_Sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    region VARCHAR(50),
    product VARCHAR(50),
    qty INT,
    cost DECIMAL(10,2),
    amt DECIMAL(10,2),
    tax DECIMAL(10,2),
    total DECIMAL(10,2),
    is_synced BOOLEAN DEFAULT FALSE
);

-- (Optional) Branch Office 2 Database
CREATE DATABASE bo2_db;
USE bo2_db;
CREATE TABLE Product_Sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    region VARCHAR(50),
    product VARCHAR(50),
    qty INT,
    cost DECIMAL(10,2),
    amt DECIMAL(10,2),
    tax DECIMAL(10,2),
    total DECIMAL(10,2),
    is_synced BOOLEAN DEFAULT FALSE
);
```

### 2. Compilation

Clone the repository, open a terminal at the root of the project, and compile using Maven:

```bash
mvn clean compile
```

---

## ▶️ How to Run and Test the Project (Demonstration)

To simulate the distributed environment, run the Java programs simultaneously (in different terminals or via your IDE):

1. **Start the Head Office:**
   Execute `HeadOfficeConsumer.java`.
   _The server will start on port 8080 and begin listening to RabbitMQ._

2. **Start the Branch Office(s):**
   Execute `BranchOfficeProducer.java` (and `BranchOffice2Producer.java` if configured).
   _The servers will start on ports 8081 and 8082._

3. **The Simulation:**
   - Open the Head Office dashboard in your browser: **http://localhost:8080**
   - Open the Branch Office POS terminal in another tab: **http://localhost:8081/branch.html**
   - Submit a new sale from the branch interface.
   - Watch the Head Office chart (port 8080) update instantly as the message is consumed from RabbitMQ and saved to the global database.

---

_Project developed for the Distributed Systems module._
