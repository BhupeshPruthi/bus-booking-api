# Bus Booking API

Node.js / Express REST API for bus bookings, poojas, and events. Deploy to a VPS or run with Docker.

## Requirements

- **Node.js** 20+ ([`package.json` engines](package.json))
- **PostgreSQL** 14+

The **MyBus** Android app is a separate repository; point it at this API’s base URL.

## Quick start (local)

```bash
cp .env.example .env
# Edit .env — set JWT_SECRET, database credentials or DATABASE_URL, GOOGLE_CLIENT_ID if using Google sign-in

npm ci
npm run migrate
npm run dev
```

- Health: `GET http://localhost:8080/health`
- API base: `http://localhost:8080/api`

## Environment

See [`.env.example`](.env.example). Important variables:

| Variable | Notes |
|----------|--------|
| `NODE_ENV` | Use `production` on the server |
| `JWT_SECRET` | **Required** in production |
| `DATABASE_URL` | Preferred on VPS; overrides discrete `DB_*` if set |
| `TRUST_PROXY` | Set to `1` when behind nginx (see [src/app.js](src/app.js)) |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins (not `*` for strict production if needed) |
| `GOOGLE_CLIENT_ID` | Web client ID for Android ID token verification |

## Database migrations

```bash
# Local / same shell as .env
npm run migrate

# Production (explicit env)
NODE_ENV=production npx knex migrate:latest --env production
```

## Production on a VPS (outline)

1. Install Node 20+, PostgreSQL, nginx (optional but recommended), Certbot for TLS.
2. Clone this repo, `npm ci`, copy `.env` with production values.
3. Run migrations, then start the process (`npm run start:prod` or systemd / PM2 — see [`deploy/`](deploy/)).
4. Put nginx in front (see [`deploy/nginx.conf.example`](deploy/nginx.conf.example)), set `TRUST_PROXY=1`.
5. Persist `uploads/` across deploys (symlink or dedicated volume).
6. Schedule PostgreSQL backups (`pg_dump`).

Example files:

- [`deploy/nginx.conf.example`](deploy/nginx.conf.example) — reverse proxy
- [`deploy/bus-booking-api.service`](deploy/bus-booking-api.service) — systemd unit
- [`ecosystem.config.cjs`](ecosystem.config.cjs) — PM2

## Docker

```bash
cp .env.example .env
# Set JWT_SECRET, POSTGRES_PASSWORD, and DATABASE_URL=postgresql://postgres:PASSWORD@db:5432/bus_booking

docker compose up -d --build
docker compose run --rm api npx knex migrate:latest --env production
```

## API route list

```bash
npm run api:list
```

Manifest: [`src/docs/api-routes.json`](src/docs/api-routes.json).

## CI

GitHub Actions runs `npm ci`, `node --check` on push/PR to `main` or `master` (see [`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

## License

MIT
