# Database 2, project 3

## by Federico Bozzini

## 1 - Understanding the tooling

The tool chosen for project 1 was postgres, version 9.6. The main source of documentation used was the official manual [0].

Postgres offers a role-based privilege management system [1]. Roles are such a central concept in Postgres privilege management that a User is just considere as a Role with LOGIN access [2].

In Postgres it is possible to create new roles [3], grant new privileges [4] and revoke the existing ones [5] with the standard SQL commands and semantics.

It is possible for a role to inherit the privileges of another role and also have ADMIN OPTIONS on it [6].

## 2 - Schema

The first thing need was to create all the tables with the *admin* user.

    create table personale
    (
        id serial not null
            constraint personale_pkey
                primary key,
        nome varchar(100),
        stipendio integer
    );

    create table studente
    (
        matricola serial not null
            constraint studente_pkey
                primary key,
        nome varchar(100),
        media integer
    );

    create table corso
    (
        codice serial not null
            constraint corso_pkey
                primary key,
        titolo varchar(100),
        prof integer
            constraint corso_personale_id_fk
                references personale
    );

    create table esame
    (
        corso serial not null
            constraint esame_corso_codice_fk
                references corso
            constraint esame_studente_matricola_fk
                references studente,
        studente serial not null,
        data timestamp,
        voto integer,
        constraint esame_corso_studente_pk
            primary key (corso, studente)
    );

## 3 - Roles and Users

The first thing necessary was to grant to the current user the *superuser* status:

    alter user admin with SUPERUSER ;

The next step was to create the other necessary roles.

The first roles created are *impiegato* and *studente*.

    CREATE ROLE impiegato;

    CREATE ROLE studente;

After that the *prof* role may be created, and it can be granted the privileges of *impiegato*.

    CREATE ROLE prof;

    GRANT impiegato TO prof;

Then it is possible to create the role *capoufficio* with the privileges of *impiegato* and *admin option* on it:

    CREATE ROLE capoufficio;

    GRANT impiegato TO capoufficio WITH ADMIN OPTION;

The same process of the previous queries may be done for the role *direttore*:

    CREATE ROLE direttore;

    GRANT prof TO direttore WITH ADMIN OPTION ;

The roles *direttore* and *capoufficio* are granted the *create* privileges:

    GRANT CREATE ON DATABASE project3 TO direttore, capoufficio;

Three new users are created and assigned the role *student*:

    CREATE USER alice IN ROLE studente;
    CREATE USER bianca IN ROLE studente;
    CREATE USER carlo IN ROLE studente;

Other users are created with the other roles:

    CREATE USER marta IN ROLE impiegato;
    CREATE USER luca IN ROLE capoufficio;

    CREATE USER nino WITH PASSWORD "nino";
    GRANT direttore TO nino WITH ADMIN OPTION;

    CREATE USER donatella;
    CREATE USER elena;
    CREATE USER fabio;
    CREATE USER olga;

Three users are granted the *prof* role by *nino*:

    GRANT prof TO donatella;
    GRANT prof TO elena;
    GRANT prof TO fabio;

There are 2 roles that *nino* can grant to other users. The first one is *prof* that *nino* can manage because of his role *direttore*, the other one is *direttore* that *nino* can manage as a single user. Both roles are granted to *olga*:

    GRANT prof TO olga;
    GRANT direttore TO olga;

## 4 - Granting privileges to the roles

**The role *capoufficio* may insert, delete, update and select the content of the table *personale*. It can also grant these privileges to other roles**

    GRANT ALL PRIVILEGES ON TABLE personale TO capoufficio WITH GRANT OPTION;

**The role *direttore* may insert, delete, update and select the content of the table *corso*. It can also grant these privileges to other roles**

    GRANT ALL PRIVILEGES ON TABLE corso TO direttore WITH GRANT OPTION;

**The role *studente* may select the content of the tables *esame*, *corso* and *studente*. **The role *impiegato* may select the content of the tables *impiegato*. The role *professor* may select the content of all the tables.

    grant select on table studente, corso, esame to studente;
    grant select on table personale to impiegato;
    grant select on table studente, corso, esame, personale to prof;

**the role *impiegato* may update the table *studente* through a function**

    CREATE FUNCTION calcolamedia() RETURNS void AS $$
    BEGIN
    //do something
    END;
    $$ LANGUAGE plpgsql;

    GRANT EXECUTE ON FUNCTION calcolamedia() TO impiegato;

**all the roles may access the table *esame* to get the number of students enrolled to a course and the average grade.**

This is not directly possible in postgres. A solution is to create a view on *esame* with the aggregated data.

    CREATE VIEW esame_aggregato AS
    SELECT
        count(studente) AS studenti,
        avg(voto)       AS media
    FROM esame
    GROUP BY corso;

    GRANT SELECT ON TABLE esame_aggregato TO studente;
    GRANT SELECT ON TABLE esame_aggregato TO impiegato;
    GRANT SELECT ON TABLE esame_aggregato TO capoufficio;
    GRANT SELECT ON TABLE esame_aggregato TO prof;
    GRANT SELECT ON TABLE esame_aggregato TO direttore;

**The role *student* can insert new rows on the table *esame*. It may not write the column *voto*. (run by user *nino*)**

    GRANT INSERT (studente, corso, data), DELETE ON TABLE esame TO studente;

**The role *prof* can insert and edit the column *voto* in the table *esame (run by user *nino*)**

    GRANT UPDATE (voto) ON TABLE esame TO prof;

The roles/privileges table:

| Role        | Table/View/Function                                         | Select | Update      | Insert      | Delete | Grantor | GrantOpt |
|-------------|-------------------------------------------------------------|--------|-------------|-------------|--------|---------|----------|
| Impiegato   | Personale                                                   | Y      |             |             |        | Admin   | N        |
| Impiegato   | calcolamedia()                                              | Y      |             |             |        | Admin   | N        |
| Impiegato   | esame_aggregato                                             | Y      |             |             |        | Admin   | N        |
| Studente    | studente, corso                                             | Y      |             |             |        | Admin   | N        |
| Studente    | esame                                                       | Y      |             | Y (partial) | Y      | Nino    | N        |
| Prof        | esame                                                       | Y      | Y (partial) |             |        | Nino    | N        |
| Prof        | Personale, corso, studente, calcolamedia(), esame_aggregato | Y      |             |             |        | Admin   | N        |
| Capoufficio | Personale                                                   | Y      | Y           | Y           | Y      | Admin   | Y        |
| Capoufficio | calcolamedia(), esame_aggregato                             | Y      |             |             |        | Admin   | Y        |
| Direttore   | Corso                                                       | Y      | Y           | Y           | Y      | Admin   | Y        |
| Direttore   | esame                                                       | Y      | Y (partial) |             |        | Nino    | Y        |
| Direttore   | Personale, corso, studente, calcolamedia(), esame_aggregato | Y      |             |             |        | Admin   | N        |

The same table can be obtained in SQL with the query:

    select * from information_schema.role_table_grants where table_name in ('corso', 'esame', 'personale', 'studente');

The User/Roles table:

| User      | Role            |
|-----------|-----------------|
| Alice     | Studente        |
| Bianca    | Studente        |
| Carlo     | Studente        |
| Marta     | Impiegato       |
| Luca      | Capoufficio     |
| Nino      | Direttore       |
| Donatella | Prof            |
| Elena     | Prof            |
| Fabio     | Prof            |
| Olga      | Direttore, Prof |

## 4 - Privileges based on content

**The role *impiegato* can access only the table *personale* when the column *stipendio* is less than 2000.**

There are different solution. The first one is to create a view with a subset of the rows and grant access only to the view, the other one is to write a policy[] with row-based access. The first approach is chosen here.

    REVOKE SELECT ON TABLE personale FROM impiegato;

    CREATE VIEW personale_povero AS
    SELECT *
    FROM personale
    WHERE stipendio < 2000;

    GRANT SELECT ON TABLE personale_povero TO impiegato;

**The role *studente* can access only the table *esame* when the column *voto* is greater than 17.**

    REVOKE SELECT ON TABLE esame FROM studente;

    CREATE VIEW esame_superato AS
    SELECT *
    FROM esame
    WHERE voto > 17;

    GRANT SELECT ON TABLE esame_superato TO studente;

**Is it possible to make *student* and *prof* access only the course where they are directly involved?**

It's not directly possible with a view. What it is possible is to use a *security definer*[7] or a *seurity policy*[8].

## 5 - Revoking privileges

By revoking a privilege to the role *direttore* with the RESTRICT option:

    REVOKE DELETE ON TABLE esame FROM direttore RESTRICT;

It is possible to see that the operation fails because of the dependent privileges.

If instead the CASCADE option is used the query succedes.

   REVOKE DELETE ON TABLE esame FROM direttore CASCADE;

It is also possible to remove a role. It appears that RESTRICT/CASCADE is not considered due to a bug with postgres [9] and that the roles granted by a role are not revoked on cascade in any case.

The following command doesn't fail:

    REVOKE direttore FROM nino RESTRICT;

## References

[0] https://www.postgresql.org/docs/9.6/static/index.html

[1] https://www.postgresql.org/docs/9.6/static/user-manag.html

[2] https://www.postgresql.org/docs/9.6/static/sql-createuser.html

[3] https://www.postgresql.org/docs/9.6/static/sql-createrole.html

[4] https://www.postgresql.org/docs/9.6/static/sql-grant.html

[5] https://www.postgresql.org/docs/9.6/static/sql-revoke.html

[6] https://www.postgresql.org/docs/9.6/static/role-membership.html

[7] https://www.postgresql.org/docs/9.6/static/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY

[8] https://www.postgresql.org/docs/9.6/static/ddl-rowsecurity.html

[9] https://stackoverflow.com/questions/31218721/postgresql-doesnt-recursively-revoke-roles