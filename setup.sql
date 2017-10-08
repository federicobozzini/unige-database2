create user project1 with password 'project1';
create database project1;
grant all privileges on database project1 to project1;

create user project2 with password 'project2';
create database project2;
grant all privileges on database project2 to project2;

create user project3 with password 'project3';
create database project3;
grant all privileges on database project3 to project3;

alter user project3 with SUPERUSER ;