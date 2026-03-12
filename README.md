# Socket Chat Server

Multi-server synchronized chat system using Spring Boot, Java Sockets, and Redis.

## Infrastructure Setup

### Redis (Docker)

To start the Redis server for distributed caching and Pub/Sub:

```bash
docker-compose up -d
```

To stop the Redis server:

```bash
docker-compose down
```

## Running Multiple Instances

To test global synchronization, you can run multiple instances of the server on different ports:

**Instance 1 (Port 8080):**
```bash
./gradlew bootRun
```

**Instance 2 (Port 8081):**
```bash
./gradlew bootRun --args='--server.port=8081'
```
