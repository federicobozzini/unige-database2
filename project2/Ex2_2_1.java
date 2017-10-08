
/*
Codice di partenza -- Labo  IV (concurrency tuning) 
Adattato da Nikolas Augsten 
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
			String q = String.format("INSERT INTO Account VALUES (%d, null, 0)", id);
			PreparedStatement st1 = conn.prepareStatement(q);
			st1.executeUpdate();
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
public class Ex2_2_1 {

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
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
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// read command line parameters
		if (args.length != 2) {
			System.err.println("params: numThreads maxConcurrent");
			System.exit(-1);
		}
		int numThreads = Integer.parseInt(args[0]);
		int maxConcurrent = Integer.parseInt(args[1]);

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

		try {
			pool.awaitTermination(10, TimeUnit.SECONDS);
			pool.shutdownNow();
			long endTime = System.currentTimeMillis();
			float totalTime = (float)((endTime - startTime) / 1000.);
			System.out.printf("execution time: %.2f secs", totalTime);
			try {
				conn.close();
			} catch (SQLException se) {
				se.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
