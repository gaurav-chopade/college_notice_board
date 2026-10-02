# College Communication Portal

A full-stack college notice board that replaces a physical board with a secure, targeted digital system. Visitors can read admission and public notices without an account. College members sign in to a private portal where every notice reaches exactly the people it is meant for.

Built with **Spring Boot 3**, **React + Vite**, and **MySQL**.

---

## Live demo accounts

Start the backend and frontend (instructions below), then sign in with any of these. Password for all: `Password@123`

| Email                    | Role                                    |
| ------------------------ | --------------------------------------- |
| admin@college.edu        | Super admin                             |
| principal@college.edu    | Principal                               |
| hod.cs@college.edu       | HOD, Computer Engineering (Engineering) |
| hod.dco@college.edu      | HOD, Computer Engineering (Polytechnic) |
| faculty.cs@college.edu   | Teacher, Computer Engineering           |
| student.cs6a@college.edu | Student, CS semester 6 class A          |

---

## Features

### Public website (no account needed)

- Landing page with the latest notices pinned on a real notice board
- Engineering and Polytechnic wing pages with departments and programmes
- Admissions page, About and Help
- Search and filter public notices by wing, category, priority and date
- Attachment download via short-lived signed Cloudinary links

### Accounts and approval

- Self-registration for students, teachers and HODs
- The system picks the right approver automatically:
  - Student → class teacher → HOD → Principal
  - Teacher → department HOD → Principal
  - HOD → Principal
- Engineering and Polytechnic approvals are kept separate; an Engineering HOD cannot approve Polytechnic staff
- Rejected applicants see the reason and can reapply
- Sign-in is rate-limited; a pending or blocked account sees its status only after the correct password

### Notices

- **Visibility:** Private (targeted to specific people) or Public (visible on the public website)
- **Targeting:** whole college, role, department, semester, class, or named individuals
- **Lifecycle:** draft → pending approval → scheduled → published → expired → archived
- Teachers' notices go to their HOD for approval before release
- Live recipient count while building the audience
- Approval timeline on each notice
- Preview before publishing
- Draft auto-saved in the browser

### For recipients

- Personalised feed with read/unread markers
- Urgent notice banner on the dashboard
- Acknowledgement system with statistics for the author
- Save notices to a personal reading list
- In-app notifications grouped by day, with email for important notices

### For staff

- Drag-and-drop file attachments with per-file progress
- AI assistant that drafts a notice from a short description
- HOD approval queue
- Notice statistics: views, reads, acknowledgements
- Account requests queue for approvers
- Class teacher assignments

### Admin and audit

- User management with account status (active, suspended, rejected)
- Department management for both wings
- Notice category management
- Full audit log of all significant actions

### UI

- Light and dark theme, remembered per device
- Mobile-responsive with a bottom navigation bar on phones
- Skeleton loading, empty states with next steps
- Accessible forms with inline validation

---

## Technology

| Layer        | Stack                                                                        |
| ------------ | ---------------------------------------------------------------------------- |
| Backend      | Java 17+, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, Hibernate |
| Database     | MySQL 8                                                                      |
| File storage | Cloudinary                                                                   |
| Email        | Gmail SMTP (optional)                                                        |
| Frontend     | React 18, Vite, Bootstrap 5                                                  |
| Tests        | Spring Boot integration tests (MockMvc, H2 in-memory)                        |

---

## Getting started

### Prerequisites

- Java 17 or later
- Node.js 18 or later
- MySQL 8 (or Docker)

### 1. Start MySQL

With Docker:

```bash
docker compose up -d
```

Or use any running MySQL instance and update the credentials in step 3.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

The first start creates all tables and loads demo data automatically. Watch for `Tomcat started on port 8080`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

---

## Configuration

The backend reads settings from environment variables. Everything has a sensible default for local development; only `JWT_SECRET` should be changed before showing the project to anyone.

Set variables in your IDE (IntelliJ: Run → Edit Configurations → Environment variables) or in the terminal:

```bash
# Windows
setx JWT_SECRET "your-64-character-random-string-here"

# macOS / Linux
export JWT_SECRET="your-64-character-random-string-here"
```

| Variable                                                                 | What it does                                                         | Required                                       |
| ------------------------------------------------------------------------ | -------------------------------------------------------------------- | ---------------------------------------------- |
| `JWT_SECRET`                                                             | Signs login tokens. Must be 64+ characters.                          | Change before demo                             |
| `DB_URL`                                                                 | MySQL JDBC URL                                                       | Default: `localhost:3306/college_notice_board` |
| `DB_USERNAME` / `DB_PASSWORD`                                            | MySQL credentials                                                    | Default: `gaurav` / `gaurav`                   |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | File attachments (sign up free at cloudinary.com)                    | Optional                                       |
| `MAIL_ENABLED=true` + `MAIL_USERNAME` + `MAIL_PASSWORD`                  | Email for approvals and important notices (use a Gmail App Password) | Optional                                       |
| `AI_API_KEY`                                                             | Anthropic API key for the AI notice assistant                        | Optional                                       |
| `COLLEGE_NAME`                                                           | Your college's name on the public site                               | Optional                                       |
| `COLLEGE_ADDRESS` / `COLLEGE_PHONE` / `COLLEGE_EMAIL`                    | Shown in the footer and on notice pages                              | Optional                                       |
| `SEED_DATA=false`                                                        | Disable demo data (set this in production)                           | —                                              |
| `AUTO_ACTIVATE_REGISTRATIONS=true`                                       | Skip HOD approval for students (development only)                    | —                                              |

See `.env.example` for the full list.

---

## Project structure

```
college-notice-board/
├── backend/                  Spring Boot API
│   └── src/
│       ├── main/java/…
│       │   ├── config/       Security, JWT, seeder
│       │   ├── controller/   REST endpoints
│       │   ├── dto/          Request and response records
│       │   ├── entity/       JPA entities
│       │   ├── repository/   Spring Data repositories
│       │   └── service/      Business logic
│       └── test/java/…       Integration tests
│
└── frontend/                 React + Vite
    └── src/
        ├── components/       Shared UI components
        ├── context/          Auth and theme context
        ├── hooks/            useAsync, usePublicData
        ├── pages/
        │   ├── admin/        Admin-only pages
        │   └── public/       Landing, notices, admissions…
        ├── services/         Axios API client
        └── utils/            Format helpers
```

---

## Running the tests

```bash
cd backend
./mvnw test           # macOS / Linux
mvnw.cmd test         # Windows
```

The test suite starts the full application against an H2 in-memory database and covers:

- Notice visibility and approval workflow
- Public API: only public, published, live notices are returned
- Engineering / Polytechnic approval separation
- Account registration, approval routing and rate limiting
- Saved notices, notifications, recipient counts and attachments

---

## API overview

All endpoints except `/api/public/**` and `/api/auth/**` require a `Bearer` token.

| Group            | Endpoints                                                                                                                     |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Auth             | `POST /register`, `/login`, `/logout`, `/forgot-password`, `/reset-password`                                                  |
| Public           | `GET /api/public/college`, `/institutions`, `/categories`, `/notices`, `/notices/{id}`, `/notices/{id}/attachments/{id}/link` |
| Notices          | `GET/POST /api/notices`, `GET/PUT /api/notices/{id}`, publish, submit, approve, reject, archive, acknowledge, read, save      |
| Account requests | `GET /api/registrations/pending`, `POST /{id}/approve`, `POST /{id}/reject`                                                   |
| Class teachers   | `GET/PUT/DELETE /api/class-teachers`                                                                                          |
| Users            | `GET/POST/PUT /api/users` (admin)                                                                                             |
| Departments      | `GET/POST/PUT/DELETE /api/departments` (admin)                                                                                |
| Categories       | `GET/POST/PUT/DELETE /api/categories` (admin)                                                                                 |
| Notifications    | `GET/PUT /api/notifications`                                                                                                  |
| Attachments      | `POST /api/notices/{id}/attachments`, `GET /api/attachments/{id}/download`                                                    |
| Audit logs       | `GET /api/audit-logs` (admin)                                                                                                 |
| Dashboard        | `GET /api/dashboard/{role}`                                                                                                   |

Full interactive documentation at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) when the backend is running.

---

## Screenshots

| Public landing page               | Notice board                      |
| --------------------------------- | --------------------------------- |
| ![Landing page](docs/landing.png) | ![Notice board](docs/notices.png) |

| Dashboard                        | Create notice                     |
| -------------------------------- | --------------------------------- |
| ![Dashboard](docs/dashboard.png) | ![Create notice](docs/create.png) |

_(Add screenshots to a `docs/` folder in the repo.)_

---

## Acknowledgements

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Cloudinary](https://cloudinary.com)
- [Bootstrap](https://getbootstrap.com)
- [Source Serif 4](https://fonts.google.com/specimen/Source+Serif+4) and [Libre Franklin](https://fonts.google.com/specimen/Libre+Franklin)
