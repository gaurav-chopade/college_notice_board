# Getting Started

### Run locally

1. Create the MySQL user once (as root):
   ```sql
   CREATE USER IF NOT EXISTS 'gaurav'@'localhost' IDENTIFIED BY 'gaurav';
   GRANT ALL PRIVILEGES ON college_notice_board.* TO 'gaurav'@'localhost';
   FLUSH PRIVILEGES;
   ```
2. Start the app:
   ```bash
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```
   The database `college_notice_board` and all tables are created automatically
   (`spring.jpa.hibernate.ddl-auto=update`). `data.sql` is not executed.
3. Open http://localhost:8080/swagger-ui.html

### Tests
```bash
./mvnw test        # uses in-memory H2, no MySQL needed
```

### Production
```bash
./mvnw package -DskipTests
java -jar target/notice-board-1.0.0.jar --spring.profiles.active=prod
```
Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`
(and optionally `CLOUDINARY_*`, `MAIL_*`, `AI_API_KEY`).

### Reference
* [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/3.3.5/maven-plugin/reference/html/)
* [Spring Data JPA](https://docs.spring.io/spring-boot/docs/3.3.5/reference/htmlsingle/index.html#data.sql.jpa-and-spring-data)
* [Spring Security](https://docs.spring.io/spring-boot/docs/3.3.5/reference/htmlsingle/index.html#web.security)
