
/*
Codice di partenza -- Labo  IV (concurrency tuning) 
Adattato da Nikolas Augsten 
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.*;

import java.sql.*;

/** 
 * Dummy transaction that prints a start message, waits for a random time 
 * (up to 100ms) and finally prints a status message at termination.
 */

class Transaction extends Thread {

	// identifier of the transaction
	int id;
	Connection conn;

	Transaction(int id, Connection conn) {
		this.id = id;
		this.conn = conn;
	}

	@Override
	public void run() {
		System.out.println("transaction " + id + " started");

		int ms = (int) (Math.random() * 100);
		try {
			sleep(ms);
		} catch (Exception e) {
		}

		try {
			conn.setAutoCommit(false);
			String q1 = String.format("SELECT balance FROM Account WHERE number=%d", id);
			PreparedStatement st1 = conn.prepareStatement(q1);
			ResultSet rs1 = st1.executeQuery();
			int e = 0;
			while (rs1.next()) {
				e = rs1.getInt("balance");
			}
			String q2 = String.format("UPDATE Account SET balance= %d + 1 WHERE number=%d", e, id);
			PreparedStatement st2 = conn.prepareStatement(q2);
			st2.executeUpdate();

			String q3 = String.format("SELECT balance FROM Account WHERE number=%d", 0);
			PreparedStatement st3 = conn.prepareStatement(q3);
			ResultSet rs3 = st3.executeQuery();
			int c = 0;
			while (rs3.next()) {
				c = rs3.getInt("balance");
			}
			String q4 = String.format("UPDATE Account SET balance= %d - 1 WHERE number=%d", c, 0);
			PreparedStatement st4 = conn.prepareStatement(q4);
			st4.executeUpdate();
			conn.commit();
		} catch (SQLException se) {
			System.out.println("transaction " + id + " failed");
			System.out.println(se.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("transaction " + id + " terminated");
	}

}

/**
 * <p>
 * Run numThreads transactions, where at most maxConcurrent transactions 
 * can run in parallel.
 * 
 * <p>params: numThreads maxConcurrent
 *
 */
public class Ex2_3 {

	public static void main(String[] args) {
		Connection conn = null;

		String url = "jdbc:postgresql://127.0.0.1:5532/project2";
		String user = "project2";
		String pass = "project2";

		try {
			Class.forName("org.postgresql.Driver");
			conn = DriverManager.getConnection(url, user, pass);
			PreparedStatement st1 = conn.prepareStatement("DELETE FROM account");
			st1.executeUpdate();
			PreparedStatement st2 = conn.prepareStatement("INSERT INTO Account VALUES (0, null, 100)");
			st2.executeUpdate();
			List<PreparedStatement> sts = new LinkedList<PreparedStatement>();
			for (int i = 1; i < 101; i++) {
				String q = String.format("INSERT INTO Account VALUES (%d, null, 0)", i);
				PreparedStatement st = conn.prepareStatement(q);
				sts.add(st);
			}
			for (PreparedStatement st : sts) {
				st.executeUpdate();
			}

			// read command line parameters
			if (args.length != 3) {
				System.err.println("params: numThreads maxConcurrent isolationLevel");
				System.err.println("isolationLevel enum.");
				System.err.println("0 - READ UNCOMMITTED");
				System.err.println("1 - READ COMMITTED");
				System.err.println("2 - REPEATABLE READ");
				System.err.println("3 - SERIALIZABLE");
				System.exit(-1);
			}
			int numThreads = Integer.parseInt(args[0]);
			int maxConcurrent = Integer.parseInt(args[1]);
			int isolationLevel = Integer.parseInt(args[2]);

			Map<Integer, Integer> isolationLevelMap = new HashMap<Integer, Integer>();
			isolationLevelMap.put(0, Connection.TRANSACTION_READ_UNCOMMITTED);
			isolationLevelMap.put(1, Connection.TRANSACTION_READ_COMMITTED);
			isolationLevelMap.put(2, Connection.TRANSACTION_REPEATABLE_READ);
			isolationLevelMap.put(3, Connection.TRANSACTION_SERIALIZABLE);
			int level = isolationLevelMap.get(isolationLevel);
			System.out.format("%d", conn.getTransactionIsolation());
			conn.setTransactionIsolation(level);
			System.out.format("%d", conn.getTransactionIsolation());

			// create numThreads transactions
			Transaction[] trans = new Transaction[numThreads];
			for (int i = 0; i < trans.length; i++) {
				trans[i] = new Transaction(i + 1, conn);
			}

			// start all transactions using a connection pool 
			ExecutorService pool = Executors.newFixedThreadPool(maxConcurrent);
			for (int i = 0; i < trans.length; i++) {
				pool.execute(trans[i]);
			}
			pool.shutdown(); // end program after all transactions are done

			pool.awaitTermination(10, TimeUnit.SECONDS);
			pool.shutdownNow();
			String q = String.format("SELECT balance FROM Account WHERE number=%d", 0);
			PreparedStatement st = conn.prepareStatement(q);
			ResultSet rs = st.executeQuery();
			int c = 0;
			while (rs.next()) {
				c = rs.getInt("balance");
			}
			String msg = String.format("The final balance on the account 0 is %d", c);
			System.out.println(msg);
			conn.close();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
