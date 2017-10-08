
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

class Transaction1 extends Thread {
	// identifier of the transaction
	int id;
	Connection conn;
	int isolationLevel;
	int numAccounts;

	Transaction1(int id, int isolationLevel, int numAccounts) {
		this.id = id;
		this.isolationLevel = isolationLevel;
		this.numAccounts = numAccounts;
	}

	@Override
	public void run() {
		System.out.println("transaction t1 id:" + id + " started");

		String url = "jdbc:postgresql://127.0.0.1:5532/project2";
		String user = "project2";
		String pass = "project2";
		int addition = 10;

		int ms = (int) (Math.random() * 100 + 20);
		try {
			sleep(ms);
		} catch (Exception e) {
		}

		try {
			Class.forName("org.postgresql.Driver");
			conn = DriverManager.getConnection(url, user, pass);
			conn.setTransactionIsolation(isolationLevel);
			conn.setAutoCommit(false);
			int numAccountsToUpdate = (int) (Math.random() * numAccounts / 10 + 1);
			int[] accountIds = new int[numAccountsToUpdate];
			for (int i = 0; i < numAccountsToUpdate; i++) {
				accountIds[i] = (int) (Math.random() * numAccounts);
			}
			for (int accountId : accountIds) {

				String q1 = String.format("SELECT balance FROM Account WHERE number=%d", accountId);
				PreparedStatement st1 = conn.prepareStatement(q1);
				ResultSet rs1 = st1.executeQuery();
				int b = 0;
				while (rs1.next()) {
					b = rs1.getInt("balance");
				}
				String q = String.format("UPDATE account SET balance = %d WHERE number = %d", b + addition,
						accountId);
				PreparedStatement st = conn.prepareStatement(q);
				st.executeUpdate();
			}
			for (int accountId : accountIds) {
				

				String q1 = String.format("SELECT bbalance FROM branch WHERE branchid IN (select branch from account where number = %d)", accountId);
				PreparedStatement st1 = conn.prepareStatement(q1);
				ResultSet rs1 = st1.executeQuery();
				int b = 0;
				while (rs1.next()) {
					b = rs1.getInt("bbalance");
				}
				String q = String.format(
						"UPDATE branch b SET bbalance = %d WHERE branchid IN (select branch from account where number = %d)",
						b+addition, accountId);
				PreparedStatement st = conn.prepareStatement(q);
				
				st.executeUpdate();
			}
			conn.commit();
			conn.close();
		} catch (Exception e) {
			try {
				if (conn != null && !conn.getAutoCommit())
					conn.rollback();
				System.out.println("transaction t1 id:" + id + " failed");
				System.out.println(e.getMessage());
			} catch (Exception se) {
			}
		}

		System.out.println("transaction t1 id:" + id + " terminated");
	}

}

class Transaction2 extends Thread {
	// identifier of the transaction
	int id;
	Connection conn;
	int isolationLevel;
	int numAccounts;

	Transaction2(int id, int isolationLevel, int numAccounts) {
		this.id = id;
		this.isolationLevel = isolationLevel;
		this.numAccounts = numAccounts;
	}

	@Override
	public void run() {
		System.out.println("transaction t2 id:" + id + " started");

		String url = "jdbc:postgresql://127.0.0.1:5532/project2";
		String user = "project2";
		String pass = "project2";

		int ms = (int) (Math.random() * 100 + 10);
		try {
			sleep(ms);
		} catch (Exception e) {
		}

		try {
			Class.forName("org.postgresql.Driver");
			conn = DriverManager.getConnection(url, user, pass);
			conn.setTransactionIsolation(isolationLevel);
			conn.setAutoCommit(false);
			int numAccountsToUpdate = (int) (Math.random() * numAccounts / 50 + 1);
			int[] accountIds = new int[numAccountsToUpdate];
			for (int i = 0; i < numAccountsToUpdate; i++) {
				accountIds[i] = (int) (Math.random() * numAccounts);
			}
			StringBuilder strBuilder = new StringBuilder();
			strBuilder.append("(");
			for (int i = 0; i < accountIds.length; i++) {
				strBuilder.append(accountIds[i]);
				if (i != accountIds.length - 1)
					strBuilder.append(",");
			}
			strBuilder.append(")");
			String cond = strBuilder.toString();
			String q1 = String.format("select number, balance from account where number in %s", cond);
			PreparedStatement st1 = conn.prepareStatement(q1);
			ResultSet rs = st1.executeQuery();
			int number = 0, balance = 0;
			StringBuilder strBuilder2 = new StringBuilder();
			while (rs.next()) {
				number = rs.getInt("number");
				balance = rs.getInt("balance");
				strBuilder2.append("account " + number + ": " + balance + ", ");
			}
			strBuilder2.append("\n");
			String balanceSheet = strBuilder2.toString();

			System.out.println("transaction t2 id:" + id + " terminated");
			System.out.println(balanceSheet);
			conn.commit();
			conn.close();
		} catch (Exception e) {
			try {
				if (conn != null && !conn.getAutoCommit())
					conn.rollback();
				System.out.println("transaction t2 id:" + id + " failed");
				System.out.println(e.getMessage());
			} catch (Exception se) {
			}
		}
	}

}

class Transaction3 extends Thread {
	// identifier of the transaction
	int id;
	Connection conn;
	int isolationLevel;

	Transaction3(int id, int isolationLevel) {
		this.id = id;
		this.isolationLevel = isolationLevel;
	}

	@Override
	public void run() {
		System.out.println("transaction t3 id:" + id + " started");

		String url = "jdbc:postgresql://127.0.0.1:5532/project2";
		String user = "project2";
		String pass = "project2";

		int ms = (int) (Math.random() * 100);
		try {
			sleep(ms);
		} catch (Exception e) {
		}

		try {
			Class.forName("org.postgresql.Driver");
			conn = DriverManager.getConnection(url, user, pass);
			conn.setTransactionIsolation(isolationLevel);
			conn.setAutoCommit(false);
			String q1 = String.format(
					"select branchid, bbalance, sum(balance) as abalance from branch join account on branchid = branch group by branchid, bbalance order by branchid");
			PreparedStatement st1 = conn.prepareStatement(q1);
			ResultSet rs = st1.executeQuery();
			int branchid = 0, abalance = 0, bbalance = 0;
			StringBuilder strBuilder2 = new StringBuilder();
			List<Integer> branchWithErrors = new LinkedList<Integer>();
			while (rs.next()) {
				branchid = rs.getInt("branchid");
				bbalance = rs.getInt("bbalance");
				abalance = rs.getInt("abalance");
				strBuilder2.append(
						branchid + ". branch balance: " + bbalance + " branch accuonts balance: " + abalance + "\n");
				if (bbalance != abalance)
					branchWithErrors.add(branchid);
			}
			String balanceSheet = strBuilder2.toString();

			System.out.println("transaction t3 id:" + id + " terminated");
			System.out.println(balanceSheet);
			if (branchWithErrors.size() > 0) {
				String msg = String.format("%d branches with Errors!!", branchWithErrors.size());
				System.out.println(msg);
				System.out.println(branchWithErrors);
			}
			conn.commit();
			conn.close();
		} catch (Exception e) {
			System.out.println("transaction t3 id:" + id + " failed");
			System.out.println(e.getMessage());
		}
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
public class Ex4bis {

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
			PreparedStatement st2 = conn.prepareStatement("DELETE FROM branch");
			st2.executeUpdate();
			List<PreparedStatement> sts1 = new LinkedList<PreparedStatement>();
			int numBranches = 50;
			int numAccounts = 1000;
			for (int i = 1; i <= numBranches; i++) {
				String q = String.format("INSERT INTO branch VALUES (%d, 0)", i);
				PreparedStatement st = conn.prepareStatement(q);
				sts1.add(st);
			}
			for (PreparedStatement st : sts1) {
				st.executeUpdate();
			}
			List<PreparedStatement> sts2 = new LinkedList<PreparedStatement>();
			for (int i = 1; i <= numAccounts; i++) {
				int branch = i % numBranches + 1;
				String q = String.format("INSERT INTO Account VALUES (%d, %d, 0)", i, branch);
				PreparedStatement st = conn.prepareStatement(q);
				sts2.add(st);
			}
			for (PreparedStatement st : sts2) {
				st.executeUpdate();
			}

			// read command line parameters
			if (args.length < 1) {
				System.err.println("params: x isolationLevel1 isolationLevel2 isolationLevel2 parallelThreads");
				System.err.println("isolationLevel enum.");
				System.err.println("0 - READ UNCOMMITTED");
				System.err.println("1 - READ COMMITTED");
				System.err.println("2 - REPEATABLE READ");
				System.err.println("3 - SERIALIZABLE");
				System.exit(-1);
			}
			int x = Integer.parseInt(args[0]);
			int isolationLevel1 = Integer.parseInt(args[1]);
			int isolationLevel2 = Integer.parseInt(args[2]);
			int isolationLevel3 = Integer.parseInt(args[3]);
			int parallelThreads = (args[4] == null) ? 5 : Integer.parseInt(args[4]);

			Map<Integer, Integer> isolationLevelMap = new HashMap<Integer, Integer>();
			isolationLevelMap.put(0, Connection.TRANSACTION_READ_UNCOMMITTED);
			isolationLevelMap.put(1, Connection.TRANSACTION_READ_COMMITTED);
			isolationLevelMap.put(2, Connection.TRANSACTION_REPEATABLE_READ);
			isolationLevelMap.put(3, Connection.TRANSACTION_SERIALIZABLE);
			int defaultIsolationLevel = conn.getTransactionIsolation();
			int il1 = isolationLevelMap.getOrDefault(isolationLevel1, defaultIsolationLevel);
			int il2 = isolationLevelMap.getOrDefault(isolationLevel2, defaultIsolationLevel);
			int il3 = isolationLevelMap.getOrDefault(isolationLevel3, defaultIsolationLevel);
			int numT1 = x * numAccounts / 100;
			int numT2 = x * numAccounts / 100;

			Thread[] trans = new Thread[numT1 + numT2 + 1];
			for (int i = 0; i < numT1; i++) {
				trans[i] = new Transaction1(i + 1, il1, numAccounts);
			}
			for (int i = numT1; i < numT2 + numT1; i++) {
				trans[i] = new Transaction2(i + 1, il2, numAccounts);
			}
			trans[numT2 + numT1] = new Transaction3(numT2 + numT1 + 1, il3);

			System.out.println(numT1 + " T1 transactions starting");
			System.out.println(numT2 + " T2 transactions starting");
			System.out.println("1 T3 transaction starting");

			// start all transactions using a connection pool 
			ExecutorService pool = Executors.newFixedThreadPool(parallelThreads);
			for (int i = 0; i < trans.length; i++) {
				pool.execute(trans[i]);
			}

			pool.shutdown(); // end program after all transactions are done

			pool.awaitTermination(10, TimeUnit.SECONDS);
			pool.shutdownNow();
			conn.close();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		} catch (SQLException se) {
			System.out.println(se.getMessage());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		long endTime = System.currentTimeMillis();
		float totalTime = (endTime - startTime) / 1000;
		System.out.printf("execution time: %.2f secs", totalTime);
	}
}
