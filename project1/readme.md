# Database 2, project 1

## by Federico Bozzini

## 1 - Understanding the tooling

The tool chosen for project 1 was postgres, version 9.6. The main source of documentation used was the official manual [0].

The main differences compared to the lessons were:

- Postgres doesn't have a production-ready implementation of the hash indexes. Hash indexes are not WAL-logged [1] and their usage is discouraged. They will be improved to production level with version 10 [2].

- Other types of indexes (GiST, SP-GiST, GIN, and BRIN) are offered by postgres for special cases like large columns or spatial data [1]. They were used in this project.

- Postgres implements all the join techniques presented during the lessons (nested join, hash join, merge join) [3]. The strategy to decide which one to use is to perform a near-exhaustive search if the number of possible combination is below a threshold and to use a genetic alghoritm otherwise [4]. It also offers a way to give hint to the query planner about the join order [5]

- Regarding projection and selection, Postgres implements multicolumn indexes [6], covering indexes [7] and index-only scans [7] and offers the possibility to use multiple indexes on the same query via bitmap scans [8].

## 2 - Generating the data

First of all I decided the basic schema of the tables:

    CREATE TABLE "zookeeper" (
        zid integer NULL,
        zname varchar(255) default NULL,
        salary integer NULL,
        age integer NULL,
        padding text
    );

    CREATE TABLE "cage" (
        cid integer NULL,
        cname TEXT default NULL,
        clocation integer NULL,
        padding1 text,
        padding2 text,
        padding3 text,
        padding4 text
    );

    CREATE TABLE "animal" (
        aid integer NULL,
        cid integer NULL,
        aname varchar(255) default NULL,
        species TEXT default NULL
    );

    CREATE TABLE "daily_feeds" (
        aid integer NULL,
        zid integer NULL,
        shift integer NULL,
        menu integer NULL
    );

To complete the project and test the queries and the indexes usage I decided to use 2 different strategies. On my personal pc I used a personal installation of [generatedata](www.generatedata.com) for the tables *zookeeper*, *cage* and *animal*. All I needed was to give the tool some constraints to get back some reasonable data. To get a more reproducible environment I also tested my queries with some online tools ([dbfiddle](http://dbfiddle.uk), [sqlfiddle](http://sqlfiddle.com/)). For this tools I could not use the generatedata services and I used some hand-made queries to generate the data.

Queries for the tables *zookeepers*, *cage*, *animal* (used only on the online tools):

    insert into zookeeper (zid, zname, salary, age, padding)
    select g, 'zname'||g,  random()*180 + 20, random()*50 + 18,
            repeat('all work and no play makes Jack a dull boy ', 500)
    from generate_series(1,3000) g;

    insert into cage (cid, cname, clocation, padding1,
                    padding2, padding3, padding4)
    select g, 'cname'||g,  random()*200,
        repeat('all work and no play makes Jack a dull boy ', 1000),
        repeat('all work and no play makes Jack a dull boy ', 1000),
        repeat('all work and no play makes Jack a dull boy ', 1000),
        repeat('all work and no play makes Jack a dull boy ', 1000)
    from generate_series(1,2000) g;

    insert into animal (aid, cid, aname, species)
    select g, random()*1900, 'aname'||g, 'aspecies'||(random()*4000)
    from generate_series(1,40000) g;

For the table *daily_feeds* the following query should ensure that every animal is fed twice a day, in two different shifs and that every zookeeper works only in one shift. Every column pair *(aid, shift)* is uniq, so menu is always unique for that key.

    insert into daily_feeds (aid,zid,shift,menu)
    select a.aid,
        floor((random()+(a.aid+g)%3)*1000)+1,
        (a.aid+g)%3+1,
        random()*160
    from animal a, generate_series(1,2) g;

To understand the pages used by every table it is possible to use the table *pg_class* and its attribute *rel_pages* [9][10].

    SELECT relname,  relpages
    FROM pg_class
    WHERE relname in ('animal', 'zookeeper', 'daily_feeds', 'cage');

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=182a693898861e1005bf152933168d9e

## 3 - Physical schema design

### 3 - A

#### Query 1

**Find all the zookeepers with a salary below 198.**

##### q1 - i, the query

    select zid
    from zookeeper
    where salary < 198

##### q1 - ii, the physical plan

It is possible to make some rough calculation to get a hint about the usefulness of an index for this query. Calculation may not be very precise because of the TOAST process [11] that reorganizes a tuple in several records. I used the function *pg_columns_size* [12] to get an approximate measure of the size of a tuple of the relation *zookeeper*.

    select pg_column_size(zid)
       + pg_column_size(zname) + pg_column_size(age)
       + pg_column_size(salary) + pg_column_size(padding) 
       from zookeeper where zid = 1;

    -- result is 323

Hence it is possible to calculate the convenience of using an index:

- Record size: 323B
- Page size: 8KB
- N tuples: 3000
- Record/page: 8000/323 = 24
- Selectivity: 198/200 = 0.99
- NR (number of records returned): 3000*.99 = 2970
- NP (number of pages returned): 3000/24 = 125

If NR > NP an index doesn't increase the performance of the query.

The best physical plan is therefore a regular sequential scan with a filter.

##### q1 - iii, the query physical schema

No index is needed. The physical schema may remain unmodified.

##### q1 - iv, the general physical schema

No index was necessary up to this point.

#### Query 2

**Given a menu, find the name of the animals fed with it and the corresponding shift.**

##### q2 - i, the query

    select aname, shift
    from animal
    natural join daily_feeds
    where menu = 1

##### q2 - ii, the physical plan

The outer table is *daily_feeds*, the inner table is *animal*. The optimal physical plan would be the one that uses 2 index-only scans with covering indexes for the two tables and then use a merge join, since the tables may be read in the proper order. The condition on menu is quite selective, so I expect a significant gain in performance.

##### q2 - iii, the query physical schema

Two indexes can be created.

    create index dmenu on daily_feeds using btree (menu);
    create index aaidana on animal using btree (aid, aname);

##### q2 - iv, the general physical schema

Two indexes are used

    create index dmenu on daily_feeds using btree (menu);
    create index aaidana on animal using btree (aid, aname);

#### Query 3

**Find the location of the cages where the animals of some species are located**

##### q3 - i, the query

    SELECT clocation
    FROM cage
    NATURAL JOIN animal
    WHERE species = 'aspecies1'

##### q3 - ii, the physical plan

Similarly to the previous query, the outer table is *animal*, the inner table is *cage*. The optimal plan would be to use a covering index on both tables with a physical plan similar to the previous query. Since the condition on species is extremely selective, I expect a gigantic gain in performance.

##### q3 - iii, the query physical schema

Two indexes can be created.

    create index ccidloc on cage(cid, clocation);
    create index aspe on animal (species);

##### q3 - iv, the general physical schema

Four indexes can now be used indexes are used

    create index dmenu on daily_feeds using btree (menu);
    create index aaidana on animal using btree (aid, aname);
    create index ccidloc on cage(cid, clocation);
    create index aspe on animal (species);

#### Query 4

**Find the zookeepers required to feed the animals caged in given location, during a given shift**

##### q4 - i, the query

    SELECT zname
    FROM zookeeper
    NATURAL JOIN daily_feeds
    NATURAL JOIN animal
    NATURAL JOIN cage
    WHERE shift = 1
    AND clocation = 1;

##### q4 - ii, the physical plan

All the tables in our schema are joined together so obviously it makes sense to try to help the joins by adding some indexes on the (potential) foreign keys. By using some multicolumn index that also includes other fields (such as *zname*) it's possible to get some covering indexes and improve the performances even further.

##### q4 - iii, the query physical schema

Four indexes can be created.

    create index acidaid on animal (cid, aid);
    create index cloccid on cage (clocation, cid);
    create index dshiaid on daily_feeds  (shift, zid, aid);
    create index zzidnam on zookeeper (zid, zname);

##### q4 - iv, the general physical schema

Since the indexes keep growing, the best approach is trying to identify is there is some redundancy.

The index *cloccid*

    create index cloccid on cage (clocation, cid);

can be substituted by *ccidloc*. The other indexes are not easy to substitute.

The indexes of the schema become:

    create index acidaid on animal (cid, aid);
    create index aaidana on animal using btree (aid, aname);
    create index aspe on animal (species);
    create index ccidloc on cage(cid, clocation);
    create index dmenu on daily_feeds using btree (menu);
    create index dshiaid on daily_feeds  (shift, zid, aid);
    create index zzidnam on zookeeper (zid, zname);

#### Query 5

**Find the locations of the cages where the animals fed with a given menu are kept**

##### q5 - i, the query

    SELECT clocation
    FROM cage
    NATURAL JOIN animal
    NATURAL JOIN daily_feeds
    WHERE menu = 1

##### q5 - ii, the physical plan

With q5 3 tables and joined, and again it makes sense to create indexes on the foreign keys. An index on the column filtering the outer table *daily_feeds.menu* can improve our performance. An index that covers also *clocation* may be useful.

##### q5 - iii, the query physical schema

Four indexes can be created.

    create index dmenaid on daily_feeds using btree (menu, aid);
    create index acidaid on animal using btree (cid, aid);
    create index ccidcloc on cage using btree (cid, clocation);

##### q5 - iv, the general physical schema

Since the indexes keep growing, the best approach is trying to identify is there is some redundancy.

The index *cloccid* and *acidaid* were already in the set of indexes of the physical schema.

The index *dmenaid* can substitute *dmenu*.

The indexes of the schema become:

    create index acidaid on animal (cid, aid);
    create index aspe on animal (species);
    create index aaidana on animal using btree (aid, aname);
    create index ccidloc on cage(cid, clocation);
    create index dshiaid on daily_feeds  (shift, zid, aid);
    create index dmenaid on daily_feeds using btree (menu, aid);
    create index zzidnam on zookeeper (zid, zname);

#### Query 6

**For each cage and each species, determine how many animals of that species are kept in that cage**

##### q6 - i, the query

    SELECT cname, species, count(*)
    FROM cage
    NATURAL JOIN animal
    GROUP BY cname, species

##### q6 - ii, the physical plan

Using indexes on the join and grouping columns can improve the performances.

##### q6 - iii, the query physical schema

Two indexes can be created.

    create index ccidcna on cage (cid, cname);
    create index acidspe on animal (cid, species);

##### q6 - iv, the general physical schema

Since the indexes keep growing, the best approach is trying to identify is there is some redundancy.

The index *acidspe* can be substituted by *acidaid* and *aspe* (not covering but performances may be comparable).

The index *ccidcna* may not be substituted.

The indexes of the schema become:

    create index acidaid on animal (cid, aid);
    create index aspe on animal (species);
    create index aaidana on animal (aid, aname);
    create index ccidloc on cage (cid, clocation);
    create index ccidcna on cage (cid, cname);
    create index dshiaid on daily_feeds  (shift, zid, aid);
    create index dmenaid on daily_feeds (menu, aid);
    create index zzidnam on zookeeper (zid, zname);

#### Final analysis of the physical schema

Below a summary of the columns used by the 6 queries is presented (the names of the tables are shortened to their initial letters):

| Query | Projection         | Selection            | Join                                     | Group By           |
|-------|--------------------|----------------------|------------------------------------------|--------------------|
| 1     | z.salary           | z.salary             |                                          |                    |
| 2     | a.name, d.shift    | d.menu               | a.aid, d.aid                             |                    |
| 3     | c.clocation        | a.species            | a.cid, c.cid                             |                    |
| 4     | z.zname            | d.shift, c.clocation | a.aid, d.aid, z.zip, d.zip, a.cid, c.cid |                    |
| 5     | c.clocation        | d.menu               | a.aid, d.aid, a.cid, c.cid               |                    |
| 6     | c.cname, a.species |                      | c.cid, a.cid                             | c.cname, a.species |

The natural primary keys of the tables (*aid* for *animal* and so on) appear to be the most frequently used columns for the queries and it may make sense to reduce the indexes used and focus mostly on these columns.

##### animal

The index on the pair (*a.cid*, *a.aid*) is used for most of the join of the *animal* table and should definitely be kept a used as a clustered index.
The index on (*a.species*) is used only on one query but offers a significant speed-up with little cost so it may covenient to keep as a non-clustered index.
The index on the pair (*a.aid*, *a.aname*) is used only by one query and is more costly to mantain. It may be dropt.

##### cage

The indexes on the pair (*c.cid*, *c.clocation*) and (*c.cid*, *c.cname*) on *cage* are covering indexes for q2, q3 and q4. To save space it is possible to keep only one index on (*c.cid*) and use it as a clustered index. Since *clocation* is used also in selection, it may be a good choice to have a secondary index on it.

##### zookeper

The only index for the table *zookeper* that emerged from the previous analysis is the index on the pair (*z.zid*, *z.zname*). It may be used as a clustered index.

##### daily_feeds

On the table *daily_feeds* two indexes were indentified (*d.shift*, *d.zid*, *d.aid*) and (*d.menu*, *d.aid*). It may be possible to keep both indexes as secondary indexes.

### 3 - B

The implementation of the indexes described before:

    create index acidaid on animal (cid, aid);
    create index aspe on animal (species);
    create index ccid on cage (cid);
    create index cloc on cage (clocation);
    create index dshizidaid on daily_feeds  (shift, zid, aid);
    create index dmenaid on daily_feeds (menu, aid);
    create index zzidnam on zookeeper (zid, zname);
    cluster animal using acidaid;
    cluster cage using ccid;
    cluster zookeeper using zzidname;

### 3 - C

Below a table with the time spent running each query the relative performance gain with and without the indexes presented before. 

| Query | time before indexing | time after indexing | percentage difference |
|-------|----------------------|---------------------|-----------------------|
| 1     | 1.927 ms             | 0.843 ms            | -56%                  |
| 2     | 20.06 ms             | 10.70               | -46%                  |
| 3     | 8.59 ms              | 1.2 ms              | -86%                  |
| 4     | 47.81 ms             | 10.37 ms            | -78%                  |
| 5     | 23.84 ms             | 11.90 ms            | -50%                  |
| 6     | 233.94 ms            | 223.93 ms           | -4%                   |

More in detail:

#### analysis - q1

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=7ea3e063baf740ae4f13351507bdee0f

The first query has an improvement in performance, but he plan remains the same. The speed-up is most likely due to caching.

#### analysis - q2

For q2, 3 different indexing strategies are presented.

The first one is the "optimal" strategy, calculated in point 3-A

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=d86b9eb0340013b40b6a9f28221cd678

The performance is improved in a significant way.

The second index strategy presented is by using covering indexes:

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=d6543f19495b5d179aff2ef21ec64c91

The covering indexes allow a index-only scan for both the tables involved, but due to the materialization the query notably worsens its performance.

The third version uses the final physical schema designed in the previous steps.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=73442700f11dc88673e782b75354e1b3

The execution plan is the same of the first version, and the performance gain is comparable as well.

#### analysis - q3

As for the previous query there are 2 different indexing strategies for q3.

The first one is to use 2 covering indexes.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=bf6ebae42d0e8bc6912d0e221300874e

In this case the results are optimal, since the results come from two index-only scans without materialization.

If instead the final physical schema is used, the speed-up is lower, but still very high.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=32d554ab975642ff66f63e5679e50459

#### analysis - q4

As for the previous query there are 2 different indexing strategies for q4.

The first one is to use 2 covering indexes.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=4a21f47c57b08eefe4b5eb57831bba2e

In this case the results are optimal, since the results come from two index-only scans without materialization.

If instead the final physical schema is used, the performance are comparable to the index-only scan.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=b100582cbd50243e373cedeba88efade

#### analysis - q5

As for q2 we have three different inxexing strategies:

- Optimal index with high performance.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=3151f82d5d804bb08041ab3b97e7ed16

- usage of covering indexes, that due to materialization offer poor performance:

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=0e2350131ddab680147220e1b831675b

- usage of the final physical schema, with a very good speed-up:

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=cd2455ec9080bb00fee665674a870f85

#### analysis - q6

In q6 there seem to be no way to make a good usage of the indexes.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=c24b12362fc5d9fc447bbe493667e20f

## 4 - Logical Tuning

The tables *animal*, *cage* and *zookeeper* presents very simple functional dependencies. All the attributes of those tables depend on the relative primary key. These relations are therefore normalized according to the BCNF.

The table *daily_feeds* features some non trivial functional dependencies:

    aid, shift -> menu
    zid -> shift

*zid* is not a superkey of the table, so the second FD makes the relations not normalized according to BCNF. A solution would be to move the attribute *shift* to the table *zookeeper* and restate the first functional dependency as

    aid, zid -> menu

This may not be a good change for the relation since the second functional dependency may just be incidental and the zookepers may change shift in the future.

The relation will not be modified.

## 6 - Query Tuning

### 6 - 1

    SELECT COUNT(*)
    FROM Animal
    GROUP BY species, cid
    HAVING cid = 27;

In the query it is better to substitute the *HAVING* clause with a *WHERE* clause. Execution time drops by more than 50%.

    SELECT COUNT(*)
    FROM Animal
    WHERE cid = 27
    GROUP BY species;

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=f639dfc50083b41e4b97b7f078715e68

### 6 - 2

    SELECT aid
    FROM Daily_Feeds
    WHERE menu = 1 OR menu = 2;

There are 2 possible ways to optimize the second query. The first would be to substite the *OR* with an IN clause:

    SELECT aid
    FROM Daily_Feeds
    WHERE menu IN (1, 2);

In this case the execution time drops by more than 80%

The other possible solution would be to convert the *OR* expression in a UNION.

    SELECT aid
    FROM Daily_Feeds
    WHERE menu = 1
    UNION
    SELECT aid
    FROM Daily_Feeds
    WHERE menu = 2;

The performance gain is smaller in this case, around 60%.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=b911abad446f13d26bdd447583df504d

### 6 - 3

    SELECT DISTINCT aid
    FROM Animal
    NATURAL JOIN Cage
    WHERE species = 'aspecies1';

There are two possible improvements for the third query. The first is removing the join with the table *cage* that is not really useful for the query. The second improvement is to remove the keyword *DISTINCT* since the *aid* are already unique:

    SELECT aid
    FROM Animal
    WHERE species = 'aspecies1';

The execution time drops by 92%.

It's important to notice that removing the *JOIN* can be done only because of the assumption that cid is never *NULL* in the *animal* table.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=2c8aff78fc9b7e9f5dd47260fc912481

### 6 - 4

    SELECT DISTINCT aid, cname
    FROM Animal 
    NATURAL JOIN Cage
    WHERE species = 'aspecies1';

For the fourth query what it's possible to do is just to remove the *DISTINCT* keyword, since there can be no duplicates in any case.

    SELECT aid, cname
    FROM Animal 
    NATURAL JOIN Cage
    WHERE species = 'aspecies1';

The execution time is reduce by 17%.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=4a3d388a8c3f72e9d87802e96e0937eb

### 6 - 5

    SELECT DISTINCT zid, aid
    FROM Daily_Feeds
    NATURAL JOIN Zookeper
    NATURAL JOIN Animal
    WHERE species = 'aspecies1' AND menu = 2;

As for the second query it is possible to remove a table that is not used (*zookeeper*) and remove the *DISTINCT* keyword.

    SELECT zid, aid
    FROM Daily_Feeds
    NATURAL JOIN Animal
    WHERE species = 'aspecies1' AND menu = 2;

Execution time is reduced by 75%.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=407f8e5c8fb1f38b7da38d5f32e3c7aa

### 6 - 6

    SELECT zid
    FROM Zookeeper
    WHERE zid IN (
        SELECT zid
        FROM Daily_Feeds
        WHERE menu = 1
    );

The sixth query uses a subquery that can be replaced by a JOIN or, under the assumption that a *zid* in the table *daily_feeds* is never null, by the subquery alone. In both case the *DISTINCT* keyword must be added to avoid returning the same *zid* multiple times.

    SELECT DISTINCT zid
    FROM Zookeeper
    NATURAL JOIN Daily_Feeds
    WHERE menu = 1

    SELECT DISTINCT zid
    FROM Daily_Feeds
    WHERE menu = 1

In the first case the execution time improves by 4%, while in the second case it drops by 75%.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=af35828c2eb79e01cf618ebdaaec5f19

### 6 - 7

    SELECT COUNT(zid)
    FROM Zookeeper
    WHERE zid IN (
        SELECT zid
        FROM Daily_Feeds
        WHERE menu = 1
    );

As for the previous query the best possible optimization would be to just use the subquery and remove the duplicates.

    SELECT COUNT(DISTINCT zid)
    FROM Daily_Feeds
    WHERE menu = 1;

The execution time drops by 80%.

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=5d6b61119601f21197e9a73b929a45a2

### 6 - 8

    SELECT zid
    FROM Zookeeper z
    WHERE age <= (
        SELECT AVG(age)
        FROM Zookeeper f
        WHERE z.salary = f.salary
    );

A possible improvement is to replace the subquery with a *JOIN* with a table with the age average per salary group. Instead of using a *GROUP BY* a possible solution would be to use a *window aggregate* [13]:

    SELECT  zid
    FROM zookeeper z
    JOIN (
        SELECT distinct salary, avg(age) over (partition by salary) as avgage
        FROM Zookeeper) z2
    ON z.salary = z2.salary
    AND age <= avgage;

The execution time is reduced by 99%!!!

http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=2a2b5b4ef0be7a208ee2c8cdab4636d0

## References

[0] https://www.postgresql.org/docs/9.6/static/index.html

[1] https://www.postgresql.org/docs/9.6/static/indexes-types.html

[2] https://wiki.postgresql.org/wiki/New_in_postgres_10#Crash_Safe.2C_Replicable_Hash_Indexes

[3] https://www.postgresql.org/docs/9.6/static/geqo.html

[4] https://www.postgresql.org/docs/9.6/static/planner-optimizer.html

[5] https://www.postgresql.org/docs/9.6/static/explicit-joins.html

[6] https://www.postgresql.org/docs/9.6/static/indexes-multicolumn.html

[7] https://www.postgresql.org/docs/9.6/static/indexes-index-only-scans.html

[8] https://www.postgresql.org/docs/9.6/static/indexes-bitmap-scans.html

[9] https://www.postgresql.org/docs/9.6/static/catalog-pg-class.html

[10] https://www.postgresql.org/docs/9.6/static/disk-usage.html

[11] https://www.postgresql.org/docs/9.6/static/storage-toast.html

[12] https://www.postgresql.org/docs/9.6/static/functions-admin.html

[13] https://www.postgresql.org/docs/9.6/static/tutorial-window.html