import java.sql.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

class Ex2_1 {

	private static void ex2autocommit(Connection con) {
		try {
			System.out.println("Ex. 2 with autocommit");
			con.setAutoCommit(true);

			PreparedStatement cleaningSt = con.prepareStatement("DELETE FROM account");
			cleaningSt.executeUpdate();

			PreparedStatement st1 = con.prepareStatement("INSERT INTO Account VALUES (0, null, 0)");
			PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, null, 0)");
			st1.executeUpdate();
			st2.executeUpdate();

			List<PreparedStatement> sts = new LinkedList<PreparedStatement>();
			for (int i = 1; i < 6; i++) {
				String q = String.format("INSERT INTO Account VALUES (%d, null, 0)", i);
				PreparedStatement st = con.prepareStatement(q);
				sts.add(st);
			}
			for (PreparedStatement st : sts) {
				st.executeUpdate();
			}

		} catch (SQLException e) {
			throw rethrow(e);
		}
	}

	private static void ex2noAutocommit(Connection con) {
		try {
			System.out.println("Ex. 2 with no autocommit");

			con.setAutoCommit(true);

			PreparedStatement cleaningSt = con.prepareStatement("DELETE FROM account");
			cleaningSt.executeUpdate();

			PreparedStatement st1 = con.prepareStatement("INSERT INTO Account VALUES (0, null, 0)");
			PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, null, 0)");
			st1.executeUpdate();
			st2.executeUpdate();

			con.setAutoCommit(false);

			List<PreparedStatement> sts = new LinkedList<PreparedStatement>();
			for (int i = 1; i < 6; i++) {
				String q = String.format("INSERT INTO Account VALUES (%d, null, 0)", i);
				PreparedStatement st = con.prepareStatement(q);
				sts.add(st);
			}
			for (PreparedStatement st : sts) {
				st.executeUpdate();
			}

			con.commit();
		} catch (SQLException e) {
			throw rethrow(e);
		}
	}

	private static void ex3autocommit(Connection con) {
		try {
			System.out.println("Ex. 3 with autocommit");
			con.setAutoCommit(true);

			PreparedStatement cleaningSt = con.prepareStatement("DELETE FROM account");
			cleaningSt.executeUpdate();

			PreparedStatement st1 = con.prepareStatement("INSERT INTO Account VALUES (0, null, 0)");
			PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, null, 0)");
			st1.executeUpdate();
			st2.executeUpdate();

			String funCreation = "CREATE OR REPLACE FUNCTION inserisci(num INTEGER) RETURNS VOID AS $$ "
					+ "BEGIN FOR i IN 1..num LOOP BEGIN RAISE NOTICE 'Inserito conto %', i; "
					+ "INSERT INTO Account VALUES (i, NULL, 0); END; END LOOP; END; $$ LANGUAGE plpgsql;";

			PreparedStatement st3 = con.prepareStatement(funCreation);
			st3.executeUpdate();
			PreparedStatement st4 = con.prepareStatement("SELECT * FROM inserisci(5)");
			st4.executeUpdate();

		} catch (SQLException e) {
			throw rethrow(e);
		}
	}

	private static void ex3noAutocommit(Connection con) {
		try {
			System.out.println("Ex. 3 with no autocommit");

			con.setAutoCommit(true);

			PreparedStatement cleaningSt = con.prepareStatement("DELETE FROM account");
			cleaningSt.executeUpdate();

			PreparedStatement st1 = con.prepareStatement("INSERT INTO Account VALUES (0, null, 0)");
			PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, null, 0)");
			st1.executeUpdate();
			st2.executeUpdate();

			String funCreation = "CREATE OR REPLACE FUNCTION inserisci(num INTEGER) RETURNS VOID AS $$ "
					+ "BEGIN FOR i IN 1..num LOOP BEGIN RAISE NOTICE 'Inserito conto %', i; "
					+ "INSERT INTO Account VALUES (i, NULL, 0); END; END LOOP; END; $$ LANGUAGE plpgsql;";

			PreparedStatement st3 = con.prepareStatement(funCreation);
			st3.executeUpdate();

			con.setAutoCommit(false);

			PreparedStatement st4 = con.prepareStatement("SELECT * FROM inserisci(5)");
			st4.executeUpdate();

			con.commit();

		} catch (SQLException e) {
			throw rethrow(e);
		}
	}
	
		private static void ex4autocommit(Connection con) {
			try {
				System.out.println("Ex. 4 with autocommit");
				con.setAutoCommit(true);
	
				PreparedStatement cleaningSt1 = con.prepareStatement("DELETE FROM account");
				PreparedStatement cleaningSt2 = con.prepareStatement("DELETE FROM branch");
				cleaningSt1.executeUpdate();
				cleaningSt2.executeUpdate();
	
				PreparedStatement st1 = con.prepareStatement("INSERT INTO branch VALUES (1, 0)");
				st1.executeUpdate();
				
				//Error: Cannot establish a savepoint in auto-commit mode.
				//Savepoint savepoint1 = con.setSavepoint("Savepoint1");
	
				List<PreparedStatement> sts = new LinkedList<PreparedStatement>();
				for (int i = 1; i < 5; i++) {
					String q = String.format("INSERT INTO Account VALUES (%d, 1, 0)", i);
					PreparedStatement st = con.prepareStatement(q);
					sts.add(st);
				}
				for (PreparedStatement st : sts) {
					st.executeUpdate();
				}

				//con.rollback(savepoint1);

				PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, 1, 0)");
				st2.executeUpdate();
	
			} catch (SQLException e) {
				throw rethrow(e);
			}
		}
	
		private static void ex4noAutocommit(Connection con) {
			try {
				System.out.println("Ex. 4 with autocommit");
				con.setAutoCommit(true);
	
				PreparedStatement cleaningSt1 = con.prepareStatement("DELETE FROM account");
				PreparedStatement cleaningSt2 = con.prepareStatement("DELETE FROM branch");
				cleaningSt1.executeUpdate();
				cleaningSt2.executeUpdate();
				
				con.setAutoCommit(false);
	
				PreparedStatement st1 = con.prepareStatement("INSERT INTO branch VALUES (1, 0)");
				st1.executeUpdate();
				
				Savepoint savepoint1 = con.setSavepoint("Savepoint1");
	
				List<PreparedStatement> sts = new LinkedList<PreparedStatement>();
				for (int i = 1; i < 5; i++) {
					String q = String.format("INSERT INTO Account VALUES (%d, 1, 0)", i);
					PreparedStatement st = con.prepareStatement(q);
					sts.add(st);
				}
				for (PreparedStatement st : sts) {
					st.executeUpdate();
				}

				con.rollback(savepoint1);

				PreparedStatement st2 = con.prepareStatement("INSERT INTO Account VALUES (5, 1, 0)");
				st2.executeUpdate();
	
			} catch (SQLException e) {
				throw rethrow(e);
			}
		}

	private static void executeQueries(Consumer<Connection> exercise) {
		String url = "jdbc:postgresql://127.0.0.1:5532/project2";
		String user = "project2";
		String pass = "project2";

		Connection con = null;

		try {
			Class.forName("org.postgresql.Driver");

			con = DriverManager.getConnection(url, user, pass);

			exercise.accept(con);			

		} catch (java.lang.ClassNotFoundException e) {
			System.err.print("ClassNotFoundException: ");
			System.err.println(e.getMessage());
		}

		catch (SQLException e1) {
			try {
				debugMsg("Query failed");
				debugMsg("SQLState: " + e1.getSQLState());
				debugMsg("    Code: " + e1.getErrorCode());
				debugMsg(" Message: " + e1.getMessage());
				if (con != null && !con.getAutoCommit())
					con.rollback();
			} catch (SQLException e) {
				while (e != null) {
					debugMsg("Rollback failed");
					debugMsg("SQLState: " + e.getSQLState());
					debugMsg("    Code: " + e.getErrorCode());
					debugMsg(" Message: " + e.getMessage());
					e = e.getNextException();
				}
			}

		} finally {
			debugMsg("test");
			try {
				PreparedStatement stFinal = con.prepareStatement("SELECT COUNT(*) as c FROM Account");
				ResultSet rs = stFinal.executeQuery();
				int count = 0;
				while (rs.next()) {
					count = rs.getInt("c");
				}
				String msg = String.format("%d accounts found after the transactions", count);
				System.out.println(msg);
				con.close();
			} catch (Exception e) {
				System.out.println(" An error happened while closing the connection");
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Throwable> RuntimeException rethrow(Throwable throwable) throws T {
		throw (T) throwable; // rely on vacuous cast
	}

	public static void debugMsg(String s) {
		if (debug)
			System.out.println(s);
	}

	private static Connection con;

	private static boolean debug = false;

	public static void main(String args[]) {
		executeQueries(Ex2_1::ex2autocommit);
		executeQueries(Ex2_1::ex2noAutocommit);
		executeQueries(Ex2_1::ex3autocommit);
		executeQueries(Ex2_1::ex3noAutocommit);
		executeQueries(Ex2_1::ex4autocommit);
		executeQueries(Ex2_1::ex4noAutocommit);
	}
}
