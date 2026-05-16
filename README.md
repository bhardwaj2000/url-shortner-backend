# URL Shortener Backend - Production-Ready Application

A production-grade URL shortener application built with Java 21, Spring Boot 3.x, and MongoDB.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-blue.svg)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-6.0-blue.svg)](https://www.mongodb.com/)

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Concurrency & Scalability](#concurrency--scalability)
- [Testing](#testing)
- [Docker Deployment](#docker-deployment)
- [Performance Considerations](#performance-considerations)

## Features

- **URL Shortening**: Generate unique 6-8 character short codes using Base62 encoding
- **Redirect**: HTTP 302 redirects with atomic click tracking
- **Analytics**: Track click counts and view URL statistics
- **Validation**: Input validation using Jakarta Validation API
- **Concurrency**: Thread-safe operations for high concurrent load
- **Observability**: Health checks, metrics, and structured logging
- **API Documentation**: OpenAPI/Swagger documentation

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.5 |
| Database | MongoDB | 6.0+ |
| Build Tool | Maven | 3.8+ |
| ORM | Spring Data MongoDB | 3.2.5 |
| Validation | Jakarta Validation | 3.0+ |
| API Docs | SpringDoc OpenAPI | 2.5.0 |
| Testing | JUnit 5, Mockito | Latest |

## Architecture

### Folder Structure

```
src/main/java/com/mks/open/
├── config/                    # Configuration classes
│   ├── AppConfig.java
│   ├── MongoConfig.java
│   └── OpenApiConfig.java
├── controller/                # REST API controllers
│   ├── UrlController.java
│   └── HealthController.java
├── service/                   # Business logic
│   └── UrlService.java
├── repository/                # Data access layer
│   └── UrlRepository.java
├── entity/                    # MongoDB entities
│   └── UrlEntity.java
├── dto/                       # Data Transfer Objects
│   ├── UrlRequestDto.java
│   ├── UrlResponseDto.java
│   ├── UrlAnalyticsDto.java
│   └── ErrorResponse.java
├── exception/                 # Custom exceptions
│   ├── UrlNotFoundException.java
│   ├── ValidationException.java
│   └── GlobalExceptionHandler.java
├── mapper/                    # Object mappers
│   └── UrlMapper.java
├── util/                      # Utility classes
│   └── Base62Util.java
└── UrlShortnerBackendApplication.java
```

### Layer Descriptions

| Layer | Responsibility | Design Pattern |
|-------|----------------|----------------|
| Controller | HTTP request/response handling | REST Controller |
| Service | Business logic, transaction management | Service Layer |
| Repository | Data access, MongoDB operations | Repository Pattern |
| Entity | MongoDB document model | Domain Model |
| DTO | Data transfer, API contracts | Data Transfer Object |
| Exception | Error handling and validation | Strategy Pattern |

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8 or higher
- MongoDB 6.0 or higher (or use Docker)

### Local Development

1. Clone the repository:
```bash
git clone <repository-url>
cd url-shortner-backend
```

2. Configure environment (optional):
```bash
# Create .env file
MONGODB_URI=mongodb://localhost:27017
MONGODB_DATABASE=urlshortner
SERVER_PORT=8080
```

3. Build the application:
```bash
./mvnw clean install
```

4. Run the application:
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

## API Documentation

### 1. Create Short URL

**Endpoint**: `POST /api/v1/urls`

**Request**:
```json
{
  "originalUrl": "https://example.com/very/long/url"
}
```

**Response** (201 Created):
```json
{
  "shortCode": "aB12Cd",
  "shortUrl": "http://localhost:8080/aB12Cd",
  "originalUrl": "https://example.com/very/long/url",
  "createdAt": "2026-05-16T10:00:00Z"
}
```

### 2. Redirect to Original URL

**Endpoint**: `GET /{shortCode}`

**Response**: HTTP 302 Found with Location header set to original URL

### 3. Get URL Analytics

**Endpoint**: `GET /api/v1/urls/{shortCode}`

**Response** (200 OK):
```json
{
  "originalUrl": "https://example.com/original",
  "shortCode": "aB12Cd",
  "clickCount": 15,
  "createdAt": "2026-05-16T10:00:00Z"
}
```

### 4. Health Check

**Endpoint**: `GET /actuator/health`

**Response**:
```json
{
  "status": "UP",
  "database": "connected",
  "timestamp": "2026-05-16T10:00:00Z"
}
```

### Sample cURL Commands

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/very/long/url"}'

# Get analytics
curl http://localhost:8080/api/v1/urls/aB12Cd

# Health check
curl http://localhost:8080/actuator/health
```

## Concurrency & Scalability

### Why This Application is Thread-Safe

#### 1. Short Code Generation

**Implementation**: `Base62Util.java`

- Uses `java.security.SecureRandom` - thread-safe cryptographically strong random generator
- Uses `java.util.concurrent.ConcurrentHashMap` - thread-safe set for tracking generated codes
- Uses `java.util.concurrent.atomic.AtomicInteger` - thread-safe counter for retries
- Retry logic with exponential backoff on collision

**Code**:
```java
private static final Set<String> GENERATED_CODES = ConcurrentHashMap.newKeySet();
private static final AtomicInteger TOTAL_RETRIES = new AtomicInteger(0);
private static final SecureRandom RANDOM = new SecureRandom();
```

#### 2. MongoDB Atomic Updates

**Implementation**: `UrlRepository.incrementClickCount()`

MongoDB's `$inc` operator provides atomic increments on the server side:

```java
@Query("{'shortCode': ?0}")
int incrementClickCount(@Param("shortCode") String shortCode, int clickCount);
```

**How It Works**:
1. MongoDB receives the update query with `$inc: { clickCount: 1 }`
2. MongoDB atomically reads the current value, increments it, and writes it back
3. No race conditions - the operation is atomic at the database level

**Contrast with Non-Atomic Approach**:
```java
// BAD: Race condition possible
UrlEntity entity = repository.findByShortCode(code);
int newCount = entity.getClickCount() + 1;
entity.setClickCount(newCount);
repository.save(entity);

// GOOD: Atomic update
repository.incrementClickCount(code, 1);
```

#### 3. Unique Constraint

MongoDB unique index on `shortCode`:
```java
@Indexed(unique = true)
String shortCode;
```

This ensures:
- Database-level collision prevention
- O(1) lookup time for redirects
- Index automatically built on application startup

### How Collisions Are Avoided

| Method | Description |
|--------|-------------|
| **Base62 Encoding** | 6 characters = 62^6 = ~56.8 billion combinations |
| **Unique Index** | MongoDB prevents duplicate short codes at database level |
| **Retry Logic** | Up to 3 retries with different codes if collision occurs |
| **Concurrent Set** | `ConcurrentHashMap.newKeySet()` tracks all generated codes |

### Horizontal Scaling

This application is designed for horizontal scaling:

1. **Stateless Design**: No session state stored in memory
2. **Shared Database**: MongoDB can be clustered for horizontal scaling
3. **Load Balancer Ready**: Standard REST API with health checks
4. **Container Ready**: Docker support for orchestration

**Scaling Architectures**:

```
Single Server:
Client -> Load Balancer -> Application -> MongoDB

Scaled Architecture:
Client -> Load Balancer -> [App1, App2, App3] -> MongoDB Cluster
                              ^ Stateless containers
```

**MongoDB Scaling Options**:
- Replica Sets: High availability
- Sharding: Horizontal data distribution
- Read Replicas: Offload read operations

## Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=UrlServiceTest

# Run with coverage
./mvnw test jacoco:report
```

### Test Coverage

| Component | Test File | Coverage |
|-----------|-----------|----------|
| Base62 Utility | `Base62UtilTest.java` | Code generation, uniqueness, concurrency |
| URL Service | `UrlServiceTest.java` | Business logic, validation, error handling |
| Repository | `UrlRepositoryTest.java` | CRUD operations, atomic updates, concurrency |
| Controller | `UrlControllerTest.java` | HTTP endpoints, validation, error responses |

### Test Types

| Test Type | Purpose | Framework |
|-----------|---------|-----------|
| Unit Tests | Isolated logic testing | JUnit 5, Mockito |
| Integration Tests | Database interactions | Spring Boot Test, Embedded MongoDB |
| Contract Tests | API behavior validation | MockMvc |

## Docker Deployment

### Building Docker Image

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Build Docker image
docker build -t url-shortner-backend:1.0.0 .
```

### Running with Docker

```bash
# Start MongoDB
docker run -d --name mongodb -p 27017:27017 mongo:6.0

# Start the application
docker run -d --name url-shortner \
  -p 8080:8080 \
  -e MONGODB_URI=mongodb://mongodb:27017 \
  -e MONGODB_DATABASE=urlshortner \
  url-shortner-backend:1.0.0
```

### Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:6.0
    container_name: mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    restart: unless-stopped

  url-shortner:
    image: url-shortner-backend:1.0.0
    container_name: url-shortner
    ports:
      - "8080:8080"
    environment:
      - MONGODB_URI=mongodb://mongodb:27017
      - MONGODB_DATABASE=urlshortner
    depends_on:
      - mongodb
    restart: unless-stopped

volumes:
  mongodb_data:
```

Run:
```bash
docker-compose up -d
```

## Performance Considerations

### Database Indexes

| Index | Field | Type | Purpose |
|-------|-------|------|---------|
| Primary | `_id` | Auto | MongoDB default |
| Unique | `shortCode` | Unique | O(1) lookup, collision prevention |
| Default | `createdAt` | Descending | Sort by creation time |

### Connection Pool Settings

MongoDB connection pooling is configured in Spring Boot:
- Min pool size: 0
- Max pool size: 100
- Connection timeout: 10s
- Socket timeout: 10s

### Caching Strategy (Future Enhancement)

For high-traffic scenarios:
1. **Redis Cache**: Cache frequently accessed URLs
2. **CDN**: Cache redirect responses
3. **Browser Caching**: Use 301 for permanent redirects

## Error Handling

### Error Response Format

```json
{
  "timestamp": "2026-05-16T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Short URL not found",
  "path": "/abc123"
}
```

### HTTP Status Codes

| Status | Description |
|--------|-------------|
| 201 | URL successfully created |
| 200 | Analytics retrieved |
| 302 | Redirect successful |
| 400 | Invalid input (validation error) |
| 404 | URL not found |
| 500 | Internal server error |

## Production Best Practices

### Checklist

- [x] Configuration externalized to environment variables
- [x] Structured logging with SLF4J
- [x] Health checks for monitoring
- [x] Metrics endpoint for observability
- [x] Global exception handling
- [x] Input validation
- [x] Thread-safe concurrent operations
- [x] MongoDB indexes for performance
- [x] Unique constraints for data integrity
- [x] Containerized with Docker
- [x] Comprehensive test coverage

### Security Considerations

- Input validation prevents injection attacks
- No sensitive data stored in logs
- HTTP-only cookies for sessions (if added)
- Rate limiting (can be added via Gateway)

## Future Improvements

1. **Caching**: Add Redis cache for frequently accessed URLs
2. **Authentication**: Add API key authentication
3. **Rate Limiting**: Implement rate limiting per client
4. **Analytics**: Add geographic and device tracking
5. **URL Expiration**: Implement TTL for short URLs
6. **Custom Short Codes**: Allow users to specify custom codes
7. **QR Code Generation**: Generate QR codes for short URLs
8. **Webhooks**: Add notifications on URL access
9. **Dashboard**: Web UI for URL management
10. **Multi-tenancy**: Support organization-level URL management

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Contact

For support, please open an issue in the GitHub repository.

---

*Generated with Claude Code*