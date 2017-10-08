# Database 2, project 2

## by Federico Bozzini

## 1 - Understanding the tooling

The tool chosen for project 2 was postgres, version 9.6. The main source of documentation used was the official manual [0].

There are no big differences compared to what was introduced during the lessons.

Postgres implements Multiversion Concurrency Control (MVCC) [1] to manage concurrency.

It offers the 4 canonical isolation levels [2]. One interesting difference is that the isolation level *READ UNCOMMITED* behaves like *READ COMMITED*. The default isolation level is *READ COMMITED*.

Postgres offers also explicit locking [3] on both tables and rows.

As for the id generation Postgres offer a system-generated counter with the *Serial* datatype [4].

Postgres uses Write-Ahead Logging (WAL) [5] as a method to ensure data integrity.

## 2 - Transactions with Java and JDBC

### 2 - 1 Single transactions

To complete this part the file Ex2_1.java was created. To run the file it is possible to run the command:

    javac Ex2_1.java && java Ex2_1

It may be necessary to set the proper classpath with the JDBC drivers.

The script executes 3 transactions with *autocommit* both set as true and false. All these transactions raise an erorr during their execution.

The first transaction (ex 2) inserts some elements (with success) and fails when tries to insert the last element because of the duplicated key. The different values assigned to autocommit highlitghs two different behaviors. With autocommit on the elements inserted before the error are persisent to the database, while with autocommit off they are rolled back and not persisted.

The second transaction (ex 3) does the same insertion of the previous transaction, but it uses a function. Functions act as transactions in postgres [6] so the behavior with autocommit on or off is the same. The elements are not inserted if an error happens and the transaction is rolled back.

The third transaction (ex 4) tries to setup a savepoint and then inserts some elements. After that the transaction is rolled back to the savepoint and a new element is inserted. In the case with autocommit on it is not possible to setup a savepoint and rollback so the first inserts are successfull and the last fail. In the case with autocommit off the elements are inserted but then are not persisted and the last insertion fails, so the database is not modified.

### 2 - 2 Concurrent transactions

To complete this part the files Ex2_2_1.java and Ex2_2_1.java were created. To run the files it is possible to run the commands:

    javac Ex2_2_1.java && java Ex2_2_1 n m

    javac Ex2_2_2.java && java Ex2_2_2 n m

Where *n* is the number of concurrent threads launched and *m* the number of concurrent threads allowed. It may be necessary to set the proper classpath with the JDBC drivers.

The difference betweeen the 2 version is that the first one uses one connection shared among all the threads, while the second one uses one one connection per thread. In this example the only visible difference is that the first version is much faster (2x) but in the JDBC documentation it's stated clearly that sharing one among multiple threads is not a correct solution [7][8].

### 2 - 3 Isolation

To complete this part the files Ex2_3.java and Ex2_3bis.java were created. To run the files it is possible to run the commands:

    javac Ex2_3.java && java Ex2_3 n m i

    javac Ex2_3bis.java && java Ex2_bis3 n i

Where *n* is the number of concurrent threads launched, *m* the number of concurrent threads allowed and *i* is the isolation level (0: READ UNCOMMITTED, 1: READ COMMITTED, 2: READ REPEATABLE, 3: SERIALIZABLE). It may be necessary to set the proper classpath with the JDBC drivers.

*n* transactions execute the code:

    e <- SELECT balance FROM Account WHERE number=i
    UPDATE Account SET balance=e + 1 WHERE number=i
    c <- SELECT balance FROM Account WHERE number=0
    UPDATE Account SET balance=c - 1 WHERE number=0

The first file uses one connection for all the threads. The transactions in this case are not isolated from each others and the result of the concurrent threads is incorrect.

The second file uses one connection per thread. The behavior of the transactions in this case depends on the isolation leve. With a isolation level READ COMMITTED the thread are not isolated properly (*non-repeatable reads* are possible!) and the result is wrong. With the isolation level set to READ REPEATABLE or SERIALIZABLE the script performs correctly even if some threads fail because of the concurrent writes.

### 2 - 3 Isolation (variation)

To complete this part the file Ex2_4.java was created. To run the file it is possible to run the commands:

    javac Ex2_4.java && java Ex2_4 n m i

Where *n* is the number of concurrent threads launched, *m* the number of concurrent threads allowed and *i* is the isolation level (0: READ UNCOMMITTED, 1: READ COMMITTED, 2: READ REPEATABLE, 3: SERIALIZABLE). It may be necessary to set the proper classpath with the JDBC drivers.

*n* transactions execute the code:

    UPDATE Account SET balance=balance+1 WHERE number=i
    UPDATE Account SET balance=balance-1 WHERE number=0

In this case the UPDATE presents no problem with concurrency even with the isolation level READ COMMITTED.

### 2 - 5 Isolation (alternate)

This point was addressed in the point 2-3 and 2-5.

## 4 - Isolation levels tuning

To complete this part the files Ex4.java and Ex4bis.java were created. To run the files it is possible to run the commands:

    javac Ex4.java && java Ex4 x i1 i2 i3 m

    javac Ex4bis.java && java Ex4bis x i1 i2 i3 m

Where *x* is the number of threads T1 and T1 created (as a percentage of the number of accounts, 1000), *m* the number of concurrent threads allowed and *i1*, *i2* and *i3* are, respectively, the isolation level (0: READ UNCOMMITTED, 1: READ COMMITTED, 2: READ REPEATABLE, 3: SERIALIZABLE) of the three threads of the program. It may be necessary to set the proper classpath with the JDBC drivers.

The file Ex4.java uses a code for the write transactions that is written in a succint way and doesn't make possible for the errors to take place, even with very low isolation levels.

The file Ex4bis.java uses a code for the write transactions that is written in a more verbose way (similar to exercise 2-3) and in this case with a low isolation level some errors take place.

In this case we can indeed define correctness as the condition that the branch balance should be the same as the sum of the balance of branch accounts. If the script Ex4bis.java is run with isolation level READ COMMITTED errors happen, while with higher isolation levels no error happens. In the former case *non-repeatable reads* are possible! The correctness doesn't change when *x* changes.

The ideal isolation level for *T1* is probably READ REPEATABLE but also SERIALIZABLE doesn't introduce any error.
The ideal isolation level for *T2* and *T3* may be READ COMMITTED, but these transaction work properly with higher isolation levels.

## References

[0] https://www.postgresql.org/docs/9.6/static/index.html

[1] https://www.postgresql.org/docs/9.6/static/mvcc-intro.html

[2] https://www.postgresql.org/docs/9.6/static/transaction-iso.html

[3] https://www.postgresql.org/docs/9.6/static/explicit-locking.html

[4] https://www.postgresql.org/docs/9.6/static/datatype-numeric.html

[5] https://www.postgresql.org/docs/9.6/static/wal-intro.html

[6] https://www.postgresql.org/docs/9.6/static/plpgsql-structure.html

[7] http://docs.oracle.com/javadb/10.8.3.0/devguide/cdevconcepts89498.html

[8] https://stackoverflow.com/questions/1531073/is-java-sql-connection-thread-safe