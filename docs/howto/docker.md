# How to use the Alf.io Docker Image

Alf.io provides a Docker image that can be used to run the application in a containerized environment. This guide will walk you through the process of setting up and running Alf.io using Docker.

## Prerequisites

- Docker installed on your machine.
- A PostgreSQL database (Alf.io requires a database to store its data).

## Pulling the Image

You can pull the latest image from the GitHub Container Registry:

```bash
docker pull ghcr.io/alfio-event/alf.io:latest
```

## Running with Docker Run

To run Alf.io using `docker run`, you need to provide the necessary environment variables for the database connection:

```bash
docker run -d \
  -p 8080:8080 \
  -e DATASOURCE_URL=jdbc:postgresql://db:5432/alfio \
  -e DATASOURCE_USERNAME=postgres \
  -e DATASOURCE_PASSWORD=password \
  ghcr.io/alfio-event/alf.io:latest
```

## Running with Docker Compose

Using Docker Compose is the recommended way to run Alf.io, as it allows you to manage the application and its database together.

Create a `docker-compose.yml` file with the following content:

```yaml
services:
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: alfio
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    volumes:
      - alfio-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  alfio:
    image: ghcr.io/alfio-event/alf.io:latest
    ports:
      - "8080:8080"
    environment:
      - DATASOURCE_URL=jdbc:postgresql://db:5432/alfio
      - DATASOURCE_USERNAME=postgres
      - DATASOURCE_PASSWORD=password
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      db:
        condition: service_healthy

volumes:
  alfio-db-data:
```

Run the following command to start the application:

```bash
docker-compose up -d
```

## Configuration (Environment Variables)

The following environment variables can be used to configure Alf.io:

| Variable | Description | Default |
| --- | --- | --- |
| `DATASOURCE_URL` | The JDBC URL for the PostgreSQL database. | `jdbc:postgresql://localhost:5432/alfio` |
| `DATASOURCE_USERNAME` | The username for the database. | `postgres` |
| `DATASOURCE_PASSWORD` | The password for the database. | `password` |
| `SPRING_PROFILES_ACTIVE` | The active Spring profiles (e.g., `prod`, `dev`). | `dev` |
| `ALFIO_JAVA_OPTS` | Additional JVM options. | |
| `ALFIO_PERFORMANCE_OPTS` | Performance-related JVM options. | `-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true` |

## Persistence

Alf.io is mostly stateless; however, all persistent data is stored in the PostgreSQL database. Ensure that you have a volume mapped for the database data to avoid data loss.
