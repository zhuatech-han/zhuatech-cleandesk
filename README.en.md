[中文](README.md) | [English](README.en.md)

# ZhiHua CleanDesk · Cleaning Bookings, Recurring Visits and Worker Settlement

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

CleanDesk connects customers, office staff and cleaning workers in one company: sites and service rates, recurring requests, quote confirmation, crew dispatch, protected work photos, rework, acceptance, external receipt/refund records and worker settlement. Built with Java 21, Spring Boot, Vue 3 and MySQL 8; Chinese and English interfaces are included.

**Source-available, non-commercial learning edition.** Personal learning and exchange only. Commercial use requires authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Public source does not grant free commercial rights. See [LICENSE](LICENSE).

## Who it is for

- Domestic cleaning companies scheduling weekly or fortnightly visits, with individual rescheduling or skips.
- Small office cleaning teams quoting per visit, area or crew hour, with monthly customer statements.
- Multi-worker crews with weekly availability, time off, travel buffers and a designated visit lead.
- Quality follow-up through before/after evidence, customer acceptance and rework without charging twice.

This is an internal system for one company with organization-based scopes, not a public marketplace. Office staff provision and link customer and worker accounts.

## Implemented features

| Interface | Available workflow |
| --- | --- |
| Customer | Own sites; one-off, weekly or fortnightly requests, up to 12 occurrences; quotes and confirmation; advance rescheduling/cancellation/skips; photos, acceptance/rework and monthly CSV statements |
| Worker | Own assignments, protected uploads, lead-only start/completion, declared minutes per worker, own settlement review and confirmation; responsive browser interface |
| Office | Customers/sites, category dictionary, rates/extras, workers/availability/time off, quotes and offline confirmation references, crew replacement, receipt/refund and worker payment records, monthly reports and audit |
| Administration | Accounts, password resets, enable/disable, roles/scopes, organizations, menus, dictionaries and parameters; self-service password change invalidates old sessions |

Quotes freeze the service, address, price, extras, duration and crew size. Each visit retains its identity when rescheduled. Server-side command idempotency prevents duplicate writes; uncertain submissions can be checked and explicitly resent with the same key.

## Actual running screenshots

Screenshots show this running application using clearly marked fictional QA records. Color patterns are test images, not customer work photographs.

| Screen | What it shows |
| --- | --- |
| ![Sign in](docs/screenshots/login.jpg) | Session sign-in without a fixed public password |
| ![Bookings](docs/screenshots/bookings.jpg) | Orders, occurrence counts and quote state |
| ![Request form](docs/screenshots/booking-form.jpg) | Customer/site/service selection and frequency |
| ![Visit details](docs/screenshots/visit-detail.jpg) | Work rounds, evidence and acceptance history |
| ![Accounts](docs/screenshots/accounts.jpg) | Account roles and enabled state |
| ![Monthly report](docs/screenshots/report.jpg) | Confirmed charges, recorded net receipts, due and refund due |
| ![Customer portal](docs/screenshots/customer.jpg) | Own bookings and monthly statement entry |
| ![Worker mobile view](docs/screenshots/worker-mobile.jpg) | Own jobs and settlements on a phone-sized browser |

## Architecture and directory layout

Same-origin browser → Nginx → Spring Boot → MySQL. Uploaded images are validated and re-encoded as PNG; metadata and binary content share a database transaction. Images have no public URLs. Flyway versions the schema; Hibernate validates it.

```text
backend/              Java business logic, authorization, migrations and tests
frontend/             Vue interfaces and original brand assets
scripts/              Initialization, readiness, HTTP QA, backup and restore
compose.yaml          Database, backend and frontend
compose.test.yaml     Separate MySQL test environment
docs/                 Business, architecture, API, operations, security and screenshots
```

See [architecture](docs/ARCHITECTURE.md), [API](docs/API.md) and [third-party notices](docs/THIRD_PARTY.md).

## Requirements and startup

- Initial builds require access to Maven, npm and official Docker registries. Business operation does not depend on a payment, notification or map provider.
- Docker Engine and Compose v2. Default web binding: `127.0.0.1:8174`.
- Source development: **Java 21**, **Maven 3.9+**, **Node.js 24.19.0+**, npm and **MySQL 8.4**.
- Pinned dependencies: **Spring Boot 4.0.7**, **Vue 3.5.43**, **Vite 8.1.5**. Reserve approximately 4 GB memory for an isolated local installation and sufficient image/database disk space; no tested throughput guarantee is provided.

```bash
python3 scripts/init-env.py
docker compose -p cleandesk-local config --quiet
docker compose -p cleandesk-local up --build -d --wait --wait-timeout 3600
python3 scripts/readiness.py --base http://127.0.0.1:8174
```

Open <http://127.0.0.1:8174>. The initialization script creates a new private `.env` with mode 0600 and refuses to overwrite an existing file. Read its `ADMIN_USERNAME` and `ADMIN_PASSWORD` locally. Never publish this file or passwords in screenshots. `.env.example` contains configuration names, not usable credentials.

### Database initialization

With a new `mysql-data` volume, MySQL creates the application database and Flyway applies `V1__identity.sql` and `V2__cleaning.sql`. Bootstrap creates the initial organization, roles, permissions, menus, categories and one administrator. **No customers, workers, bookings or payments are seeded.** Manual SQL initialization is unnecessary. Append a new migration for schema changes; do not edit applied migrations.

Changing `.env` does not reset credentials in existing volumes or overwrite an existing administrator password. Administrators reset other accounts in account management; users change their own password from the header. At least one complete administrator must remain.

### Configuration

| Variable | Purpose |
| --- | --- |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Initial administrator only; password 12–72 bytes with uppercase, lowercase and a digit |
| `DATABASE_PASSWORD` / `MYSQL_ROOT_PASSWORD` | Application and database-root credentials; keep private |
| `BIND_ADDRESS` / `WEB_PORT` | Web binding; defaults 127.0.0.1 and 8174 |
| `COOKIE_SECURE` | false for local HTTP, true for HTTPS deployment |
| `TEST_DATABASE_PASSWORD` / `TEST_ROOT_PASSWORD` | Credentials for a separate disposable test database |

A directly launched backend also needs `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Compose supplies its private database connection without publishing a MySQL port. Organization time zones use IANA names, initially `Asia/Shanghai`; time zones are locked after workers or business records exist. Currency is locked after the first booking. System settings include customer cancellation notice hours (default 24), worker travel buffer minutes (default 30) and one installation currency (default CNY).

### Source development

Prepare a separate MySQL database and private environment variables, then start the backend:

```bash
cd backend
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

The development proxy defaults to `127.0.0.1:8080`; override with `CLEANDESK_API_TARGET=http://127.0.0.1:8080` if needed. Vite binds to localhost.

## First-use workflow

1. Configure organization, roles, categories and parameters; create dedicated customer and worker accounts.
2. Create a customer, optionally link a customer account, and add service sites. Office-recorded offline confirmations require an external reference.
3. Maintain rates/extras, worker settlement rates, account links, weekly availability and time off.
4. The customer requests service; staff quote; the customer confirms; dispatch assigns the required crew.
5. The lead uploads before photos, starts work, uploads after photos, records each worker's actual minutes and completes the round.
6. The customer accepts or requests rework. Finance records completed external receipts/refunds, creates worker statements and records payment after the worker confirms.

See [operations](docs/OPERATIONS.md) for state rules and recovery instructions.

## Tests and validation

```bash
cd backend
mvn spotless:check verify
```

```bash
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

```bash
docker compose -p cleandesk-local config --quiet
python3 -m compileall -q scripts
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/release-check.py
```

Backend tests cover exact money, daylight-saving transitions, recurrence identity, snapshots, idempotency, simultaneous crew allocation, revoked sessions, photo access, refunds, rework and settlement. H2 is the default; use a dedicated MySQL environment for database-specific tests:

```bash
docker volume create cleandesk-maven-cache
docker compose --env-file .env -p cleandesk-tests -f compose.test.yaml up -d --wait mysql
docker compose --env-file .env -p cleandesk-tests -f compose.test.yaml run --rm tests
docker compose --env-file .env -p cleandesk-tests -f compose.test.yaml down -v
```

The real-HTTP scenario creates fictional accounts and records in an **empty dedicated QA installation**. Never run it against customer data. At late-night hours it changes only the verified empty QA organization to UTC to avoid a midnight-crossing slot; one-time QA credentials are saved in a private 0600 file:

```bash
python3 scripts/quality.py --base http://127.0.0.1:8174 --isolated-test-database
python3 scripts/verify-persistence.py --base http://127.0.0.1:8174 --state output/qa-state.json
```

## Deployment, backup and restore

Terminate HTTPS with a reverse proxy and set `COOKIE_SECURE=true`. Expose only required web access, not the database or private photos/backups. Rehearse fresh installation, restart and restore in isolation before business use. Image builds execute tests, including Maven tests.

```bash
python3 scripts/backup.py --project cleandesk-local --output /private/backup/cleandesk.zip
python3 scripts/restore-env.py --source .env --output output/restore.env --port 8176
python3 scripts/restore.py /private/backup/cleandesk.zip --project cleandesk-restore --env-file output/restore.env
```

Backup pauses only the selected backend and restarts it afterward. Photo BLOBs are included in SQL. Archives contain addresses, photos and password hashes and must remain confidential. Restore accepts only a trusted archive into a nonexistent target project/volume on a distinct localhost port. Checksums detect corruption, not authenticity. See [deployment](docs/DEPLOYMENT.md) and [security](docs/SECURITY.md).

## Known limitations

- No payment gateway, card debit, tax invoices, SMS/email/WhatsApp notifications, route optimization, GPS or native app. Receipts, refunds and worker payments are manual records of **completed external transactions**.
- Requested slots do not reserve workers. Quotes must be confirmed and crews assigned. Recurrence is bounded to 12 visits, not unlimited automatic renewal. Crew duration is 30–720 minutes and scheduled work must stay within one local date.
- Minutes are lead-declared; photos do not certify location or capture time. No offline writes, public registration, OTP or forgotten-password email flow.
- Work already started must proceed through completion/rework and acceptance. Mid-job termination refunds, disputes and partial-service allowances are not implemented. An administrator must re-enable a disabled account to finish its outstanding job.
- A service with completed rework cannot be cancelled as unperformed. Customers must satisfy advance notice; staff exceptions require reasons, and offline confirmations/acceptance require references.
- One currency per installation. Monthly statements include confirmed orders, group by visit date and show current ledger values; they are not bank reconciliation, cash-period accounts or a general ledger.
- Business writes use one database lock; list reads are capped at 10,000 records. Photo limits: 8 MB each, 12 per round, 256 MB per organization. Intended for learning and small-scale evaluation; large-scale, multi-instance and throughput limits have not been validated.
- No claim of real-customer adoption, payment evidence, production certification or completed business security assessment. Commercial deployment requires authorization and project-specific capacity, privacy, security and deployment review.

## License and third-party notices

Own source is for personal learning and exchange only; commercial use requires authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. This is not an MIT/Apache free-commercial-use release. See [LICENSE](LICENSE). Third-party dependencies retain their own copyrights and licenses; their permissions do not relicense this application's original business code.

## Contact ZhiHua Technology

For commercial licensing, custom development, private deployment and system integration:

- Website: <https://www.zhuatech.cn/>
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
