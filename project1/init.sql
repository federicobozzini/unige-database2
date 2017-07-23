DROP TABLE IF EXISTS zookeeper;
DROP TABLE IF EXISTS cage;
DROP TABLE IF EXISTS animal;
DROP TABLE IF EXISTS "daily_feeds";

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

insert into zookeeper (zid, zname, salary, age)
select g, 'zname'||g,  random()*180 + 20, random()*50 + 18
from generate_series(1,3000) g;

insert into cage (cid, cname, clocation)
select g, 'cname'||g,  random()*200
from generate_series(1,2000) g;

insert into animal (aid, cid, aname, species)
select g, random()*1900, 'aname'||g, 'aspecies'||(random()*4000)
from generate_series(1,40000) g;

insert into daily_feeds (aid,zid,shift,menu)
select a.aid,
    floor((random()+(a.aid+g)%3)*1000)+1,
    (a.aid+g)%3+1,
    random()*160
from animal a, generate_series(1,2) g;