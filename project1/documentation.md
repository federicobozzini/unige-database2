# Database 2, project 1
## by Federico Bozzini

## 1 - Understanding the tooling

## 2 - Generating the data

First of all I decided the basic schema of the tables:

    CREATE TABLE "zookeeper" (
    zid integer NULL,
    zname varchar(255) default NULL,
    salary integer NULL,
    age integer NULL
    );

    CREATE TABLE "cage" (
    cid integer NULL,
    cname TEXT default NULL,
    clocation integer NULL
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

    insert into zookeeper (zid, zname, salary, age)
    select g, 'zname'||g,  random()*180 + 20, random()*50 + 18
    from generate_series(1,3000) g;

    insert into cage (cid, cname, clocation)
    select g, 'cname'||g,  random()*200
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

## Physical schema design

First of all I decided to rewrite the queries in plain SQL, to get a rough idea of the execution times and the execution plans. After a first execution I added the indexes I felt may improve the execution times and recorded the results.

### 1

**Find all the zookeepers with a salary below 198.**

[Online version](http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=6d653f4c399d83581761945f4d9a8db4).

    explain analyze
    select zid
    from zookeeper
    where salary < 198

Execution plan:

    Seq Scan on zookeeper  (cost=0.00..23.50 rows=93 width=4) (actual time=0.012..0.603 rows=2946 loops=1)
        Filter: (salary < 198)
        Rows Removed by Filter: 54
    Planning time: 0.034 ms
    Execution time: 0.810 ms

A possible improved here would be to create an index on the *zookeeper.salary* column, using the btree alghorithm since the query condition is a "less than" comparison. For an additional improvement the table may be clustered on the newly created index.

    create index zsalary on zookeeper using btree (salary);

    cluster zookeeper using zsalary;

    Bitmap Heap Scan on zookeeper  (cost=24.03..56.53 rows=1000 width=4) (actual time=0.245..0.718 rows=2966 loops=1)
        Recheck Cond: (salary < 198)
        Heap Blocks: exact=20
        ->  Bitmap Index Scan on zsal  (cost=0.00..23.78 rows=1000 width=0) (actual time=0.233..0.233 rows=2966 loops=1)
            Index Cond: (salary < 198)
    Planning time: 0.145 ms
    Execution time: 0.939 ms

The execution plan changes, with no visible speedup. This is due to the the fact that the condition *salary < 198 * has a very low selectivity and we need to access most of the rows anyway.

I also tried to create a covering index on the column pair *(salary, zid)* but it was not used by the plan optimizer.

### 2

**Find, given a menu, the name of the animals fed with it and the corresponding shift.**

[Online Version](http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=ef769bd1ef43b6ea0575549e2ff94abf).

    explain analyze
    select aname, shift
    from animal
    natural join daily_feeds
    where menu = 1


    Hash Join  (cost=1439.33..2050.90 rows=7689 width=520) (actual time=9.041..19.515 rows=488 loops=1)
        Hash Cond: (animal.aid = daily_feeds.aid)
        ->  Seq Scan on animal  (cost=0.00..333.35 rows=3835 width=520) (actual time=0.011..5.253 rows=40000 loops=1)
        ->  Hash  (cost=1434.31..1434.31 rows=401 width=8) (actual time=8.994..8.994 rows=488 loops=1)
                Buckets: 1024  Batches: 1  Memory Usage: 28kB
                ->  Seq Scan on daily_feeds  (cost=0.00..1434.31 rows=401 width=8) (actual time=0.036..8.893 rows=488 loops=1)
                    Filter: (menu = 1)
                    Rows Removed by Filter: 79512
    Planning time: 0.179 ms
    Execution time: 19.572 ms

I spotted two possible strategies to improve the performance of this query. A possible improvement (2.a) would be to create an index on the *aid* column of both tables and then cluster one or both tables according to these new indexes. Another idea (2.b) may be to add an index on *daily_feeds* (by using both hash or btree) and then cluster on this index.

#### 2.a

I tried to create the indexes on *aid*:

    create index aaid on animal using btree (aid);
    create index daid on daily_feeds using btree (aid);

and cluster on these indexes:

    cluster animal using aaid;
    cluster daily_feeds using daid;

    Merge Join  (cost=1450.58..4978.58 rows=80000 width=520) (actual time=8.123..21.590 rows=488 loops=1)
    Merge Cond: (animal.aid = daily_feeds.aid)
    ->  Index Scan using aaid on animal  (cost=0.29..2228.29 rows=40000 width=520) (actual time=0.016..8.675 rows=39975 loops=1)
    ->  Sort  (cost=1450.29..1451.29 rows=400 width=8) (actual time=8.087..8.140 rows=488 loops=1)
            Sort Key: daily_feeds.aid
            Sort Method: quicksort  Memory: 47kB
            ->  Seq Scan on daily_feeds  (cost=0.00..1433.00 rows=400 width=8) (actual time=0.031..7.950 rows=488 loops=1)
                Filter: (menu = 1)
                Rows Removed by Filter: 79512
    Planning time: 0.177 ms
    Execution time: 21.667 ms

The execution plan changed to use a merge join, but there is no visible speedup.

#### 2.b

    create index dmenu on daily_feeds using btree (menu);

    Merge Join  (cost=483.79..4011.79 rows=80000 width=520) (actual time=1.105..15.784 rows=488 loops=1)
    Merge Cond: (animal.aid = daily_feeds.aid)
    ->  Index Scan using aaid on animal  (cost=0.29..2228.29 rows=40000 width=520) (actual time=0.025..9.583 rows=39975 loops=1)
    ->  Sort  (cost=483.50..484.50 rows=400 width=8) (actual time=1.061..1.134 rows=488 loops=1)
            Sort Key: daily_feeds.aid
            Sort Method: quicksort  Memory: 47kB
            ->  Bitmap Heap Scan on daily_feeds  (cost=19.10..466.21 rows=400 width=8) (actual time=0.091..0.934 rows=488 loops=1)
                Recheck Cond: (menu = 1)
                Heap Blocks: exact=295
                ->  Bitmap Index Scan on dmenu  (cost=0.00..19.00 rows=400 width=0) (actual time=0.059..0.059 rows=488 loops=1)
                        Index Cond: (menu = 1)
    Planning time: 0.172 ms
    Execution time: 15.855 ms

The index is used by the Plan Optimizer when filtering the *daily_feeds* with the menu searched. A bitmap index scan is used instead of a sequential scan. The result is a 1.5 speedup.

### 3

**Find the location of the cages where the animals of some species are located**

[Online version](http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=9c10c7803237e598c73770c33b099ab8).

    EXPLAIN ANALYZE
    SELECT clocation
    FROM cage
    NATURAL JOIN animal
    WHERE species = 'aspecies1'

    Hash Join  (cost=343.18..381.06 rows=148 width=4) (actual time=5.251..5.251 rows=0 loops=1)
    Hash Cond: (cage.cid = animal.cid)
    ->  Seq Scan on cage  (cost=0.00..28.60 rows=1560 width=8) (actual time=0.012..0.012 rows=1 loops=1)
    ->  Hash  (cost=342.94..342.94 rows=19 width=4) (actual time=5.227..5.227 rows=0 loops=1)
            Buckets: 1024  Batches: 1  Memory Usage: 8kB
            ->  Seq Scan on animal  (cost=0.00..342.94 rows=19 width=4) (actual time=5.227..5.227 rows=0 loops=1)
                Filter: (species = 'libero'::text)
                Rows Removed by Filter: 40000
    Planning time: 0.114 ms
    Execution time: 5.267 ms

I decided to test if an index (btree or hash) on *animal.species* could improve the performance of the query. Indexes on the join columns didn't not make much difference.

    Hash Join  (cost=289.96..397.96 rows=2000 width=4) (actual time=0.026..0.026 rows=0 loops=1)
    Hash Cond: (cage.cid = animal.cid)
    ->  Seq Scan on cage  (cost=0.00..33.00 rows=2000 width=8) (actual time=0.006..0.006 rows=1 loops=1)
    ->  Hash  (cost=287.46..287.46 rows=200 width=4) (actual time=0.016..0.016 rows=0 loops=1)
            Buckets: 1024  Batches: 1  Memory Usage: 8kB
            ->  Bitmap Heap Scan on animal  (cost=5.84..287.46 rows=200 width=4) (actual time=0.016..0.016 rows=0 loops=1)
                Recheck Cond: (species = 'libero'::text)
                ->  Bitmap Index Scan on aspe  (cost=0.00..5.79 rows=200 width=0) (actual time=0.015..0.015 rows=0 loops=1)
                        Index Cond: (species = 'libero'::text)
    Planning time: 0.173 ms
    Execution time: 0.048 ms

There was a 100x speedup.

### 4

**Find the zookeepers required to feed the animals caged in given location, during a given shift**

[Online version](http://dbfiddle.uk/?rdbms=postgres_9.6&fiddle=a738858268ad0728819cb9adab304876).

    EXPLAIN ANALYZE
    SELECT zname
    FROM zookeeper
    NATURAL JOIN daily_feeds
    NATURAL JOIN animal
    NATURAL JOIN cage
    WHERE shift = 1
        AND clocation = 1;

    Hash Join  (cost=504.12..1972.93 rows=546 width=516) (actual time=12.140..27.016 rows=165 loops=1)
        Hash Cond: (daily_feeds.zid = zookeeper.zid)
        ->  Hash Join  (cost=477.82..1926.55 rows=390 width=4) (actual time=11.000..25.793 rows=165 loops=1)
                Hash Cond: (daily_feeds.aid = animal.aid)
                ->  Seq Scan on daily_feeds  (cost=0.00..1434.31 rows=401 width=8) (actual time=0.023..11.925 rows=26667 loops=1)
                    Filter: (shift = 1)
                    Rows Removed by Filter: 53333
                ->  Hash  (cost=475.39..475.39 rows=194 width=4) (actual time=10.821..10.821 rows=253 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 17kB
                    ->  Hash Join  (cost=32.60..475.39 rows=194 width=4) (actual time=0.315..10.778 rows=253 loops=1)
                            Hash Cond: (animal.cid = cage.cid)
                            ->  Seq Scan on animal  (cost=0.00..422.62 rows=4862 width=8) (actual time=0.017..5.541 rows=40000 loops=1)
                            ->  Hash  (cost=32.50..32.50 rows=8 width=4) (actual time=0.280..0.280 rows=12 loops=1)
                                Buckets: 1024  Batches: 1  Memory Usage: 9kB
                                ->  Seq Scan on cage  (cost=0.00..32.50 rows=8 width=4) (actual time=0.069..0.277 rows=12 loops=1)
                                        Filter: (clocation = 1)
                                        Rows Removed by Filter: 1988
        ->  Hash  (cost=22.80..22.80 rows=280 width=520) (actual time=1.125..1.125 rows=3000 loops=1)
                Buckets: 4096 (originally 1024)  Batches: 1 (originally 1)  Memory Usage: 173kB
                ->  Seq Scan on zookeeper  (cost=0.00..22.80 rows=280 width=520) (actual time=0.011..0.573 rows=3000 loops=1)
    Planning time: 0.377 ms
    Execution time: 27.061 ms

I tried to add indexes on the join columns and on the columns used for filtering. 

    create index acid on animal using btree(cid);
    create index ccid on cage using btree(cid);
    create index daid on daily_feeds using btree(aid);
    create index aaid on animal using btree(aid);
    create index dzid on daily_feeds  (zid);
    create index zzid on zookeeper (zid);
    create index dshi on daily_feeds (shift);
    create index cloc on cage(clocation);

Some of the indexes are used but there were drastic improvements.

    Merge Join  (cost=1809.88..2886.38 rows=60000 width=516) (actual time=21.644..22.004 rows=165 loops=1)
        Merge Cond: (zookeeper.zid = daily_feeds.zid)
        ->  Index Scan using zzid on zookeeper  (cost=0.28..169.28 rows=3000 width=520) (actual time=0.008..0.186 rows=1000 loops=1)
        ->  Sort  (cost=1809.60..1819.60 rows=4000 width=4) (actual time=21.631..21.653 rows=165 loops=1)
                Sort Key: daily_feeds.zid
                Sort Method: quicksort  Memory: 32kB
                ->  Hash Join  (cost=481.28..1570.28 rows=4000 width=4) (actual time=11.770..21.581 rows=165 loops=1)
                    Hash Cond: (animal.aid = daily_feeds.aid)
                    ->  Hash Join  (cost=17.78..961.78 rows=2000 width=4) (actual time=0.048..9.781 rows=253 loops=1)
                            Hash Cond: (animal.cid = cage.cid)
                            ->  Seq Scan on animal  (cost=0.00..774.00 rows=40000 width=8) (actual time=0.006..4.850 rows=40000 loops=1)
                            ->  Hash  (cost=17.65..17.65 rows=10 width=4) (actual time=0.023..0.023 rows=12 loops=1)
                                Buckets: 1024  Batches: 1  Memory Usage: 9kB
                                ->  Bitmap Heap Scan on cage  (cost=4.36..17.65 rows=10 width=4) (actual time=0.012..0.019 rows=12 loops=1)
                                        Recheck Cond: (clocation = 1)
                                        Heap Blocks: exact=9
                                        ->  Bitmap Index Scan on cloc  (cost=0.00..4.35 rows=10 width=0) (actual time=0.009..0.009 rows=12 loops=1)
                                            Index Cond: (clocation = 1)
                    ->  Hash  (cost=458.50..458.50 rows=400 width=8) (actual time=11.715..11.715 rows=26667 loops=1)
                            Buckets: 32768 (originally 1024)  Batches: 1 (originally 1)  Memory Usage: 1298kB
                            ->  Bitmap Heap Scan on daily_feeds  (cost=11.39..458.50 rows=400 width=8) (actual time=1.796..7.353 rows=26667 loops=1)
                                Recheck Cond: (shift = 1)
                                Heap Blocks: exact=433
                                ->  Bitmap Index Scan on dshi  (cost=0.00..11.29 rows=400 width=0) (actual time=1.742..1.742 rows=26667 loops=1)
                                        Index Cond: (shift = 1)
    Planning time: 0.310 ms
    Execution time: 22.054 ms