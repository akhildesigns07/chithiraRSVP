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

The tests use an in-memory H2 database in PostgreSQL mode, migrated by the same Flyway scripts, so they need no Docker.

## Configuration

| Env var | Required | Default | Purpose |
|---|---|---|---|
| `ADMIN_USERNAME` | yes | – | Staff login username |
| `ADMIN_PASSWORD` | yes | – | Staff login password |
| `APP_BASE_URL` | in production | `http://localhost:8080` | Public URL used to build invite links in WhatsApp messages and the host link |
| `DB_URL` | in production | `jdbc:postgresql://localhost:5432/rsvp` | JDBC URL |
| `DB_USERNAME` | in production | `rsvp` | Database user |
| `DB_PASSWORD` | in production | `rsvp` | Database password |
| `PORT` | no | `8080` | HTTP port |
| `RSVP_RATE_LIMIT_PER_MINUTE` | no | `10` | RSVP submissions allowed per client IP per minute (sliding window, per app instance) |
| `SHOP_NAME` | no | `Chithira Printer` | Shop name on the homepage, portal and invitations |
| `SHOP_ADDRESS` | no | `TD Complex, Chamakkada, Kollam` | Address, also used for the "Get directions" link |
| `SHOP_PHONE` | no | `9995360201` | Phone number for "Call" links |
| `SHOP_WHATSAPP` | no | `9995360201` | Number for "Chat on WhatsApp" links |
| `SHOP_EMAIL` | no | `chithiraprinterskollam@gmail.com` | Contact email |

## Deploy

The app is a single container plus a PostgreSQL database. Any host that runs Docker works, for example a VPS, Render, Railway, Fly.io or Cloud Run.

1. **Create a PostgreSQL database** (version 14 or newer) and note its host, database name, user and password.
2. **Build the image:**
   ```bash
   docker build -t chithira-rsvp .
   ```
   Push it to your registry if your host pulls images from one:
   ```bash
   docker tag chithira-rsvp registry.example.com/chithira-rsvp:1
   docker push registry.example.com/chithira-rsvp:1
   ```
3. **Run it with the environment variables:**
   ```bash
   docker run -d --name rsvp -p 8080:8080 --restart unless-stopped \
     -e ADMIN_USERNAME=admin \
     -e ADMIN_PASSWORD='a-long-random-password' \
     -e APP_BASE_URL=https://invites.example.com \
     -e DB_URL=jdbc:postgresql://db-host:5432/rsvp \
     -e DB_USERNAME=rsvp \
     -e DB_PASSWORD='db-password' \
     chithira-rsvp
   ```
   Flyway applies migrations automatically on startup.
4. **Put HTTPS in front of it.** Allow request bodies of at least 15 MB at the proxy so letter uploads aren't rejected (nginx: `client_max_body_size 15m;`). Use a reverse proxy (Caddy, nginx, or the platform's load balancer) that terminates TLS and forwards to port 8080. The app only honours `X-Forwarded-For`/`-Proto` from proxies on private or loopback addresses, which is Tomcat's default `internal-proxies` list. A client can't fake its IP to get around the RSVP rate limit. If your load balancer connects from a public address, add it with `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regex). Otherwise every guest will appear to come from the load balancer and share one RSVP budget. Set `APP_BASE_URL` to the public `https://` address, because that is the address guests receive on WhatsApp.
   A minimal Caddyfile:
   ```
   invites.example.com {
       reverse_proxy localhost:8080
   }
   ```
5. **Check it:** open `https://invites.example.com/portal`, log in, create a test event, add yourself as a guest and send the invite to your own phone.

**Backups:** all data, including photos and letters, is in PostgreSQL. Back up the database, for example with a nightly `pg_dump`.

**Upgrades:** build and run the new image. Pending migrations run on startup.
