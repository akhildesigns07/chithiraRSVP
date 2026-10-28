# Chithira RSVP

The website and invitation portal for Chithira Printer, Chamakkada, Kollam.

The homepage at `/` presents the shop and its services. Under it, at `/portal`, staff create events and paste guest lists. Each guest gets a personal animated card at `/i/{token}`, where they can RSVP and open the full printed invitation letter. The host follows replies on `/h/{hostToken}` and sends the invites one by one through WhatsApp.

Stack: Spring Boot 3.5, Java 21, Thymeleaf, htmx, PostgreSQL, Flyway.

## How it works

| Who | URL | Access |
|---|---|---|
| Public | `/` | Shop homepage, linking to the staff portal |
| Staff | `/portal` (login at `/portal/login`) | Form login. A single admin account comes from env vars |
| Guest | `/i/{token}` | 128-bit random token per guest |
| Host | `/h/{hostToken}` | 128-bit random token per event |

1. Staff log in, create an event and paste guests as `Name, phone` lines. Indian (`98765 43210`, `+91…`, `0…`) and UAE (`050 123 4567`, `+971…`, `5…`) mobile numbers are normalised. If any line is invalid, nothing is added and the bad lines are listed.
   Staff can also upload the **invitation letter**: one PDF, or up to 20 page images (10 MB in total). Guests then see a "View full invitation letter" button on their card.
2. Staff copy the **Host dashboard** link from the event page and give it to the customer.
3. On the host dashboard, **Send invites on WhatsApp** opens the Send screen. Each **Send** button opens `https://wa.me/{phone}?text=…` in a new tab with the personal message and invite link, marks that guest as sent, and moves the highlight to the next unsent guest.
4. Guests open their link, see the animated card, and reply Attending (with headcount) or Not attending.

**Send screen.** The message composer has an English and a Malayalam version. Both can be edited, and both support the `{name}` and `{link}` placeholders. Every Send uses the message currently on screen and saves it. A message without `{link}` is refused. The screen shows "N of M sent" and has Not sent / Sent / RSVP'd filters. After each send, an Undo button can mark the guest as not sent again. The bottom bar always offers the next guest, so you can send to the whole list one-handed.

**Link previews.** Invite pages carry Open Graph tags. The image `/og/{slug}.jpg` is a 1200×630 JPEG, drawn per event with Java2D: photo, names and date, with no guest data. It is cached in the database and redrawn after the event changes. The slug has a random suffix so event images can't be enumerated. JPEG and PNG photos appear in the image; WebP photos are left out, because Java's image reader can't decode them.

**Guests** can be added (paste) or removed on the staff event page at any time. Removing a guest disables their link.

Phone numbers never appear on guest or host pages, or in the CSV export. The Send button posts to the server, which redirects to `wa.me`, so the number stays out of the page HTML.

## Run locally

Requirements: Java 21, Maven, Docker.

```bash
docker compose up -d            # PostgreSQL on localhost:5432 (db/user/password: rsvp)

export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=change-me
mvn spring-boot:run
```

Open http://localhost:8080 for the homepage, and http://localhost:8080/portal to log in. Flyway creates the schema on startup.

## Tests

```bash
mvn test
```

The tests use an in-memory H2 database in PostgreSQL mode, migrated by the same Flyway scripts, so they need no Docker. `ProdProfileServerTest` starts the `prod` profile on a real embedded server and checks it the way Cloudflare reaches it: headers, cookies, caching, actuator exposure and rate limiting.

## Configuration

Settings come from environment variables. In the `prod` profile, which the Docker image uses, the first six have **no defaults**. If any is missing or blank, the app refuses to start and names the missing ones:

```
Missing required settings; set these environment variables: ADMIN_PASSWORD (app.admin.password)
```

| Env var | Required | Default (local dev only) | Purpose |
|---|---|---|---|
| `DB_URL` | yes | `jdbc:postgresql://localhost:5432/rsvp` | JDBC URL, e.g. `jdbc:postgresql://db-host:5432/rsvp?sslmode=require` |
| `DB_USERNAME` | yes | `rsvp` | Database user |
| `DB_PASSWORD` | yes | `rsvp` | Database password |
| `APP_BASE_URL` | yes | `http://localhost:8080` | Public `https://` address. It is used in invite links sent on WhatsApp, the host link and share images. |
| `ADMIN_USERNAME` | yes | – | Staff login username |
| `ADMIN_PASSWORD` | yes | – | Staff login password. Use a long random value. |
| `SPRING_PROFILES_ACTIVE` | no | – (image: `prod`) | `prod` enables everything under "What the prod profile does" |
| `PORT` | no | `8080` | HTTP port inside the container |
| `DB_POOL_SIZE` | no | `10` | Database connection pool size |
| `CLIENT_IP_HEADER` | no | prod: `CF-Connecting-IP` | Header carrying the real client IP, for the RSVP rate limit |
| `RSVP_RATE_LIMIT_PER_MINUTE` | no | `10` | RSVP submissions per client IP per minute, per app instance |
| `SHOP_NAME`, `SHOP_ADDRESS`, `SHOP_PHONE`, `SHOP_WHATSAPP`, `SHOP_EMAIL` | no | Chithira Printer's details | Shown on the homepage, portal and invitations |

## Production deployment (behind Cloudflare)

### What the prod profile does

- **Secrets:** all secrets and the database come from the environment, and startup fails if any are missing.
- **Migrations:** Flyway migrates the database on startup. `clean` is disabled.
- **Behind Cloudflare:** `server.forward-headers-strategy=framework`, so Cloudflare's `X-Forwarded-Proto`/`-Host` tell the app that requests arrived over HTTPS.
- **Session cookie:** `__Host-rsvp-session`, with `Secure`, `HttpOnly`, `SameSite=Lax`, and an 8-hour lifetime.
- **Headers:** HSTS (`max-age=31536000`, sent on HTTPS requests). A Content-Security-Policy allows only same-origin scripts (no inline or eval'd script), Google Fonts, and the form hand-off to `wa.me`/`api.whatsapp.com`. Also `X-Frame-Options: SAMEORIGIN` and `Referrer-Policy: no-referrer`.
- **Caching:**

  | Paths | Cache-Control |
  |---|---|
  | `/i/**` pages and files, `/h/**`, `/portal/**` | `no-store` |
  | `/og/**` | `max-age=604800, public` (7 days; the page links it with `?v=<version>`, so edits get a new URL) |
  | `/css/**`, `/js/**`, `/webjars/**` | `max-age=604800, public` (7 days; CSS and JS URLs carry a content hash, e.g. `/css/app-5a19….css`, so deploys take effect immediately) |
- **Actuator:** only `/actuator/health` is exposed, without authentication, returning just `{"status":"UP"}` (it includes a database check). Everything else under `/actuator` is unavailable.
- **Logs:** JSON, one object per line on stdout, with ECS-style fields (`@timestamp`, `log.level`, `log.logger`, `message`, `error.*`). Phone numbers, URL tokens and token hashes are redacted from messages and stack traces, and SQL and bind-parameter logging is off.
- **Rate limit:** the RSVP rate limit keys on `CF-Connecting-IP`, which Cloudflare overwrites on every request. `X-Forwarded-For` is not used for this, because its first entry is whatever the client sent.

> **Important: only Cloudflare may reach the app.** The app trusts Cloudflare's forwarded headers (`X-Forwarded-*`, `CF-Connecting-IP`). Anyone who can reach the origin directly could fake them. Use a Cloudflare Tunnel (recommended, below), or firewall the origin port to [Cloudflare's IP ranges](https://www.cloudflare.com/ips/).

### Steps

1. **Create the database** (PostgreSQL 14 or newer, ideally a managed service with automatic backups):
   ```sql
   CREATE USER rsvp_app WITH PASSWORD 'a-long-random-db-password';
   CREATE DATABASE rsvp OWNER rsvp_app;
   ```
   The app creates and migrates its own tables on first start.

2. **Build and push the image** (multi-stage build on `eclipse-temurin:21-jre`, runs as non-root UID 10001):
   ```bash
   docker build -t registry.example.com/chithira-rsvp:2026-10-28 .
   docker push registry.example.com/chithira-rsvp:2026-10-28
   ```

3. **Write the environment file** on the server, readable by root only:
   ```bash
   sudo install -m 600 /dev/null /etc/chithira-rsvp.env
   sudoedit /etc/chithira-rsvp.env
   ```
   ```
   DB_URL=jdbc:postgresql://db-host:5432/rsvp?sslmode=require
   DB_USERNAME=rsvp_app
   DB_PASSWORD=a-long-random-db-password
   APP_BASE_URL=https://invites.example.com
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=a-long-random-admin-password
   ```

4. **Run the container**, bound to localhost only so it isn't reachable from outside:
   ```bash
   docker run -d --name rsvp --restart unless-stopped \
     --env-file /etc/chithira-rsvp.env \
     -p 127.0.0.1:8080:8080 \
     registry.example.com/chithira-rsvp:2026-10-28
   docker logs -f rsvp        # wait for "Started RsvpApplication"
   curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
   ```

5. **Connect Cloudflare with a Tunnel**, so the origin has no open inbound port:
   ```bash
   cloudflared tunnel login
   cloudflared tunnel create chithira-rsvp
   cloudflared tunnel route dns chithira-rsvp invites.example.com
   ```
   `/etc/cloudflared/config.yml`:
   ```yaml
   tunnel: chithira-rsvp
   credentials-file: /root/.cloudflared/<tunnel-id>.json
   ingress:
     - hostname: invites.example.com
       service: http://127.0.0.1:8080
     - service: http_status:404
   ```
   ```bash
   sudo cloudflared service install && sudo systemctl enable --now cloudflared
   ```
   In the Cloudflare dashboard:
   - Set **SSL/TLS → Edge Certificates → Always Use HTTPS** to on.
   - Leave caching at the defaults, which respect the app's `Cache-Control`. Do **not** add a "Cache Everything" rule, because pages under `/i/` and `/h/` are personal.

6. **Verify:**
   ```bash
   curl -s https://invites.example.com/actuator/health                       # {"status":"UP"}
   curl -sI https://invites.example.com/ | grep -iE 'strict-transport|content-security'
   curl -s  https://invites.example.com/actuator/env -o /dev/null -w '%{http_code}\n'   # not 200
   ```
   Then log in at `https://invites.example.com/portal`, create a test event, add yourself as a guest, and send the invite to your own phone. The WhatsApp link preview should show the share image.

Point your platform's health check at `GET /actuator/health`.

**Upgrades:** take a backup (below), then push the new image and replace the container:
```bash
docker pull registry.example.com/chithira-rsvp:NEW-TAG
docker stop rsvp && docker rm rsvp
docker run -d --name rsvp --restart unless-stopped --env-file /etc/chithira-rsvp.env \
  -p 127.0.0.1:8080:8080 registry.example.com/chithira-rsvp:NEW-TAG
```
Pending migrations run on startup. If a migration fails, the app doesn't start and the old data is untouched. Start the previous image again and restore if needed.

**Logs:** `docker logs rsvp` shows JSON lines like:
```json
{"@timestamp":"2026-10-28T08:43:29.812Z","log.level":"INFO","log.logger":"org.flywaydb.core.internal.command.DbMigrate","process.thread.name":"main","service.name":"chithira-rsvp","message":"Successfully applied 5 migrations to schema \"public\", now at version v5"}
```

## Backups and restore

All data, including photos, letters and cached share images, is in PostgreSQL. Use `pg_dump`/`pg_restore` at the same or a newer major version than the server.

### Back up

Run nightly from cron and keep copies off the server:
```bash
export PGPASSWORD='a-long-random-db-password'
pg_dump --host=db-host --username=rsvp_app --dbname=rsvp \
        --format=custom --no-owner --file="rsvp-$(date +%F).dump"
```

### Restore

1. **Stop the app** so nothing writes during the restore:
   ```bash
   docker stop rsvp
   ```
2. **Restore into a new, empty database:**
   ```bash
   export PGPASSWORD='a-long-random-db-password'
   createdb   --host=db-host --username=rsvp_app rsvp_restored
   pg_restore --host=db-host --username=rsvp_app --dbname=rsvp_restored \
              --no-owner --exit-on-error rsvp-2026-10-28.dump
   ```
   If `createdb` is not permitted for `rsvp_app`, run it as an admin with `--owner=rsvp_app`.
3. **Check what came back:**
   ```bash
   psql --host=db-host --username=rsvp_app --dbname=rsvp_restored -c \
     "select (select count(*) from event) events, (select count(*) from guest) guests,
             (select max(version) from flyway_schema_history where success) schema_version;"
   ```
4. **Switch to the restored database.** Either set `DB_URL=jdbc:postgresql://db-host:5432/rsvp_restored?sslmode=require` in `/etc/chithira-rsvp.env`, or swap the names (as an admin, with the app stopped):
   ```sql
   ALTER DATABASE rsvp RENAME TO rsvp_before_restore;
   ALTER DATABASE rsvp_restored RENAME TO rsvp;
   ```
5. **Start the app and check it:**
   ```bash
   docker start rsvp     # or docker rm rsvp + docker run … if you changed the env file
   curl -s https://invites.example.com/actuator/health
   ```
   Guest and host links from the backup keep working, because their tokens are restored with the data. Changes made after the backup was taken are lost.

A backup from an older app version is fine: the app migrates it forward on startup. Don't restore a backup made by a *newer* version into an older image. Flyway refuses to start because the schema is ahead of the code; deploy the matching or a newer image instead.

This restore procedure (dump, restore into a fresh database, compare counts, start the app on it, open an existing invite link) was run end to end against PostgreSQL 16.
