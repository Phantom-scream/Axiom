# Deployment

Build the non-root multi-stage image:

```bash
docker build -t axiom:local .
```

For local PostgreSQL only, run `docker compose up -d postgres`. For the production-like application
profile, copy `.env.production.example` to a protected environment file, replace its secret values,
then run:

```bash
cp .env.production.example .env.production
chmod 600 .env.production
# Replace placeholder values before starting; .env.production is ignored by Git.
docker compose --env-file .env.production --profile application up -d --build
```

The application waits for PostgreSQL health, Flyway applies migrations, and the image health check
targets `/actuator/health/readiness`. Database data uses the `axiom-postgres-data` volume. Credentials
are supplied at runtime and are neither copied into the image nor committed.
