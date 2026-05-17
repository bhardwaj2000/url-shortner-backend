# URL Shortener Backend - High-Level Design (HLD) & Low-Level Design (LLD)

**Project**: URL Shortener Backend  
**Version**: 1.0.0  
**Technology Stack**: Java 21, Spring Boot 3.2.5, MongoDB 6.0+  
**Date**: May 17, 2026

---

## Table of Contents

1. [High-Level Design (HLD)](#high-level-design-hld)
   - [System Overview](#system-overview)
   - [Architecture Pattern](#architecture-pattern)
   - [Components Overview](#components-overview)
   - [Data Flow](#data-flow)
   - [Technology Stack](#technology-stack)
   - [Deployment Architecture](#deployment-architecture)

2. [Low-Level Design (LLD)](#low-level-design-lld)
   - [Module Descriptions](#module-descriptions)
   - [Class Diagrams](#class-diagrams)
   - [Database Schema](#database-schema)
   - [API Contracts](#api-contracts)
   - [Thread Safety & Concurrency](#thread-safety--concurrency)
   - [Error Handling Strategy](#error-handling-strategy)

3. [Design Patterns Used](#design-patterns-used)
4. [Non-Functional Requirements](#non-functional-requirements)
5. [Scalability & Performance](#scalability--performance)

---

## HIGH-LEVEL DESIGN (HLD)

### System Overview

The URL Shortener Backend is a production-ready REST API service that converts long URLs into compact short codes and provides redirect functionality with analytics tracking. The system is designed to handle high concurrent load with thread-safe operations and atomic database updates.

**Core Capabilities**:
- Generate unique 6-character short codes from long URLs
- Redirect users from short codes to original URLs
- Track click counts with atomic increments
- Provide analytics for shortened URLs
- Support horizontal scaling with stateless design

---

### Architecture Pattern

The project follows a **Layered (N-Tier) Architecture** pattern with clear separation of concerns:

```
┌────────────────────────────────────────────────┐
│           Presentation Layer                    │
│  (REST Controllers, Request/Response Handling)  │
├────────────────────────────────────────────────┤
│           Business Logic Layer                  │
│  (Service Classes, Domain Logic, Validation)   │
├────────────────────────────────────────────────┤
│           Data Access Layer                     │
│  (Repositories, MongoDB Operations)             │
├────────────────────────────────────────────────┤
│           Database Layer                        │
│  (MongoDB - Persistent Data Storage)            │
└────────────────────────────────────────────────┘
```

**Key Characteristics**:
- **Loose Coupling**: Each layer depends only on the layer below
- **High Cohesion**: Related functionality grouped together
- **Testability**: Layers can be tested independently
- **Maintainability**: Easy to locate and modify functionality

---

### Components Overview

#### 1. **Controller Layer** (`controller/`)
- **Purpose**: Handle HTTP requests and responses
- **Components**:
  - `UrlController.java`: Manages URL shortening, redirection, and analytics endpoints
  - `HealthController.java`: Provides service health status

#### 2. **Service Layer** (`service/`)
- **Purpose**: Implement business logic and orchestrate operations
- **Components**:
  - `UrlService.java`: Core logic for URL creation, redirection, and analytics
  - Responsibilities:
    - URL validation
    - Short code generation coordination
    - Idempotency handling
    - Click count tracking coordination

#### 3. **Repository Layer** (`repository/`)
- **Purpose**: Data access and MongoDB operations
- **Components**:
  - `UrlRepository.java`: CRUD operations and atomic updates
  - Features:
    - Find by short code
    - Find by original URL (idempotency)
    - Atomic click count increment

#### 4. **Entity Layer** (`entity/`)
- **Purpose**: MongoDB document representation
- **Components**:
  - `UrlEntity.java`: MongoDB record with indexing strategy

#### 5. **DTO Layer** (`dto/`)
- **Purpose**: Data transfer between layers
- **Components**:
  - `UrlRequestDto.java`: Input validation for shortening requests
  - `UrlResponseDto.java`: Response format for shortened URLs
  - `UrlAnalyticsDto.java`: Analytics response format
  - `ErrorResponse.java`: Standardized error format

#### 6. **Utility Layer** (`util/`)
- **Purpose**: Reusable utility functions
- **Components**:
  - `Base62Util.java`: Short code generation with collision handling

#### 7. **Exception Handling** (`exception/`)
- **Purpose**: Custom exceptions and global exception handler
- **Components**:
  - `UrlNotFoundException.java`: When short code doesn't exist
  - `ValidationException.java`: When validation fails
  - `GlobalExceptionHandler.java`: Centralized exception handling

#### 8. **Configuration** (`config/`)
- **Purpose**: Application configuration
- **Components**:
  - `OpenApiConfig.java`: Swagger/OpenAPI documentation setup
  - `MongoConfig.java`: (if exists) MongoDB-specific configuration
  - `AppConfig.java`: (if exists) Application-wide configurations

---

### Data Flow

#### 1. **Create Short URL Flow**

```
┌─────────────────────────────────────────────────────────┐
│ Client sends POST /api/v1/urls with originalUrl         │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ UrlController        │
        │ @PostMapping /urls   │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Request Validation   │
        │ (Jakarta Validation) │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ UrlService.create()  │
        │ 1. Validate URL      │
        │ 2. Check idempotency │
        └──────────┬───────────┘
                   │
         ┌─────────┴─────────┐
         │ Already Exists?   │
         └─────────┬────┬────┘
                   │ No │ Yes
                   ▼    └───────────┐
        ┌──────────────────────┐    │
        │ Generate Short Code  │    │
        │ Base62Util.generate()│    │
        └──────────┬───────────┘    │
                   │                │
                   ▼                │
        ┌──────────────────────┐    │
        │ Save to MongoDB      │    │
        │ with unique index    │    │
        └──────────┬───────────┘    │
                   │                │
                   └────┬───────────┘
                        │
                        ▼
        ┌──────────────────────┐
        │ Return Response DTO  │
        │ shortCode, shortUrl  │
        └──────────┬───────────┘
                   │
                   ▼
            HTTP 201 Created
```

#### 2. **Redirect & Track Click Flow**

```
┌──────────────────────────────────┐
│ Client GET /api/v1/r/{shortCode} │
└─────────────┬────────────────────┘
              │
              ▼
   ┌──────────────────────┐
   │ UrlController        │
   │ @GetMapping /r/...   │
   └──────────┬───────────┘
              │
              ▼
   ┌──────────────────────┐
   │ Path Validation      │
   │ @Pattern regex check │
   └──────────┬───────────┘
              │
              ▼
   ┌──────────────────────┐
   │ UrlService.redirect()│
   └──────────┬───────────┘
              │
        ┌─────┴────────────┐
        │ Find short code  │
        │ from MongoDB     │
        └─────┬────────────┘
              │
         ┌────┴────┐
         │ Found?  │
         └────┬─┬──┘
        No    │ │ Yes
             ▼ ▼
        ┌─404 Error──────────────┐    ┌────────────────────┐
        │ UrlNotFoundException    │    │ Get Original URL   │
        └────────────────────────┘    └──────────┬─────────┘
                                                 │
                                                 ▼
                                      ┌────────────────────┐
                                      │ Atomic Increment   │
                                      │ Click Count        │
                                      │ MongoDB $inc       │
                                      └──────────┬─────────┘
                                                 │
                                                 ▼
                                      ┌────────────────────┐
                                      │ Return 302 Redirect│
                                      │ Location header    │
                                      └────────────────────┘
```

#### 3. **Get Analytics Flow**

```
┌────────────────────────────────┐
│ Client GET /api/v1/urls/{code} │
└─────────────┬──────────────────┘
              │
              ▼
   ┌──────────────────────┐
   │ UrlController        │
   │ @GetMapping /urls/.. │
   └──────────┬───────────┘
              │
              ▼
   ┌──────────────────────┐
   │ Path Validation      │
   │ @Pattern check       │
   └──────────┬───────────┘
              │
              ▼
   ┌──────────────────────┐
   │ UrlService.          │
   │ getAnalytics()       │
   └──────────┬───────────┘
              │
              ▼
   ┌──────────────────────┐
   │ Find from MongoDB    │
   └──────────┬───────────┘
              │
         ┌────┴────┐
         │ Found?  │
         └────┬─┬──┘
        No    │ │ Yes
             ▼ ▼
        ┌─404 Error──────────────┐    ┌────────────────────┐
        │ UrlNotFoundException    │    │ Build Analytics DTO│
        └────────────────────────┘    │ originalUrl        │
                                      │ shortCode          │
                                      │ clickCount         │
                                      │ createdAt          │
                                      └──────────┬─────────┘
                                                 │
                                                 ▼
                                           HTTP 200 OK
```

---

### Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Runtime** | Java | 21 | Language runtime |
| **Framework** | Spring Boot | 3.2.5 | Application framework |
| **Web** | Spring Web | 3.2.5 | REST endpoints |
| **Database** | MongoDB | 6.0+ | Document database |
| **ORM** | Spring Data MongoDB | 3.2.5 | MongoDB abstraction |
| **Validation** | Jakarta Validation | 3.0+ | Input validation |
| **Retry** | Spring Retry | Latest | Automatic retry on failure |
| **API Docs** | SpringDoc OpenAPI | 2.5.0 | Swagger/OpenAPI docs |
| **Testing** | JUnit 5 | Latest | Unit testing |
| **Mocking** | Mockito | Latest | Test mocking |
| **Build** | Maven | 3.8+ | Build automation |
| **Logging** | SLF4J/Logback | Latest | Application logging |

---

### Deployment Architecture

#### Single Server Deployment

```
┌─────────────────────────────────────────┐
│         Client Requests                  │
└──────────────────┬──────────────────────┘
                   │
        ┌──────────▼─────────┐
        │  Load Balancer     │
        │  (Optional)        │
        └──────────┬─────────┘
                   │
        ┌──────────▼─────────┐
        │ Spring Boot App    │
        │ :8080              │
        └──────────┬─────────┘
                   │
        ┌──────────▼─────────┐
        │ MongoDB            │
        │ :27017             │
        └────────────────────┘
```

#### Horizontally Scaled Deployment

```
┌──────────────────────────────────────┐
│       Client Requests                 │
└──────────────┬───────────────────────┘
               │
    ┌──────────▼──────────┐
    │   Load Balancer     │
    │   (Round Robin)     │
    └──┬──────────────┬───┘
       │              │
    ┌──▼──┐        ┌──▼──┐
    │App 1│        │App 2│  ...  ┌──────┐
    │:8080│        │:8080│       │App N │
    └──┬──┘        └──┬──┘       └─┬────┘
       │              │            │
       └──────────┬───┴────────────┘
                  │
       ┌──────────▼──────────┐
       │ MongoDB Cluster     │
       │ (Replica Set/Shards)│
       └─────────────────────┘
```

---

## LOW-LEVEL DESIGN (LLD)

### Module Descriptions

#### 1. **UrlEntity** (`entity/UrlEntity.java`)

**Purpose**: MongoDB document representing a URL mapping

**Structure**:
```java
@Document(collection = "shortner")
public record UrlEntity(
    @Id String id,
    @Indexed(unique = true) String originalUrl,
    @Indexed(unique = true) String shortCode,
    int clickCount,
    Instant createdAt,
    Instant updatedAt
)
```

**Key Features**:
- Record-based immutability
- Unique indexes on both `originalUrl` and `shortCode` for fast lookups
- Atomic operations support
- Builder pattern methods: `withClickCount()`, `withOriginalUrl()`, `incrementClickCount()`

**Justification for Unique Index on originalUrl**:
- Enables idempotency: same URL always returns same short code
- Prevents duplicate work in concurrent scenarios
- Enables efficient duplicate detection

---

#### 2. **UrlRepository** (`repository/UrlRepository.java`)

**Purpose**: Data access layer for MongoDB operations

**Method Signatures**:
```java
Optional<UrlEntity> findByShortCode(String shortCode);
Optional<UrlEntity> findByOriginalUrl(String originalUrl);
boolean existsByShortCode(String shortCode);
@Query("{'shortCode': ?0}")
@Update("{ $inc: { 'clickCount': ?1 } }")
long incrementClickCount(String shortCode, int increment);
```

**Thread-Safety Implementation**:
- `incrementClickCount()` uses MongoDB's atomic `$inc` operator
- No client-side read-modify-write pattern
- Database-level atomicity guarantees

**Why Atomic Updates Matter**:
```
❌ WRONG - Race Condition:
1. Thread A reads clickCount = 5
2. Thread B reads clickCount = 5
3. Thread A increments to 6, writes back
4. Thread B increments to 6, writes back
Result: Expected 7, got 6 (lost update)

✅ CORRECT - Atomic Update:
MongoDB processes: { $inc: { clickCount: 1 } }
- Read: 5
- Modify: 5 + 1
- Write: 6
(Entire operation is atomic at database level)
```

---

#### 3. **UrlService** (`service/UrlService.java`)

**Purpose**: Core business logic orchestration

**Key Methods**:

##### a. `createShortUrl(UrlRequestDto request)`

**Algorithm**:
```
1. Validate URL format (protocol, host)
2. Check if URL already shortened (idempotency)
   - If exists: return existing short code
   - If not: continue
3. Retry loop (up to 3 attempts):
   a. Generate unique short code
   b. Save to MongoDB with unique constraints
   c. On success: return response DTO
   d. On collision: retry with new code
```

**Idempotency Implementation**:
```java
// Check before generation to avoid wasted work
var existing = urlRepository.findByOriginalUrl(request.originalUrl());
if (existing.isPresent()) {
    return buildResponse(existing.get());
}

// Retry loop to handle race conditions
try {
    return createShortUrlWithCode(request.originalUrl(), generateShortCode());
} catch (DataIntegrityViolationException e) {
    // Another thread already saved this URL
    // Re-read and use existing
    var existing = urlRepository.findByOriginalUrl(request.originalUrl());
    if (existing.isPresent()) {
        return buildResponse(existing.get());
    }
    // Or retry with new code
}
```

##### b. `redirect(String shortCode)`

**Algorithm**:
```
1. Find URL entity by short code
   - If not found: throw UrlNotFoundException
   - If found: continue
2. Atomically increment click count using MongoDB $inc
3. Return original URL for redirect
```

**Why Separate Find and Increment**:
- Find returns the original URL needed immediately
- Increment is fire-and-forget for analytics
- No wait for async increment operation

##### c. `getAnalytics(String shortCode)`

**Algorithm**:
```
1. Find URL entity by short code
   - If not found: throw UrlNotFoundException
   - If found: continue
2. Map entity to analytics DTO
3. Return with current click count
```

---

#### 4. **Base62Util** (`util/Base62Util.java`)

**Purpose**: Thread-safe unique short code generation

**Character Set**: `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789` (62 characters)

**Code Length**: Default 6 characters → 62^6 = 56,800,235,584 possible combinations

**Thread-Safety Mechanisms**:

1. **SecureRandom for Randomness**
   ```java
   private static final SecureRandom RANDOM = new SecureRandom();
   // Thread-safe RNG, cryptographically strong
   ```

2. **ConcurrentHashMap for Collision Detection**
   ```java
   private static final Set<String> GENERATED_CODES = 
       ConcurrentHashMap.newKeySet();
   // Thread-safe set tracking all generated codes
   ```

3. **AtomicInteger for Metrics**
   ```java
   private static final AtomicInteger TOTAL_RETRIES = 
       new AtomicInteger(0);
   // Thread-safe retry counter
   ```

**Generation Algorithm**:
```
generateShortCode(length) {
    retry_count = 0
    while (retry_count < MAX_RETRIES) {
        code = random_base62_string(length)
        if (GENERATED_CODES.add(code)) {
            return code  // Success
        }
        // Collision detected
        retry_count++
        sleep(exponential_backoff)  // 1ms, 2ms, 4ms, 8ms, 16ms
    }
    throw RuntimeException("Unable to generate unique code")
}
```

**Collision Handling**:
- In-memory set tracks all generated codes per JVM instance
- MongoDB unique index prevents duplicates across instances
- Retry logic with exponential backoff reduces contention
- Extremely low collision rate (56+ billion combinations)

---

#### 5. **UrlController** (`controller/UrlController.java`)

**Purpose**: HTTP request handling and routing

**Endpoints**:

| Method | Endpoint | Status | Purpose |
|--------|----------|--------|---------|
| POST | `/api/v1/urls` | 201 | Create short URL |
| GET | `/api/v1/r/{shortCode}` | 302 | Redirect to original |
| GET | `/api/v1/urls/{shortCode}` | 200 | Get analytics |

**Request Validation**:
- Jakarta Validation annotations on DTOs
- @Pattern regex validation on path variables
- Global validation via @Validated annotation

**Response Handling**:
- ResponseEntity pattern for flexible status codes
- Consistent DTO format for all responses

---

#### 6. **GlobalExceptionHandler** (`exception/GlobalExceptionHandler.java`)

**Purpose**: Centralized exception handling

**Exception Mapping**:

| Exception | HTTP Status | Response |
|-----------|------------|----------|
| `UrlNotFoundException` | 404 | Not Found |
| `ValidationException` | 400 | Bad Request |
| `MethodArgumentNotValidException` | 400 | Validation Failed |
| `ConstraintViolationException` | 400 | Bad Request |
| `IllegalArgumentException` | 400 | Bad Request |
| `HttpMessageNotReadableException` | 400 | Malformed JSON |
| `ResponseStatusException` | Varies | Custom status |
| `Exception` (catch-all) | 500 | Internal Server Error |

**Error Response Format**:
```json
{
    "timestamp": "2026-05-17T12:30:45Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid URL format",
    "path": "/api/v1/urls"
}
```

---

### Database Schema

#### MongoDB Collection: `shortner`

**Document Structure**:
```json
{
    "_id": ObjectId("507f1f77bcf86cd799439011"),
    "originalUrl": "https://example.com/very/long/url/path?param=value",
    "shortCode": "aB12Cd",
    "clickCount": 42,
    "createdAt": ISODate("2026-05-17T12:30:45Z"),
    "updatedAt": ISODate("2026-05-17T13:45:30Z")
}
```

**Indexes**:

1. **Primary Index (Default)**
   - Field: `_id`
   - Type: Unique
   - Created by: MongoDB automatically
   - Purpose: Document identification

2. **Short Code Index**
   ```
   db.shortner.createIndex({ "shortCode": 1 }, { unique: true })
   ```
   - Field: `shortCode`
   - Type: Unique, Ascending
   - Purpose: O(1) lookup for redirects, collision prevention
   - Performance: ~1-2ms for 1M documents

3. **Original URL Index (Implicit through entity annotation)**
   ```
   db.shortner.createIndex({ "originalUrl": 1 }, { unique: true })
   ```
   - Field: `originalUrl`
   - Type: Unique, Ascending
   - Purpose: O(1) idempotency check, duplicate detection
   - Performance: ~1-2ms for 1M documents

**Query Performance**:
- Insert: O(1) with indexes
- Find by shortCode: O(log N) → O(1) with index
- Find by originalUrl: O(log N) → O(1) with index
- Update clickCount: O(1) atomic operation

**Storage Estimates** (per document):
- MongoDB overhead: ~30-50 bytes
- Field data: ~150-300 bytes
- Total per URL: ~200-350 bytes
- 1M URLs: ~200-350 MB

---

### API Contracts

#### 1. Create Short URL

**Request**:
```http
POST /api/v1/urls HTTP/1.1
Content-Type: application/json

{
    "originalUrl": "https://example.com/very/long/url"
}
```

**Response (201 Created)**:
```json
{
    "shortCode": "aB12Cd",
    "shortUrl": "http://localhost:8080/api/v1/aB12Cd",
    "originalUrl": "https://example.com/very/long/url",
    "createdAt": "2026-05-17T12:30:45Z"
}
```

**Error Responses**:
- 400: Invalid URL format
- 400: URL cannot be null or empty
- 400: URL must use http or https
- 500: Unable to generate unique code

---

#### 2. Redirect Endpoint

**Request**:
```http
GET /api/v1/r/aB12Cd HTTP/1.1
```

**Response (302 Found)**:
```http
HTTP/1.1 302 Found
Location: https://example.com/very/long/url
```

**Side Effects**:
- Click count incremented atomically
- No response body

**Error Responses**:
- 404: Short URL not found
- 400: Invalid short code format

---

#### 3. Get Analytics

**Request**:
```http
GET /api/v1/urls/aB12Cd HTTP/1.1
```

**Response (200 OK)**:
```json
{
    "originalUrl": "https://example.com/very/long/url",
    "shortCode": "aB12Cd",
    "clickCount": 42,
    "createdAt": "2026-05-17T12:30:45Z"
}
```

**Error Responses**:
- 404: Short URL not found
- 400: Invalid short code format

---

### Thread Safety & Concurrency

#### 1. Short Code Generation (Application Level)

**Thread-Safe Components**:
```java
SecureRandom RANDOM = new SecureRandom();  // ✓ Thread-safe
Set<String> GENERATED_CODES = ConcurrentHashMap.newKeySet();  // ✓ Thread-safe
AtomicInteger TOTAL_RETRIES = new AtomicInteger(0);  // ✓ Thread-safe
```

**Concurrent Scenario**:
```
Timeline: Thread A and Thread B both call generateShortCode() simultaneously

T1: Thread A generates "aB12Cd"
T1: Thread B generates "aB12Cd"
T2: Thread A calls GENERATED_CODES.add("aB12Cd") → returns true, uses code
T2: Thread B calls GENERATED_CODES.add("aB12Cd") → returns false, collision!
T3: Thread B retries, generates "xY98Zp"
T3: Thread B calls GENERATED_CODES.add("xY98Zp") → returns true, uses code
```

**Result**: Both threads get unique codes despite collision ✓

---

#### 2. Click Count Tracking (Database Level)

**Problem**:
```
Client 1 redirects: Reads clickCount = 5, Increments to 6, Writes back
Client 2 redirects: Reads clickCount = 5, Increments to 6, Writes back
Result: Expected 7, got 6 (lost update) ❌
```

**Solution - Atomic MongoDB $inc**:
```java
@Update("{ $inc: { 'clickCount': ?1 } }")
long incrementClickCount(String shortCode, int increment);
```

MongoDB Operation:
```javascript
db.shortner.updateOne(
    { shortCode: "aB12Cd" },
    { $inc: { clickCount: 1 } }
)
```

**Database-Level Atomic Operation**:
```
Client 1 → MongoDB: $inc clickCount by 1
Client 2 → MongoDB: $inc clickCount by 1

MongoDB processes both atomically:
1. Client 1's increment: 5 → 6
2. Client 2's increment: 6 → 7
Result: Expected 7, got 7 ✓
```

---

#### 3. Idempotency (Application Level)

**Race Condition Scenario**:
```
Client A sends: { originalUrl: "https://example.com" }
Client B sends: { originalUrl: "https://example.com" } (same URL)

Timeline:
T1: A checks if exists → Not found
T1: B checks if exists → Not found
T2: A generates code "aB12Cd", saves → Success
T2: B generates code "xY98Zp", saves → DataIntegrityViolationException!

Why exception? Because originalUrl has unique index,
and A already saved with that URL.
```

**Resolution**:
```java
try {
    return createShortUrlWithCode(request.originalUrl(), generateShortCode());
} catch (DataIntegrityViolationException e) {
    // Re-read the existing URL saved by other thread
    var existing = urlRepository.findByOriginalUrl(request.originalUrl());
    if (existing.isPresent()) {
        return buildResponse(existing.get());
        // Return A's short code to B → Idempotency achieved ✓
    }
}
```

---

#### 4. Spring Container Scope

**Service Scope**: `@Service` (Singleton)
```
Single UrlService instance per application context
↓
Thread-safe by design (no shared mutable state)
↓
All external state stored in MongoDB (shared, atomic updates)
```

**Controller Scope**: `@RestController` (Singleton)
```
Single UrlController instance per application context
↓
Delegates to UrlService (thread-safe)
↓
No shared mutable state
```

---

### Error Handling Strategy

#### Exception Hierarchy

```
Throwable
├── Exception
│   ├── IOException
│   ├── RuntimeException
│   │   ├── UrlNotFoundException (Custom)
│   │   ├── ValidationException (Custom)
│   │   └── IllegalArgumentException (Standard)
│   ├── MethodArgumentNotValidException (Spring)
│   ├── ConstraintViolationException (Jakarta)
│   └── ...
└── Error (System-level, not typically caught)
```

#### Exception Handling Flow

```
1. Exception thrown in controller/service
   ↓
2. Spring catches exception
   ↓
3. GlobalExceptionHandler examines exception type
   ↓
4. Appropriate @ExceptionHandler method invoked
   ↓
5. ErrorResponse DTO constructed
   ↓
6. HTTP response with appropriate status code
```

#### Error Response Example

```json
{
    "timestamp": "2026-05-17T12:30:45Z",
    "status": 404,
    "error": "Not Found",
    "message": "Short URL not found: invalidCode",
    "path": "uri=/api/v1/urls/invalidCode"
}
```

---

## DESIGN PATTERNS USED

### 1. **Layered Architecture Pattern**
- **Where**: Project structure
- **Benefit**: Separation of concerns, maintainability
- **Components**: Controller → Service → Repository → Database

### 2. **Repository Pattern**
- **Where**: `UrlRepository` interface
- **Benefit**: Abstraction of data access logic
- **Benefit**: Easy to mock for testing
- **Implementation**: Spring Data MongoDB

### 3. **Singleton Pattern**
- **Where**: Spring beans (`@Service`, `@Repository`, `@Controller`)
- **Benefit**: Per-application context instance, thread-safe
- **Cost**: Requires thread-safe implementation

### 4. **Immutability Pattern**
- **Where**: Entity records, DTOs
- **Benefit**: Thread-safe by design
- **Implementation**: Java records, final fields

### 5. **Data Transfer Object (DTO) Pattern**
- **Where**: `UrlRequestDto`, `UrlResponseDto`, `UrlAnalyticsDto`
- **Benefit**: Decouples internal representation from API contracts
- **Benefit**: Validation at boundaries

### 6. **Global Exception Handler Pattern**
- **Where**: `GlobalExceptionHandler` with `@ControllerAdvice`
- **Benefit**: Centralized exception handling
- **Benefit**: Consistent error responses

### 7. **Retry Pattern**
- **Where**: Short code generation with collision handling
- **Benefit**: Handles rare collision scenarios
- **Implementation**: Exponential backoff

### 8. **Atomic Operation Pattern**
- **Where**: MongoDB `$inc` operator for click count
- **Benefit**: Thread-safe without locks
- **Benefit**: High performance under concurrent load

### 9. **Idempotency Pattern**
- **Where**: URL shortening with unique constraint on originalUrl
- **Benefit**: Safe concurrent requests for same URL
- **Implementation**: Unique index + exception handling

### 10. **Factory Pattern** (Implicit)
- **Where**: DTOs built from entities
- **Implementation**: `buildResponse()`, `buildAnalytics()` methods

---

## NON-FUNCTIONAL REQUIREMENTS

### 1. **Performance**
- Short URL creation: < 500ms (p99)
- Redirect response time: < 100ms (p99)
- Analytics retrieval: < 200ms (p99)
- Database queries: O(1) or O(log N) with indexes

### 2. **Scalability**
- Horizontal scaling: Stateless application design
- Concurrent requests: Thread-safe operations
- Database scaling: MongoDB replica sets/sharding ready
- High traffic: Atomic operations prevent bottlenecks

### 3. **Availability**
- Service uptime: 99.9% (4.38 hours downtime/month)
- Health checks: Available via `/actuator/health`
- Graceful degradation: Service fails safe

### 4. **Reliability**
- Data persistence: MongoDB durability
- Atomic operations: No lost updates
- Retry logic: Handles transient failures
- Idempotency: Safe concurrent requests

### 5. **Security**
- Input validation: URL format, short code format
- Output encoding: Proper error messages
- Injection prevention: Parameterized queries
- Rate limiting: (Can be added via API Gateway)

### 6. **Maintainability**
- Clean code: Layered architecture, clear responsibilities
- Testing: Comprehensive unit and integration tests
- Documentation: JavaDoc, API docs via Swagger
- Logging: DEBUG level in development, INFO in production

### 7. **Observability**
- Logging: Structured logs with thread ID, log level
- Metrics: Spring Boot Actuator endpoints
- Health checks: MongoDB connectivity status
- API Documentation: OpenAPI/Swagger UI

---

## SCALABILITY & PERFORMANCE

### Horizontal Scaling Architecture

```
┌──────────────────────────────────────────────────────┐
│ Client Requests (1000s per second)                   │
└─────────────────┬──────────────────────────────────┘
                  │
        ┌─────────▼────────────┐
        │ Load Balancer        │
        │ (Round Robin)        │
        └┬──────────────────┬──┘
         │                  │
    ┌────▼────┐        ┌────▼────┐
    │App Pod 1│        │App Pod N│  ← Stateless
    │:8080    │        │:8080    │     (Scale easily)
    └────┬────┘        └────┬────┘
         │                  │
         └──────────┬───────┘
                    │
         ┌──────────▼────────────┐
         │ MongoDB Cluster       │
         │ (Replica Set)         │
         └───────────────────────┘
         - Primary read/write
         - Secondaries for read
         - Automatic failover
```

### Database Performance Optimization

**Query Performance with Indexes**:
```
Without Index:
  db.shortner.find({ shortCode: "aB12Cd" })
  → Full collection scan: O(N) ~ 5-10ms for 1M documents

With Index:
  db.shortner.find({ shortCode: "aB12Cd" })
  → B-tree lookup: O(log N) ~ 0.1-0.2ms for 1M documents
  → 50-100x faster with index ✓
```

**Atomic Update Performance**:
```
MongoDB $inc operation:
  db.shortner.updateOne(
      { shortCode: "aB12Cd" },
      { $inc: { clickCount: 1 } }
  )
  → Server-side atomic operation: ~0.5-1ms
  → No lock contention
  → No application-level synchronization needed
```

### Connection Pooling

**MongoDB Connection Pool**:
```
Max Pool Size: 100
Min Pool Size: 0
Timeout: 10 seconds
Socket Timeout: 10 seconds

Benefits:
- Reuse connections: Reduces handshake overhead
- Handle spikes: Up to 100 concurrent database operations
- Auto-failover: Connections to primary/replicas
```

### Caching Strategy (Future Enhancement)

```
Level 1: Browser Caching
  GET /api/v1/r/aB12Cd → HTTP 301/302 with Cache-Control
  Pros: Zero server load for repeat visitors
  Cons: Cannot track clicks accurately

Level 2: Redis Cache (Optional)
  Cache frequently accessed short codes
  TTL: 1 hour
  Invalidation: LRU + TTL

Level 3: Application Cache
  Cache hot short codes in memory (ConcurrentHashMap)
  Pros: Zero latency for hot URLs
```

### Load Testing Results (Expected)

```
Scenario: 1000 concurrent users, 10 seconds

Single Server (No Redis):
  Throughput: 5000-10000 requests/second
  Latency p99: 100-200ms
  Database utilization: 70-80%
  CPU utilization: 60-70%

3 Servers (No Redis):
  Throughput: 15000-30000 requests/second
  Latency p99: 50-100ms
  Database utilization: 60-70%
  CPU utilization: 40-50%

3 Servers + Redis Cache:
  Throughput: 30000-50000 requests/second
  Latency p99: 10-50ms
  Database utilization: 10-20%
  CPU utilization: 30-40%
```

---

## FUTURE ENHANCEMENTS

### 1. **Redis Caching**
```java
@Cacheable(value = "urls", key = "#shortCode")
public UrlAnalyticsDto getAnalytics(String shortCode) {
    return urlService.getAnalytics(shortCode);
}
```

### 2. **Rate Limiting**
```java
@RateLimiter(value = "10", timeUnit = "1m")
@PostMapping("/urls")
public ResponseEntity<UrlResponseDto> createShortUrl(...) {
    // Limited to 10 requests per minute per IP
}
```

### 3. **Asynchronous Processing**
```java
@Async
public CompletableFuture<Void> trackClickAsync(String shortCode) {
    return CompletableFuture.runAsync(() -> 
        urlRepository.incrementClickCount(shortCode, 1)
    );
}
```

### 4. **Custom Short Codes**
```json
Post request:
{
    "originalUrl": "https://...",
    "customShortCode": "my-link"
}
```

### 5. **URL Expiration (TTL)**
```java
public record UrlEntity(
    ...
    Instant expiresAt,
    ...
) {}
```

### 6. **Geographic Analytics**
```java
public record ClickEvent(
    String shortCode,
    String country,
    String city,
    String userAgent,
    Instant timestamp
) {}
```

---

## MONITORING & OBSERVABILITY

### Metrics Exposed (Spring Boot Actuator)

```
GET /actuator/metrics

Key Metrics:
- jvm.memory.used
- jvm.threads.live
- process.cpu.usage
- http.server.requests
- mongodb.driver.pool.size
```

### Health Check

```
GET /actuator/health

Response:
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "MongoDB",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": {
            "status": "UP",
            "details": {
                "total": 499963174912,
                "free": 237540020224
            }
        }
    }
}
```

### Logging Strategy

```
Application Logs:
- DEBUG: Detailed execution flow (development)
- INFO: Important events (production)
- WARN: Potential issues
- ERROR: Errors with recovery
- FATAL: Critical failures

Log Pattern:
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

Example:
2026-05-17 12:30:45.123 [http-nio-8080-exec-1] INFO  com.mks.open.controller.UrlController - Creating short URL for: https://example.com
2026-05-17 12:30:45.456 [http-nio-8080-exec-1] DEBUG com.mks.open.service.UrlService - Short code generated: aB12Cd
2026-05-17 12:30:45.789 [http-nio-8080-exec-1] INFO  com.mks.open.repository.UrlRepository - URL saved successfully
```

---

## CONCLUSION

The URL Shortener Backend is architected for **production-grade reliability, performance, and scalability**:

✓ **Thread-safe** operations at both application and database levels  
✓ **High performance** with atomic operations and optimized indexes  
✓ **Horizontally scalable** with stateless design  
✓ **Highly available** with automatic failover support  
✓ **Maintainable** with clean layered architecture  
✓ **Observable** with comprehensive logging and metrics  
✓ **Testable** with clear separation of concerns  

This design ensures the system can handle millions of URLs and billions of redirects reliably and efficiently.

---

**Document Version**: 1.0  
**Last Updated**: May 17, 2026  
**Author**: Development Team  
**Status**: Final

