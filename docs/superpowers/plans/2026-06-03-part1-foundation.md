# Part 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete data layer and project skeleton for the World Cup 2026 prediction game — Maven project, Docker setup, Flyway schema, JPA entities, and Spring Data repositories — with no security or controllers yet.

**Architecture:** Single Spring Boot 3.3.x monolith with a database managed by Flyway migrations. JPA entities with Lombok reduce boilerplate; Spring Data repositories provide all data access. Docker Compose runs the app and database together for local development.

**Tech Stack:** Java 21, Spring Boot 3.3.x, SQLite (default) / PostgreSQL 16 (production), Flyway, Spring Data JPA, Hibernate, Lombok, JUnit 5

**Depends on:** nothing — this is Part 1

**Next parts:** Part 2 (OAuth2 Auth + Security), Part 3 (Game Engine / Scoring), Part 4 (Thymeleaf UI), Part 5 (Admin Panel), Part 6 (External API Sync)

---

## Database Support

This app supports **two database backends** selected via Spring profiles:

| Profile | Database | Use case |
|---|---|---|
| `sqlite` (default) | SQLite file-based DB | Local development, simple/zero-config deployment |
| `postgres` | PostgreSQL 16 | Production, multi-user, hosted environments |

**Default is SQLite** — no database server required to run the app locally. Just run `./mvnw spring-boot:run` and the app creates `worldcup.db` in the working directory.

**To switch to PostgreSQL:** set the environment variable `APP_PROFILE=postgres` (or `SPRING_PROFILES_ACTIVE=postgres`). Then set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.

**Schema strategy:** The Flyway migration SQL is written in standard ANSI SQL that runs on both SQLite and PostgreSQL. PostgreSQL-specific features (ENUMs, `TIMESTAMP WITH TIME ZONE`, `BIGSERIAL`, triggers) are replaced with portable equivalents. Enum values are stored as `VARCHAR(50)` strings; Hibernate's `@Enumerated(EnumType.STRING)` handles the mapping.

---

## File Structure

```
world-cup-prediction/
├── pom.xml                                                         # Maven project descriptor
├── Dockerfile                                                      # Multi-stage Docker build
├── docker-compose.yml                                              # App + PostgreSQL services
├── .gitignore                                                      # Java/Maven/IDE ignores
├── src/
│   ├── main/
│   │   ├── java/com/worldcup/prediction/
│   │   │   ├── WorldCupPredictionApplication.java                  # Spring Boot entry point
│   │   │   ├── domain/
│   │   │   │   ├── enums/
│   │   │   │   │   ├── UserStatus.java                             # PENDING, ACTIVE, DISABLED
│   │   │   │   │   ├── UserRole.java                               # PARTICIPANT, ADMIN
│   │   │   │   │   ├── OAuthProvider.java                          # GOOGLE, LINKEDIN
│   │   │   │   │   ├── MatchStatus.java                            # SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
│   │   │   │   │   ├── MatchStage.java                             # GROUP, ROUND_OF_32, ROUND_OF_16, QUARTER_FINAL, SEMI_FINAL, THIRD_PLACE, FINAL
│   │   │   │   │   ├── PredictionScore.java                        # EXACT, CORRECT_DRAW, CORRECT_WINNER, WRONG, PENDING
│   │   │   │   │   └── AuditAction.java                            # All auditable action types
│   │   │   │   ├── User.java                                       # JPA entity: users table
│   │   │   │   ├── OAuthIdentity.java                              # JPA entity: oauth_identities table
│   │   │   │   ├── Team.java                                       # JPA entity: teams table
│   │   │   │   ├── Group.java                                      # JPA entity: groups table
│   │   │   │   ├── Match.java                                      # JPA entity: matches table
│   │   │   │   ├── Prediction.java                                 # JPA entity: predictions table
│   │   │   │   ├── TournamentWinnerPrediction.java                 # JPA entity: tournament_winner_predictions
│   │   │   │   └── AuditLog.java                                   # JPA entity: audit_logs table
│   │   │   └── repository/
│   │   │       ├── UserRepository.java                             # User CRUD + lookup by email
│   │   │       ├── OAuthIdentityRepository.java                    # Lookup by provider+subject
│   │   │       ├── TeamRepository.java                             # Lookup by FIFA code
│   │   │       ├── GroupRepository.java                            # Lookup by group name
│   │   │       ├── MatchRepository.java                            # Lookups by stage, status, date
│   │   │       ├── PredictionRepository.java                       # Lookups by user+match, user+round
│   │   │       ├── TournamentWinnerPredictionRepository.java       # One per user
│   │   │       └── AuditLogRepository.java                         # Search by action, user, date
│   │   └── resources/
│   │       ├── application.properties                              # Shared config + default profile selection
│   │       ├── application-sqlite.properties                       # SQLite datasource (default profile)
│   │       ├── application-postgres.properties                     # PostgreSQL datasource (production)
│   │       └── db/migration/
│   │           └── V1__initial_schema.sql                          # ANSI SQL schema (SQLite + PostgreSQL)
│   └── test/
│       └── java/com/worldcup/prediction/
│           ├── WorldCupPredictionApplicationTests.java             # Context loads test (SQLite)
│           └── repository/
│               ├── UserRepositoryTest.java                         # Integration tests (SQLite)
│               ├── MatchRepositoryTest.java                        # Integration tests
│               └── PredictionRepositoryTest.java                   # Integration tests
```

---

### Task 1: Maven Project Setup

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/com/worldcup/prediction/WorldCupPredictionApplication.java`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.worldcup</groupId>
    <artifactId>world-cup-prediction</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>world-cup-prediction</name>
    <description>FIFA World Cup 2026 Prediction Game</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web (MVC + Thymeleaf later) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Security (needed for OAuth2 in Part 2; skeleton only in Part 1) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- OAuth2 Client (Google + LinkedIn) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-client</artifactId>
        </dependency>

        <!-- Thymeleaf (needed for Part 4 UI) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

        <!-- Thymeleaf Spring Security extras -->
        <dependency>
            <groupId>org.thymeleaf.extras</groupId>
            <artifactId>thymeleaf-extras-springsecurity6</artifactId>
        </dependency>

        <!-- Flyway core -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <!-- Flyway PostgreSQL support (no-op for SQLite profile) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- PostgreSQL JDBC driver (runtime only — activated by postgres profile) -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- SQLite JDBC driver -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.3.0</version>
        </dependency>

        <!-- Hibernate community dialects (provides SQLiteDialect) -->
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring Boot Actuator (health checks for Docker) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- DevTools (hot reload in dev) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create .gitignore**

```gitignore
# Maven
target/
*.jar
*.war
*.class

# IDE
.idea/
*.iml
*.ipr
*.iws
.vscode/
*.eclipse
.classpath
.project
.settings/

# Spring Boot
application-local.properties
application-secrets.properties

# Environment
.env
*.env

# macOS
.DS_Store

# Logs
*.log
logs/

# SQLite database files
*.db
*.db-shm
*.db-wal

# Docker volumes
postgres-data/
```

- [ ] **Step 3: Create WorldCupPredictionApplication.java**

```java
package com.worldcup.prediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorldCupPredictionApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorldCupPredictionApplication.class, args);
    }
}
```

- [ ] **Step 4: Verify Maven wrapper is available, then compile**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction
mvn wrapper:wrapper -Dmaven=3.9.6 || true
./mvnw compile -q
```

Expected: `BUILD SUCCESS` with no compilation errors.

- [ ] **Step 5: Commit**

```bash
git init
git add pom.xml .gitignore src/main/java/com/worldcup/prediction/WorldCupPredictionApplication.java
git commit -m "feat: initialize Maven project with Spring Boot 3.3.x skeleton and SQLite + PostgreSQL support"
```

---

### Task 2: Application Properties

**Files:**
- Create: `src/main/resources/application.properties`
- Create: `src/main/resources/application-sqlite.properties`
- Create: `src/main/resources/application-postgres.properties`

- [ ] **Step 1: Create application.properties (shared base + default profile)**

```properties
spring.application.name=world-cup-prediction

# Default profile: sqlite (zero-config local deployment)
# Override with: APP_PROFILE=postgres (or SPRING_PROFILES_ACTIVE=postgres)
spring.profiles.active=${APP_PROFILE:sqlite}

# JPA / Hibernate (dialect set per profile)
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.show-sql=false

# Flyway
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=false

# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized

# Server
server.port=8080
server.servlet.session.timeout=7d

# Security placeholder (configured in Part 2)
# spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
# spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
# spring.security.oauth2.client.registration.linkedin.client-id=${LINKEDIN_CLIENT_ID}
# spring.security.oauth2.client.registration.linkedin.client-secret=${LINKEDIN_CLIENT_SECRET}

# Football API (configured in Part 6)
# football.api.key=${FOOTBALL_API_KEY:}
# football.api.base-url=https://api.football-data.org/v4

# Logging
logging.level.com.worldcup.prediction=DEBUG
logging.level.org.springframework.security=INFO
logging.level.org.flywaydb=INFO
```

- [ ] **Step 2: Create application-sqlite.properties (SQLite — default profile)**

```properties
# SQLite file-based database (default deployment)
# Database file location: override with SQLITE_PATH env var
spring.datasource.url=jdbc:sqlite:${SQLITE_PATH:./worldcup.db}
spring.datasource.driver-class-name=org.sqlite.JDBC

# SQLite dialect from hibernate-community-dialects
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=validate

# Flyway: SQLite does not support DDL transactions — mixed mode required
spring.flyway.locations=classpath:db/migration
spring.flyway.mixed=true

# Show SQL in development (SQLite is always local)
spring.jpa.show-sql=true

# DevTools
spring.devtools.restart.enabled=true
spring.devtools.livereload.enabled=true

# Thymeleaf hot reload
spring.thymeleaf.cache=false
```

- [ ] **Step 3: Create application-postgres.properties (PostgreSQL — production)**

```properties
# PostgreSQL datasource
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/worldcup}
spring.datasource.username=${DATABASE_USERNAME:worldcup}
spring.datasource.password=${DATABASE_PASSWORD:worldcup}
spring.datasource.driver-class-name=org.postgresql.Driver

# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# PostgreSQL dialect
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate

# Flyway
spring.flyway.locations=classpath:db/migration

# Production-style logging
logging.level.com.worldcup.prediction=INFO
logging.level.org.springframework.security=WARN
spring.jpa.show-sql=false
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties \
        src/main/resources/application-sqlite.properties \
        src/main/resources/application-postgres.properties
git commit -m "feat: add application properties for sqlite (default) and postgres profiles"
```

---

### Task 3: Flyway Database Schema

**Files:**
- Create: `src/main/resources/db/migration/V1__initial_schema.sql`

The schema is written in **standard ANSI SQL** compatible with both SQLite and PostgreSQL:
- No `CREATE TYPE ... AS ENUM` — use `VARCHAR(50)` for all enum columns
- No `TIMESTAMP WITH TIME ZONE` — use `TIMESTAMP`
- No `BIGSERIAL` / `SERIAL` — use `INTEGER PRIMARY KEY` (SQLite auto-increment) or rely on JPA `IDENTITY` strategy
- No PostgreSQL triggers or stored procedures
- No `GENERATED ALWAYS AS ... STORED` computed columns (not supported in SQLite)
- Inline foreign key references only (SQLite does not support `ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY`)

- [ ] **Step 1: Create V1__initial_schema.sql**

```sql
-- =============================================================================
-- V1__initial_schema.sql
-- World Cup 2026 Prediction Game — Initial Database Schema
-- ANSI SQL — compatible with SQLite and PostgreSQL
-- =============================================================================

-- -----------------------------------------------------------------------------
-- USERS
-- Enum columns use VARCHAR(50): UserStatus (PENDING/ACTIVE/DISABLED),
-- UserRole (PARTICIPANT/ADMIN)
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id                   INTEGER PRIMARY KEY,
    email                VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    display_name         VARCHAR(200),
    avatar_url           VARCHAR(1000),
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    role                 VARCHAR(50)  NOT NULL DEFAULT 'PARTICIPANT',
    total_points         INTEGER      NOT NULL DEFAULT 0,
    exact_score_count    INTEGER      NOT NULL DEFAULT 0,
    correct_winner_count INTEGER      NOT NULL DEFAULT 0,
    correct_draw_count   INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at          TIMESTAMP,
    approved_by_id       INTEGER REFERENCES users(id)
);

CREATE UNIQUE INDEX users_email_idx ON users(email);
CREATE INDEX users_status_idx ON users(status);
CREATE INDEX users_role_idx ON users(role);
CREATE INDEX users_total_points_idx ON users(total_points);

-- -----------------------------------------------------------------------------
-- OAUTH IDENTITIES
-- One user may have multiple OAuth identities (Google + LinkedIn same email)
-- provider uses VARCHAR(50): OAuthProvider (GOOGLE/LINKEDIN)
-- -----------------------------------------------------------------------------

CREATE TABLE oauth_identities (
    id               INTEGER PRIMARY KEY,
    user_id          INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    avatar_url       VARCHAR(1000),
    linked_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at    TIMESTAMP,
    UNIQUE(provider, provider_subject)
);

CREATE INDEX oauth_identities_user_id_idx ON oauth_identities(user_id);
CREATE INDEX oauth_identities_email_idx ON oauth_identities(email);

-- -----------------------------------------------------------------------------
-- TEAMS
-- -----------------------------------------------------------------------------

CREATE TABLE teams (
    id            INTEGER PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    short_name    VARCHAR(50),
    fifa_code     VARCHAR(3)   NOT NULL,
    flag_code     VARCHAR(10)  NOT NULL,
    confederation VARCHAR(20),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(fifa_code)
);

CREATE INDEX teams_name_idx ON teams(name);

-- -----------------------------------------------------------------------------
-- GROUPS
-- 12 groups (A-L) in the 2026 FIFA World Cup
-- -----------------------------------------------------------------------------

CREATE TABLE groups (
    id         INTEGER PRIMARY KEY,
    name       VARCHAR(2) NOT NULL,
    created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name)
);

-- Group membership (4 teams per group)
CREATE TABLE group_teams (
    group_id INTEGER NOT NULL REFERENCES groups(id),
    team_id  INTEGER NOT NULL REFERENCES teams(id),
    PRIMARY KEY (group_id, team_id)
);

-- Group standings (updated when results are entered)
-- Note: goal_difference is computed in application logic, not as a stored generated column
CREATE TABLE group_standings (
    id              INTEGER PRIMARY KEY,
    group_id        INTEGER NOT NULL REFERENCES groups(id),
    team_id         INTEGER NOT NULL REFERENCES teams(id),
    played          INTEGER NOT NULL DEFAULT 0,
    won             INTEGER NOT NULL DEFAULT 0,
    drawn           INTEGER NOT NULL DEFAULT 0,
    lost            INTEGER NOT NULL DEFAULT 0,
    goals_for       INTEGER NOT NULL DEFAULT 0,
    goals_against   INTEGER NOT NULL DEFAULT 0,
    goal_difference INTEGER NOT NULL DEFAULT 0,
    points          INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, team_id)
);

CREATE INDEX group_standings_group_id_idx ON group_standings(group_id);

-- -----------------------------------------------------------------------------
-- MATCHES
-- All 104 match slots. Knockout slots may have null teams until determined.
-- stage uses VARCHAR(50): MatchStage (GROUP/ROUND_OF_32/ROUND_OF_16/...)
-- status uses VARCHAR(50): MatchStatus (SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED)
-- -----------------------------------------------------------------------------

CREATE TABLE matches (
    id                           INTEGER PRIMARY KEY,
    external_id                  VARCHAR(50),
    stage                        VARCHAR(50)  NOT NULL,
    group_id                     INTEGER REFERENCES groups(id),
    match_number                 INTEGER      NOT NULL,
    round_label                  VARCHAR(50),
    home_team_id                 INTEGER REFERENCES teams(id),
    away_team_id                 INTEGER REFERENCES teams(id),
    home_team_placeholder        VARCHAR(100),
    away_team_placeholder        VARCHAR(100),
    kickoff_time                 TIMESTAMP    NOT NULL,
    venue                        VARCHAR(200),
    city                         VARCHAR(100),
    status                       VARCHAR(50)  NOT NULL DEFAULT 'SCHEDULED',
    home_score                   INTEGER,
    away_score                   INTEGER,
    home_score_90                INTEGER,
    away_score_90                INTEGER,
    prediction_window_open       INTEGER      NOT NULL DEFAULT 0,
    prediction_window_opens_at   TIMESTAMP,
    prediction_window_closes_at  TIMESTAMP,
    result_entered_at            TIMESTAMP,
    result_entered_by_id         INTEGER REFERENCES users(id),
    created_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(match_number)
);

CREATE INDEX matches_stage_idx ON matches(stage);
CREATE INDEX matches_kickoff_time_idx ON matches(kickoff_time);
CREATE INDEX matches_status_idx ON matches(status);
CREATE INDEX matches_group_id_idx ON matches(group_id);
CREATE INDEX matches_home_team_id_idx ON matches(home_team_id);
CREATE INDEX matches_away_team_id_idx ON matches(away_team_id);
CREATE INDEX matches_prediction_window_idx ON matches(prediction_window_open, prediction_window_closes_at);

-- -----------------------------------------------------------------------------
-- PREDICTIONS
-- One prediction per user per match (upsert on re-submit within window)
-- score_result uses VARCHAR(50): PredictionScore (EXACT/CORRECT_DRAW/CORRECT_WINNER/WRONG/PENDING)
-- -----------------------------------------------------------------------------

CREATE TABLE predictions (
    id               INTEGER PRIMARY KEY,
    user_id          INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id         INTEGER     NOT NULL REFERENCES matches(id),
    predicted_home   INTEGER     NOT NULL,
    predicted_away   INTEGER     NOT NULL,
    score_result     VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    points_awarded   INTEGER     NOT NULL DEFAULT 0,
    submitted_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at        TIMESTAMP,
    edited_by_admin  INTEGER     NOT NULL DEFAULT 0,
    admin_edit_note  VARCHAR(500),
    UNIQUE(user_id, match_id)
);

CREATE INDEX predictions_user_id_idx ON predictions(user_id);
CREATE INDEX predictions_match_id_idx ON predictions(match_id);
CREATE INDEX predictions_score_result_idx ON predictions(score_result);

-- -----------------------------------------------------------------------------
-- TOURNAMENT WINNER PREDICTIONS
-- One per user; visible to all immediately after submission
-- -----------------------------------------------------------------------------

CREATE TABLE tournament_winner_predictions (
    id             INTEGER PRIMARY KEY,
    user_id        INTEGER   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id        INTEGER   NOT NULL REFERENCES teams(id),
    points_awarded INTEGER   NOT NULL DEFAULT 0,
    scored         INTEGER   NOT NULL DEFAULT 0,
    submitted_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX twp_user_id_idx ON tournament_winner_predictions(user_id);
CREATE INDEX twp_team_id_idx ON tournament_winner_predictions(team_id);

-- -----------------------------------------------------------------------------
-- AUDIT LOGS
-- Immutable log of all admin actions
-- action uses VARCHAR(50): AuditAction enum values
-- -----------------------------------------------------------------------------

CREATE TABLE audit_logs (
    id          INTEGER PRIMARY KEY,
    actor_id    INTEGER REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   INTEGER,
    old_value   TEXT,
    new_value   TEXT,
    note        VARCHAR(1000),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_logs_actor_id_idx ON audit_logs(actor_id);
CREATE INDEX audit_logs_action_idx ON audit_logs(action);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id);
CREATE INDEX audit_logs_created_at_idx ON audit_logs(created_at);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V1__initial_schema.sql
git commit -m "feat: add Flyway V1 ANSI SQL schema compatible with SQLite and PostgreSQL"
```

---

### Task 4: Enums

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/UserStatus.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/UserRole.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/OAuthProvider.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/MatchStatus.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/MatchStage.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/PredictionScore.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/AuditAction.java`

- [ ] **Step 1: Create UserStatus.java**

```java
package com.worldcup.prediction.domain.enums;

public enum UserStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
```

- [ ] **Step 2: Create UserRole.java**

```java
package com.worldcup.prediction.domain.enums;

public enum UserRole {
    PARTICIPANT,
    ADMIN
}
```

- [ ] **Step 3: Create OAuthProvider.java**

```java
package com.worldcup.prediction.domain.enums;

public enum OAuthProvider {
    GOOGLE,
    LINKEDIN
}
```

- [ ] **Step 4: Create MatchStatus.java**

```java
package com.worldcup.prediction.domain.enums;

public enum MatchStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
```

- [ ] **Step 5: Create MatchStage.java**

```java
package com.worldcup.prediction.domain.enums;

public enum MatchStage {
    GROUP("Group Stage"),
    ROUND_OF_32("Round of 32"),
    ROUND_OF_16("Round of 16"),
    QUARTER_FINAL("Quarter Final"),
    SEMI_FINAL("Semi Final"),
    THIRD_PLACE("Third Place"),
    FINAL("Final");

    private final String displayName;

    MatchStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

- [ ] **Step 6: Create PredictionScore.java**

```java
package com.worldcup.prediction.domain.enums;

public enum PredictionScore {
    EXACT(3),
    CORRECT_DRAW(2),
    CORRECT_WINNER(1),
    WRONG(0),
    PENDING(0);

    private final int points;

    PredictionScore(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
```

- [ ] **Step 7: Create AuditAction.java**

```java
package com.worldcup.prediction.domain.enums;

public enum AuditAction {
    USER_APPROVED,
    USER_REJECTED,
    USER_DISABLED,
    USER_ENABLED,
    MATCH_RESULT_ENTERED,
    MATCH_RESULT_OVERRIDDEN,
    PREDICTION_WINDOW_OPENED,
    PREDICTION_WINDOW_CLOSED,
    PREDICTION_EDITED_BY_ADMIN,
    POINTS_OVERRIDDEN,
    KNOCKOUT_PROGRESSION_UPDATED,
    REMINDER_SENT
}
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/
git commit -m "feat: add domain enums for users, matches, predictions, and audit"
```

---

### Task 5: JPA Domain Entities

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/User.java`
- Create: `src/main/java/com/worldcup/prediction/domain/OAuthIdentity.java`
- Create: `src/main/java/com/worldcup/prediction/domain/Team.java`
- Create: `src/main/java/com/worldcup/prediction/domain/Group.java`
- Create: `src/main/java/com/worldcup/prediction/domain/Match.java`
- Create: `src/main/java/com/worldcup/prediction/domain/Prediction.java`
- Create: `src/main/java/com/worldcup/prediction/domain/TournamentWinnerPrediction.java`
- Create: `src/main/java/com/worldcup/prediction/domain/AuditLog.java`

**Important:** Do NOT use `columnDefinition` referencing PostgreSQL custom types (e.g. `columnDefinition = "user_status"`). The schema uses `VARCHAR(50)` for all enum columns; `@Enumerated(EnumType.STRING)` is sufficient for Hibernate to map correctly on both SQLite and PostgreSQL. Use `LocalDateTime` instead of `OffsetDateTime` for timestamp fields — SQLite stores timestamps as text/integer and the SQLiteDialect maps best to `LocalDateTime`.

- [ ] **Step 1: Create User.java**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"oauthIdentities", "predictions", "tournamentWinnerPrediction"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserStatus status = UserStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role = UserRole.PARTICIPANT;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @Column(name = "exact_score_count", nullable = false)
    private int exactScoreCount = 0;

    @Column(name = "correct_winner_count", nullable = false)
    private int correctWinnerCount = 0;

    @Column(name = "correct_draw_count", nullable = false)
    private int correctDrawCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OAuthIdentity> oauthIdentities = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Prediction> predictions = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TournamentWinnerPrediction tournamentWinnerPrediction;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
```

- [ ] **Step 2: Create OAuthIdentity.java**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_identities",
       uniqueConstraints = @UniqueConstraint(
               name = "oauth_identities_provider_subject_idx",
               columnNames = {"provider", "provider_subject"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OAuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
```

- [ ] **Step 3: Create Team.java**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "fifa_code", nullable = false, length = 3, unique = true)
    private String fifaCode;

    @Column(name = "flag_code", nullable = false, length = 10)
    private String flagCode;

    @Column(name = "confederation", length = 20)
    private String confederation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getFlagUrl() {
        return "https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/" + flagCode + ".svg";
    }

    public String getFlagFallbackUrl() {
        return "https://flagcdn.com/w80/" + flagCode + ".png";
    }
}
```

- [ ] **Step 4: Create Group.java**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "teams")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 2, unique = true)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "group_teams",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private List<Team> teams = new ArrayList<>();
}
```

- [ ] **Step 5: Create Match.java**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "predictions")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "external_id", length = 50)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MatchStage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "match_number", nullable = false, unique = true)
    private int matchNumber;

    @Column(name = "round_label", length = 50)
    private String roundLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "home_team_placeholder", length = 100)
    private String homeTeamPlaceholder;

    @Column(name = "away_team_placeholder", length = 100)
    private String awayTeamPlaceholder;

    @Column(name = "kickoff_time", nullable = false)
    private LocalDateTime kickoffTime;

    @Column(length = 200)
    private String venue;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "home_score_90")
    private Integer homeScore90;

    @Column(name = "away_score_90")
    private Integer awayScore90;

    @Column(name = "prediction_window_open", nullable = false)
    @Builder.Default
    private boolean predictionWindowOpen = false;

    @Column(name = "prediction_window_opens_at")
    private LocalDateTime predictionWindowOpensAt;

    @Column(name = "prediction_window_closes_at")
    private LocalDateTime predictionWindowClosesAt;

    @Column(name = "result_entered_at")
    private LocalDateTime resultEnteredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_entered_by_id")
    private User resultEnteredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Prediction> predictions = new ArrayList<>();

    public boolean isCompleted() {
        return status == MatchStatus.COMPLETED;
    }

    public boolean isGroupStage() {
        return stage == MatchStage.GROUP;
    }

    /**
     * Returns the effective home score for prediction scoring purposes.
     * For knockout matches, uses the 90-minute score (homeScore90) if available.
     * For group matches, homeScore is the final score.
     */
    public Integer getEffectiveHomeScore() {
        if (stage != MatchStage.GROUP && homeScore90 != null) {
            return homeScore90;
        }
        return homeScore;
    }

    /**
     * Returns the effective away score for prediction scoring purposes.
     */
    public Integer getEffectiveAwayScore() {
        if (stage != MatchStage.GROUP && awayScore90 != null) {
            return awayScore90;
        }
        return awayScore;
    }
}
```

- [ ] **Step 6: Create Prediction.java**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.PredictionScore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "predictions",
       uniqueConstraints = @UniqueConstraint(
               name = "predictions_user_match_idx",
               columnNames = {"user_id", "match_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "match"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "predicted_home", nullable = false)
    private int predictedHome;

    @Column(name = "predicted_away", nullable = false)
    private int predictedAway;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_result", nullable = false, length = 50)
    @Builder.Default
    private PredictionScore scoreResult = PredictionScore.PENDING;

    @Column(name = "points_awarded", nullable = false)
    @Builder.Default
    private int pointsAwarded = 0;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "edited_by_admin", nullable = false)
    @Builder.Default
    private boolean editedByAdmin = false;

    @Column(name = "admin_edit_note", length = 500)
    private String adminEditNote;

    public boolean isPredictedDraw() {
        return predictedHome == predictedAway;
    }
}
```

- [ ] **Step 7: Create TournamentWinnerPrediction.java**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_winner_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "team"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TournamentWinnerPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "points_awarded", nullable = false)
    @Builder.Default
    private int pointsAwarded = 0;

    @Column(name = "scored", nullable = false)
    @Builder.Default
    private boolean scored = false;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 8: Create AuditLog.java**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "actor")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 1000)
    private String note;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/
git commit -m "feat: add JPA domain entities with Lombok — no PostgreSQL-specific columnDefinition"
```

---

### Task 6: Spring Data JPA Repositories

**Files:**
- Create: `src/main/java/com/worldcup/prediction/repository/UserRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/OAuthIdentityRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/TeamRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/GroupRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/MatchRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/TournamentWinnerPredictionRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/AuditLogRepository.java`

- [ ] **Step 1: Create UserRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(UserRole role);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    List<User> findByStatusOrderByTotalPointsDescExactScoreCountDescCorrectWinnerCountDesc(UserStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            ORDER BY u.totalPoints DESC,
                     u.exactScoreCount DESC,
                     u.correctWinnerCount DESC,
                     u.createdAt ASC
            """)
    List<User> findLeaderboard();

    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            ORDER BY u.totalPoints DESC,
                     u.exactScoreCount DESC,
                     u.correctWinnerCount DESC,
                     u.createdAt ASC
            LIMIT 10
            """)
    List<User> findTop10Leaderboard();

    long countByStatus(UserStatus status);
}
```

- [ ] **Step 2: Create OAuthIdentityRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.OAuthIdentity;
import com.worldcup.prediction.domain.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    List<OAuthIdentity> findByUserId(Long userId);

    Optional<OAuthIdentity> findByEmailIgnoreCaseAndProvider(String email, OAuthProvider provider);

    boolean existsByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
```

- [ ] **Step 3: Create TeamRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByFifaCodeIgnoreCase(String fifaCode);

    Optional<Team> findByNameIgnoreCase(String name);

    List<Team> findByConfederationIgnoreCase(String confederation);

    boolean existsByFifaCodeIgnoreCase(String fifaCode);
}
```

- [ ] **Step 4: Create GroupRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByNameIgnoreCase(String name);

    @Query("SELECT g FROM Group g JOIN FETCH g.teams ORDER BY g.name")
    List<Group> findAllWithTeams();
}
```

- [ ] **Step 5: Create MatchRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByMatchNumber(int matchNumber);

    Optional<Match> findByExternalId(String externalId);

    List<Match> findByStage(MatchStage stage);

    List<Match> findByStatus(MatchStatus status);

    List<Match> findByStageOrderByKickoffTimeAsc(MatchStage stage);

    List<Match> findByGroupIdOrderByKickoffTimeAsc(Long groupId);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.stage = :stage
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByStageWithTeams(@Param("stage") MatchStage stage);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.predictionWindowOpen = true
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findOpenPredictionWindows();

    @Query("""
            SELECT m FROM Match m
            WHERE m.kickoffTime >= :from
              AND m.kickoffTime < :to
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByKickoffTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT m FROM Match m
            WHERE m.predictionWindowOpen = false
              AND m.predictionWindowOpensAt IS NOT NULL
              AND m.predictionWindowOpensAt <= :now
              AND m.status = 'SCHEDULED'
            """)
    List<Match> findMatchesWhereWindowShouldOpen(@Param("now") LocalDateTime now);

    @Query("""
            SELECT m FROM Match m
            WHERE m.predictionWindowOpen = true
              AND m.predictionWindowClosesAt IS NOT NULL
              AND m.predictionWindowClosesAt <= :now
            """)
    List<Match> findMatchesWhereWindowShouldClose(@Param("now") LocalDateTime now);

    long countByStatus(MatchStatus status);
}
```

- [ ] **Step 6: Create PredictionRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.PredictionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByUserIdAndMatchId(Long userId, Long matchId);

    List<Prediction> findByUserId(Long userId);

    List<Prediction> findByMatchId(Long matchId);

    boolean existsByUserIdAndMatchId(Long userId, Long matchId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN FETCH p.match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE p.user.id = :userId
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdWithMatchDetails(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN FETCH p.user u
            WHERE p.match.id = :matchId
            ORDER BY u.totalPoints DESC
            """)
    List<Prediction> findByMatchIdWithUsers(@Param("matchId") Long matchId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.stage = :stage
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdAndMatchStage(
            @Param("userId") Long userId,
            @Param("stage") MatchStage stage);

    @Query("""
            SELECT p FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.roundLabel = :roundLabel
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdAndRoundLabel(
            @Param("userId") Long userId,
            @Param("roundLabel") String roundLabel);

    @Query("""
            SELECT COUNT(p) FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.predictionWindowOpen = true
              AND p.scoreResult = 'PENDING'
            """)
    long countPendingForOpenWindows(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(p) FROM Prediction p
            WHERE p.match.id = :matchId
              AND p.scoreResult = :scoreResult
            """)
    long countByMatchIdAndScoreResult(
            @Param("matchId") Long matchId,
            @Param("scoreResult") PredictionScore scoreResult);
}
```

- [ ] **Step 7: Create TournamentWinnerPredictionRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentWinnerPredictionRepository extends JpaRepository<TournamentWinnerPrediction, Long> {

    Optional<TournamentWinnerPrediction> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<TournamentWinnerPrediction> findByTeamId(Long teamId);

    @Query("""
            SELECT twp FROM TournamentWinnerPrediction twp
            JOIN FETCH twp.user u
            JOIN FETCH twp.team t
            ORDER BY u.totalPoints DESC
            """)
    List<TournamentWinnerPrediction> findAllWithDetails();

    long countByTeamId(Long teamId);
}
```

- [ ] **Step 8: Create AuditLogRepository.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.AuditLog;
import com.worldcup.prediction.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<AuditLog> findByTargetTypeAndTargetId(String targetType, Long targetId, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/
git commit -m "feat: add Spring Data JPA repositories for all domain entities"
```

---

### Task 7: Context Loads Test

**Files:**
- Create: `src/test/java/com/worldcup/prediction/WorldCupPredictionApplicationTests.java`
- Create: `src/test/resources/application-test.properties`

Tests use the SQLite profile — no Testcontainers or external database server required.

- [ ] **Step 1: Create WorldCupPredictionApplicationTests.java**

```java
package com.worldcup.prediction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorldCupPredictionApplicationTests {

    @Test
    void contextLoads() {
        // Spring context must start up cleanly with Flyway migrations applied against SQLite
    }
}
```

- [ ] **Step 2: Create test application properties**

Create `src/test/resources/application-test.properties`:

```properties
# SQLite in-memory database for tests (no file created, no cleanup needed)
spring.datasource.url=jdbc:sqlite::memory:?cache=shared
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=validate

# Flyway: apply migrations to in-memory SQLite
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.mixed=true

# Override the default profile (sqlite file-based) with test config
spring.profiles.active=test

# Show SQL in tests
spring.jpa.show-sql=true

# Disable security for context load test
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,\
  org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
```

- [ ] **Step 3: Run context loads test**

```bash
./mvnw test -Dtest=WorldCupPredictionApplicationTests -pl . 2>&1 | tail -30
```

Expected output includes:
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: add Spring context loads test using SQLite in-memory (no Testcontainers)"
```

---

### Task 8: Repository Integration Tests

**Files:**
- Create: `src/test/java/com/worldcup/prediction/repository/UserRepositoryTest.java`
- Create: `src/test/java/com/worldcup/prediction/repository/MatchRepositoryTest.java`
- Create: `src/test/java/com/worldcup/prediction/repository/PredictionRepositoryTest.java`

Tests use `@DataJpaTest` with SQLite in-memory — no Testcontainers, no Docker required.

- [ ] **Step 1: Create UserRepositoryTest.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User activeUser;
    private User pendingUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        activeUser = userRepository.save(User.builder()
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .totalPoints(15)
                .exactScoreCount(3)
                .correctWinnerCount(4)
                .build());

        pendingUser = userRepository.save(User.builder()
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .status(UserStatus.PENDING)
                .role(UserRole.PARTICIPANT)
                .totalPoints(0)
                .exactScoreCount(0)
                .correctWinnerCount(0)
                .build());
    }

    @Test
    void findByEmailIgnoreCase_whenEmailExists_returnsUser() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM");
        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Alice");
    }

    @Test
    void findByEmailIgnoreCase_whenEmailNotFound_returnsEmpty() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("unknown@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmailIgnoreCase_whenExists_returnsTrue() {
        assertThat(userRepository.existsByEmailIgnoreCase("alice@example.com")).isTrue();
    }

    @Test
    void findByStatus_returnsOnlyMatchingUsers() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("alice@example.com");

        List<User> pendingUsers = userRepository.findByStatus(UserStatus.PENDING);
        assertThat(pendingUsers).hasSize(1);
        assertThat(pendingUsers.get(0).getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void findLeaderboard_returnsActiveUsersOrderedByPoints() {
        userRepository.save(User.builder()
                .email("charlie@example.com")
                .firstName("Charlie")
                .lastName("Brown")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .totalPoints(25)
                .exactScoreCount(5)
                .correctWinnerCount(6)
                .build());

        List<User> leaderboard = userRepository.findLeaderboard();
        assertThat(leaderboard).hasSize(2); // alice and charlie (bob is PENDING)
        assertThat(leaderboard.get(0).getEmail()).isEqualTo("charlie@example.com");
        assertThat(leaderboard.get(1).getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void countByStatus_returnsCorrectCount() {
        assertThat(userRepository.countByStatus(UserStatus.ACTIVE)).isEqualTo(1);
        assertThat(userRepository.countByStatus(UserStatus.PENDING)).isEqualTo(1);
        assertThat(userRepository.countByStatus(UserStatus.DISABLED)).isEqualTo(0);
    }

    @Test
    void save_persistsAllFields() {
        User admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .firstName("Super")
                .lastName("Admin")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .totalPoints(0)
                .exactScoreCount(0)
                .correctWinnerCount(0)
                .build());

        User found = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(found.isAdmin()).isTrue();
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Create MatchRepositoryTest.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchRepositoryTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Team brazil;
    private Team argentina;
    private Match groupMatch;
    private Match openWindowMatch;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();

        brazil = teamRepository.save(Team.builder()
                .name("Brazil")
                .fifaCode("BRA")
                .flagCode("br")
                .confederation("CONMEBOL")
                .build());

        argentina = teamRepository.save(Team.builder()
                .name("Argentina")
                .fifaCode("ARG")
                .flagCode("ar")
                .confederation("CONMEBOL")
                .build());

        groupMatch = matchRepository.save(Match.builder()
                .matchNumber(1)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .homeTeam(brazil)
                .awayTeam(argentina)
                .kickoffTime(LocalDateTime.now().plusDays(5))
                .venue("Rose Bowl")
                .city("Los Angeles")
                .status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(false)
                .build());

        openWindowMatch = matchRepository.save(Match.builder()
                .matchNumber(2)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusHours(2))
                .venue("MetLife Stadium")
                .city("New York")
                .status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(true)
                .predictionWindowClosesAt(LocalDateTime.now().plusHours(1))
                .build());
    }

    @Test
    void findByMatchNumber_returnsCorrectMatch() {
        Optional<Match> found = matchRepository.findByMatchNumber(1);
        assertThat(found).isPresent();
        assertThat(found.get().getVenue()).isEqualTo("Rose Bowl");
    }

    @Test
    void findByStage_returnsAllMatchesForStage() {
        List<Match> groupMatches = matchRepository.findByStage(MatchStage.GROUP);
        assertThat(groupMatches).hasSize(2);
    }

    @Test
    void findByStatus_returnsCorrectMatches() {
        List<Match> scheduled = matchRepository.findByStatus(MatchStatus.SCHEDULED);
        assertThat(scheduled).hasSize(2);
    }

    @Test
    void findOpenPredictionWindows_returnsOnlyOpenWindows() {
        List<Match> open = matchRepository.findOpenPredictionWindows();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getMatchNumber()).isEqualTo(2);
    }

    @Test
    void findByKickoffTimeBetween_returnsMatchesInRange() {
        LocalDateTime from = LocalDateTime.now().plusHours(1);
        LocalDateTime to = LocalDateTime.now().plusHours(3);
        List<Match> matches = matchRepository.findByKickoffTimeBetween(from, to);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchNumber()).isEqualTo(2);
    }

    @Test
    void getEffectiveScore_forGroupMatch_returnsMainScore() {
        Match completed = matchRepository.save(Match.builder()
                .matchNumber(3)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().minusHours(2))
                .status(MatchStatus.COMPLETED)
                .homeScore(2)
                .awayScore(1)
                .predictionWindowOpen(false)
                .build());

        Match found = matchRepository.findById(completed.getId()).orElseThrow();
        assertThat(found.getEffectiveHomeScore()).isEqualTo(2);
        assertThat(found.getEffectiveAwayScore()).isEqualTo(1);
    }

    @Test
    void getEffectiveScore_forKnockoutMatch_returns90MinuteScore() {
        Match knockout = matchRepository.save(Match.builder()
                .matchNumber(50)
                .stage(MatchStage.QUARTER_FINAL)
                .roundLabel("Quarter Final")
                .kickoffTime(LocalDateTime.now().minusHours(3))
                .status(MatchStatus.COMPLETED)
                .homeScore(2)
                .awayScore(1)
                .homeScore90(1)
                .awayScore90(1)
                .predictionWindowOpen(false)
                .build());

        Match found = matchRepository.findById(knockout.getId()).orElseThrow();
        assertThat(found.getEffectiveHomeScore()).isEqualTo(1);
        assertThat(found.getEffectiveAwayScore()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Create PredictionRepositoryTest.java**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PredictionRepositoryTest {

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRepository matchRepository;

    private User alice;
    private User bob;
    private Match match1;
    private Match match2;

    @BeforeEach
    void setUp() {
        predictionRepository.deleteAll();
        matchRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(User.builder()
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .build());

        bob = userRepository.save(User.builder()
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .status(UserStatus.ACTIVE)
                .role(UserRole.PARTICIPANT)
                .build());

        match1 = matchRepository.save(Match.builder()
                .matchNumber(1)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(1))
                .status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(true)
                .predictionWindowClosesAt(LocalDateTime.now().plusHours(23))
                .build());

        match2 = matchRepository.save(Match.builder()
                .matchNumber(2)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(2))
                .status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(false)
                .build());
    }

    @Test
    void findByUserIdAndMatchId_whenExists_returnsPrediction() {
        Prediction saved = predictionRepository.save(Prediction.builder()
                .user(alice)
                .match(match1)
                .predictedHome(2)
                .predictedAway(1)
                .build());

        Optional<Prediction> found = predictionRepository.findByUserIdAndMatchId(alice.getId(), match1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPredictedHome()).isEqualTo(2);
        assertThat(found.get().getPredictedAway()).isEqualTo(1);
    }

    @Test
    void existsByUserIdAndMatchId_whenExists_returnsTrue() {
        predictionRepository.save(Prediction.builder()
                .user(alice)
                .match(match1)
                .predictedHome(1)
                .predictedAway(0)
                .build());

        assertThat(predictionRepository.existsByUserIdAndMatchId(alice.getId(), match1.getId())).isTrue();
        assertThat(predictionRepository.existsByUserIdAndMatchId(bob.getId(), match1.getId())).isFalse();
    }

    @Test
    void findByUserId_returnsAllPredictionsForUser() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).predictedHome(2).predictedAway(0).build());
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match2).predictedHome(1).predictedAway(1).build());
        predictionRepository.save(Prediction.builder()
                .user(bob).match(match1).predictedHome(0).predictedAway(0).build());

        List<Prediction> alicePredictions = predictionRepository.findByUserId(alice.getId());
        assertThat(alicePredictions).hasSize(2);

        List<Prediction> bobPredictions = predictionRepository.findByUserId(bob.getId());
        assertThat(bobPredictions).hasSize(1);
    }

    @Test
    void countByMatchIdAndScoreResult_returnsCorrectCount() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).predictedHome(2).predictedAway(1)
                .scoreResult(PredictionScore.EXACT).pointsAwarded(3).build());
        predictionRepository.save(Prediction.builder()
                .user(bob).match(match1).predictedHome(1).predictedAway(1)
                .scoreResult(PredictionScore.WRONG).pointsAwarded(0).build());

        long exactCount = predictionRepository.countByMatchIdAndScoreResult(match1.getId(), PredictionScore.EXACT);
        assertThat(exactCount).isEqualTo(1);

        long wrongCount = predictionRepository.countByMatchIdAndScoreResult(match1.getId(), PredictionScore.WRONG);
        assertThat(wrongCount).isEqualTo(1);
    }

    @Test
    void prediction_uniqueConstraint_preventsDoubleSubmit() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).predictedHome(2).predictedAway(1).build());

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> predictionRepository.saveAndFlush(Prediction.builder()
                        .user(alice).match(match1).predictedHome(1).predictedAway(0).build())
        );
    }

    @Test
    void isPredictedDraw_whenScoresEqual_returnsTrue() {
        Prediction drawPrediction = Prediction.builder()
                .user(alice).match(match1).predictedHome(1).predictedAway(1).build();
        assertThat(drawPrediction.isPredictedDraw()).isTrue();

        Prediction nonDrawPrediction = Prediction.builder()
                .user(alice).match(match1).predictedHome(2).predictedAway(1).build();
        assertThat(nonDrawPrediction.isPredictedDraw()).isFalse();
    }
}
```

- [ ] **Step 4: Run all repository integration tests**

```bash
./mvnw test -Dtest="UserRepositoryTest,MatchRepositoryTest,PredictionRepositoryTest" -pl . 2>&1 | tail -30
```

Expected output:
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- UserRepositoryTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- MatchRepositoryTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- PredictionRepositoryTest
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/worldcup/prediction/repository/
git commit -m "test: add SQLite-backed integration tests for User, Match, and Prediction repositories"
```

---

### Task 9: Docker Setup

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`

- [ ] **Step 1: Create Dockerfile**

```dockerfile
# =============================================================================
# Multi-stage build: Stage 1 — Build
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from builder
COPY --from=builder /app/target/world-cup-prediction-0.0.1-SNAPSHOT.jar app.jar

# Actuator health port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
version: '3.9'

services:

  db:
    image: postgres:16-alpine
    container_name: worldcup-db
    environment:
      POSTGRES_DB: worldcup
      POSTGRES_USER: ${DB_USERNAME:-worldcup}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-worldcup}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-worldcup} -d worldcup"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    restart: unless-stopped

  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: worldcup-app
    environment:
      # Use postgres profile when running in Docker
      APP_PROFILE: postgres
      DATABASE_URL: jdbc:postgresql://db:5432/worldcup
      DATABASE_USERNAME: ${DB_USERNAME:-worldcup}
      DATABASE_PASSWORD: ${DB_PASSWORD:-worldcup}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-placeholder}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-placeholder}
      LINKEDIN_CLIENT_ID: ${LINKEDIN_CLIENT_ID:-placeholder}
      LINKEDIN_CLIENT_SECRET: ${LINKEDIN_CLIENT_SECRET:-placeholder}
      FOOTBALL_API_KEY: ${FOOTBALL_API_KEY:-}
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped

volumes:
  postgres-data:
    driver: local
```

- [ ] **Step 3: Verify Docker build compiles**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction
docker build --target builder -t worldcup-build-test . 2>&1 | tail -20
```

Expected: `Successfully built` or `exporting to image` with no errors.

- [ ] **Step 4: Commit**

```bash
git add Dockerfile docker-compose.yml
git commit -m "feat: add multi-stage Dockerfile and docker-compose (postgres profile for Docker)"
```

---

### Task 10: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

```bash
./mvnw test 2>&1 | tail -40
```

Expected:
```
[INFO] Tests run: N, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 2: Verify Maven build produces JAR**

```bash
./mvnw package -DskipTests -q && ls -lh target/*.jar
```

Expected: `world-cup-prediction-0.0.1-SNAPSHOT.jar` present, size > 40MB.

- [ ] **Step 3: Final commit with tag**

```bash
git add -A
git commit -m "feat: Part 1 complete — foundation data layer, schema, entities, repositories, Docker"
git tag v0.1.0-part1
```

---

## Summary

Part 1 delivers a fully functional data layer with zero application logic. The Spring context starts clean, Flyway applies the complete schema against SQLite in-memory (tests) or a file-based SQLite DB (default local run), all JPA entities map correctly to database tables, and all repositories pass integration tests.

**Database support summary:**
- `sqlite` profile (default): zero-config, file-based `worldcup.db` — just run `./mvnw spring-boot:run`
- `postgres` profile: set `APP_PROFILE=postgres` + `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`
- Docker Compose uses `APP_PROFILE=postgres` automatically
- Tests use SQLite in-memory — no Testcontainers, no Docker needed for `./mvnw test`

**What is explicitly NOT in Part 1:**
- No Spring Security configuration (OAuth2 wiring is in Part 2)
- No controllers or Thymeleaf templates
- No service layer or business logic
- No scoring engine
- No external API client
- No data seed SQL (teams/groups/fixtures seed will be a separate Flyway migration in Part 3)

**Ready for Part 2 (Auth):** The `User` and `OAuthIdentity` entities are fully defined with all fields needed for `OAuth2UserService` implementation.
