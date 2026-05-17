# Visual Guide: Concurrent URL Shortening Flow

## Request Flow Diagram

### Without Idempotency Check (❌ Wrong)

```
┌─────────────────┐
│  Request A      │
│ Same URL        │
└────────┬────────┘
         │
         ▼
   ┌─────────────┐
   │ Generate    │
   │ shortCode1  │
   │ "abc123"    │
   └─────┬───────┘
         │
         ▼
    ┌────────────┐
    │ Save to DB │
    │ Success ✓  │
    └─────┬──────┘
          │
          ▼
    ┌──────────────────┐
    │ Return           │
    │ "abc123" ← User A gets this
    └──────────────────┘

┌─────────────────┐
│  Request B      │
│ Same URL        │← PARALLEL EXECUTION
└────────┬────────┘
         │
         ▼
   ┌─────────────┐
   │ Generate    │
   │ shortCode2  │
   │ "xyz789"    │◄─── Different code!
   └─────┬───────┘
         │
         ▼
    ┌────────────┐
    │ Save to DB │
    │ Success ✓  │
    └─────┬──────┘
          │
          ▼
    ┌──────────────────┐
    │ Return           │
    │ "xyz789" ← User B gets this (WRONG!)
    └──────────────────┘

PROBLEM: Same URL shortened twice! ❌
DATABASE: [abc123, xyz789] for same URL
```

---

### With Idempotency Check (✅ Correct)

```
┌─────────────────┐         ┌─────────────────┐
│  Request A      │         │  Request B      │
│ Same URL        │         │ Same URL        │
└────────┬────────┘         └────────┬────────┘
         │                           │
         ▼                           ▼
    ┌──────────────────┐      ┌──────────────────┐
    │ findByOriginal   │      │ findByOriginal   │
    │ URL() → NOT FOUND│      │ URL() → NOT FOUND│
    └────────┬─────────┘      └────────┬─────────┘
             │ (Both queries complete)  │
             ▼                          ▼
       ┌──────────────┐          ┌──────────────┐
       │ Generate     │          │ Generate     │
       │ "abc123"     │          │ "xyz789"     │
       └────────┬─────┘          └────────┬─────┘
                │                         │
                ▼                         ▼
           ┌─────────┐              ┌─────────┐
           │ Save A  │─────────────▶│ Save B  │
           │ SUCCESS │(B checks DB  │ BLOCKED │
           └────┬────┘  after A)    └────┬────┘
                │                        │
                ▼                        ▼
        ┌──────────────────┐    ┌──────────────────┐
        │findByOriginalURL │    │findByOriginalURL │
        │() → FOUND! (A)   │    │() → FOUND! (A)   │
        └────────┬─────────┘    └────────┬─────────┘
                 │                      │
                 ▼                      ▼
        ┌──────────────────┐    ┌──────────────────┐
        │ Return A's code  │    │ Return A's code  │
        │ "abc123"         │    │ "abc123"         │
        └──────────────────┘    └──────────────────┘

✅ CORRECT: Same code for same URL!
DATABASE: [abc123] for same URL (no duplicates)
```

---

## Database Index Strategy

```
┌─────────────────────────────────────────────┐
│         MongoDB Collection: shortner        │
└─────────────────────────────────────────────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
         ▼              ▼              ▼
    ┌─────────┐    ┌──────────┐   ┌──────────┐
    │   _id   │    │shortCode │   │original │
    │         │    │ (UNIQUE) │   │  URL    │
    │ INDEX 1 │    │ INDEX 2  │   │INDEX 3  │
    │PRIMARY  │    │ PK       │   │         │
    │KEY      │    │ O(1)     │   │ O(1)    │
    └─────────┘    │          │   │         │
                   │Prevents  │   │Duplicate│
                   │collisions│   │ detect  │
                   └──────────┘   └──────────┘
         │                              │
         │        ┌──────────┐         │
         │        │clickCount│         │
         │        │ INDEX 4  │         │
         │        │ O(log n) │         │
         │        │Analytics │         │
         │        └──────────┘         │
         │              │              │
         └──────────────┼──────────────┘
                        │
                   ┌────────┐
                   │createdAt│
                   │INDEX 5  │
                   │O(log n) │
                   │Time-base│
                   └────────┘

Query Performance:
┌─────────────────────────────────────┐
│ GET /{shortCode} → INDEX 2 (O(1))   │
│ Find by originalURL → INDEX 3 (O(1))│
│ Analytics query → INDEX 4 (O(log n))│
│ Time-based filters → INDEX 5        │
└─────────────────────────────────────┘
```

---

## Concurrency Model

### Timeline: Two Concurrent Requests

```
TIME  REQUEST A                      REQUEST B
────────────────────────────────────────────────
 T0   POST /api/v1/urls          POST /api/v1/urls
      {"originalUrl": "X"}       {"originalUrl": "X"}
      │                          │
      ▼                          ▼
      
 T1   findByOriginalURL(X)       findByOriginalURL(X)
      │                          │
      ├─ Query Index: NOT FOUND   │
      │  (or: slow query time)    │
      │                           ├─ Query Index: NOT FOUND
      │                           │
 T2   Generator                   Generator
      "code1" = random(6-8)      "code2" = random(6-8)
      │                          │
      ▼                          ▼

 T3   INSERT INTO shortner       (Waiting at T1)
      _id, X, "code1", 0         │
      │                          │
      ▼                          ▼
      
      ✓ COMMITTED                findByOriginalURL(X)
      Database now has:          Query Index
      - originalUrl: X           │
      - shortCode: "code1"       ├─ FOUND!
      │                          │ Returns "code1"
      │                          │
 T4   │                          ▼
      │                          
      │                          Return Response
      │                          shortCode: "code1"
      │
      ▼
      
      Return Response
      shortCode: "code1"
      
      
RESULT:
 User A gets: "code1" ✓
 User B gets: "code1" ✓ ← SAME!
 Database:   [code1]  ← NO DUPLICATES!
```

---

## Click Count Update Flow

### Problem: Lost Updates Without Atomic Operations

```
Initial clickCount = 0

USER A clicks         USER B clicks         USER C clicks
      │                    │                    │
      ▼                    ▼                    ▼
   
   READ: 0          READ: 0              READ: 0
      │                │                    │
      ▼                ▼                    ▼
      
   CALC: 0+1=1     CALC: 0+1=1          CALC: 0+1=1
      │                │                    │
      ▼                ▼                    ▼
      
   WRITE: 1        WRITE: 1             WRITE: 1
   
   
RESULT: clickCount = 1 ❌ (Should be 3!)
LOST UPDATES! Two clicks disappeared!
```

---

### Solution: MongoDB Atomic Update with $inc

```
Initial clickCount = 0

USER A           USER B                USER C
   │                │                     │
   ├──────────┬──────┴────────┬──────────┤
   │          │               │          │
   ▼          ▼               ▼          │
   
   ATOMIC OPERATION: $inc { clickCount: 1 }
   │                                     │
   └──────────────────┬──────────────────┘
                      │
                      ▼
          ┌──────────────────────┐
          │ MongoDB Server-Side  │
          │ (Single Thread)      │
          │                      │
          │ All updates queued   │
          │ and executed one-by-one
          │                      │
          │ clickCount += 1      │ (A)
          │ clickCount += 1      │ (B)
          │ clickCount += 1      │ (C)
          │                      │
          │ Final: 3             │
          └──────────────────────┘
          
RESULT: clickCount = 3 ✓ (Correct!)
NO LOST UPDATES!
```

---

## Architecture: Multi-Layer Protection

```
                    REQUEST
                        │
                        ▼
          ┌─────────────────────────┐
          │  APPLICATION LAYER      │
          │  (UrlService)           │
          │  ────────────────────   │
          │  - Validate URL         │  Layer 1
          │  - Query duplicate      │  APPLICATION
          │ ┌─ Check original       │  IDEMPOTENCY
          │ └─ Create if not exists │
          └─────────────┬───────────┘
                        │
                        ▼
          ┌─────────────────────────┐
          │  DATABASE CONSTRAINT    │
          │  MongoDB Unique Index   │
          │  ────────────────────   │
          │  - Unique on shortCode  │  Layer 2
          │  - Prevent collisions   │  DATABASE
          │  - Retry on failure     │  UNIQUENESS
          └─────────────┬───────────┘
                        │
                        ▼
          ┌─────────────────────────┐
          │  ATOMIC OPERATIONS      │
          │  MongoDB $inc Operator  │
          │  ────────────────────   │
          │  - Click count update   │  Layer 3
          │  - Server-side atomic   │  ATOMIC OPS
          │  - No lost updates      │
          └─────────────┬───────────┘
                        │
                        ▼
                    RESPONSE


Benefits of Multi-Layer:
✓ Layer 1 catches duplicates early (fast path)
✓ Layer 2 provides database-level guarantee
✓ Layer 3 ensures data consistency
✓ No single point of failure
✓ Production-grade reliability
```

---

## Index Lookup Performance

```
Scenario: 1 Million URLs in Database

WITHOUT INDEXES:
┌────────────────────────┐
│ findByOriginalUrl(X)   │
│ Linear scan: O(n)      │
│ 1,000,000 comparisons  │
│ ~1000ms ⚠️ TOO SLOW    │
└────────────────────────┘

WITH B-TREE INDEX:
┌────────────────────────┐
│ findByOriginalUrl(X)   │
│ Index lookup: O(log n) │
│ ~20 comparisons        │
│ ~1ms ✓ FAST            │
└────────────────────────┘

Index Structure (Simplified):
              [M]
             /   \
           [G]   [S]
          / \    / \
        [D][J][P][U]
         │   │  │  │
        [A] [H] [R] [Z]

Search for URL "X":
M < X → go right → S
S > X → go left → U
U < X → go right → Z contains X
Found in ~3 steps ✓
```

---

## Connection Pool with Concurrent Requests

```
┌────────────────────────────────────┐
│    Spring Boot Application         │
│  (Default: 4 connection pool)      │
└────────────────────────────────────┘
           │
    ┌──────┼──────┬──────┬──────┐
    │      │      │      │      │
    ▼      ▼      ▼      ▼      ▼
┌─────┐┌─────┐┌─────┐┌─────┐
│Conn1││Conn2││Conn3││Conn4│
└──┬──┘└──┬──┘└──┬──┘└──┬──┘
   │      │      │      │
   │      │      │      └────── User D redirect
   │      │      └───────────── User C create shortURL
   │      └──────────────────── User B create shortURL
   └─────────────────────────── User A create shortURL
           │
           ▼
    ┌──────────────────┐
    │ MongoDB Cluster  │
    │    (shared)      │
    │                  │
    │ All connections  │
    │ query same       │
    │ indexes and      │
    │ constraints      │
    └──────────────────┘

Concurrent Handling:
- Multiple connection pools in different threads
- Each thread has its own connection
- All queries go to same MongoDB
- MongoDB handles concurrency with document-level locking
- Result: Multiple concurrent requests processed correctly
```

---

## State Machine: URL Creation

```
START
  │
  ▼
┌──────────────────────┐
│ Validate URL format  │
│ - http/https only    │
│ - valid host         │
│ - no empty           │
└──────────────────────┘
  │ ✓ Valid
  ▼
┌──────────────────────┐
│ Query originalUrl    │
│ findByOriginal(url)  │
└──────────────────────┘
  │
  ├─ FOUND ─────────────┐
  │                     │
  │ ✓ Exists            ▼
  │            ┌──────────────────┐
  │            │ Return existing  │
  │            │ short code       │
  │            │ (idempotent)     │
  │            │ STATUS: 200 OK   │
  │            └──────────────────┘
  │
  └─ NOT FOUND
        │
        ▼
┌──────────────────────┐
│ Generate short code  │
│ random 6-8 chars     │
│ Base62 alphabet      │
└──────────────────────┘
        │
        ▼
┌──────────────────────┐
│ INSERT into MongoDB  │
│ Save new document    │
└──────────────────────┘
        │
        ├─ SUCCESS
        │   │
        │   ▼
        │ ┌──────────────────┐
        │ │ Return new code  │
        │ │ STATUS: 201      │
        │ │ CREATED          │
        │ └──────────────────┘
        │
        └─ COLLISION (unique index violation)
            │
            ├─ Retry < 3?
            │   │
            │   YES ─▶ (Go back to Generate)
            │   │
            │   NO ▼
            │    ┌──────────────────┐
            │    │ Throw Exception  │
            │    │ 500 ERROR        │
            │    │ (Very rare)      │
            │    └──────────────────┘
            │
            └─ OTHER ERROR
                │
                ▼
                ┌──────────────────┐
                │ Throw Exception  │
                │ 500 ERROR        │
                └──────────────────┘

END
```

---

## Summary Matrix

| Aspect | With Fix | Without Fix |
|--------|----------|------------|
| **Same URL twice** | Same code ✅ | Different codes ❌ |
| **Concurrent requests** | Protected efficiently | Race condition |
| **Database duplicates** | Prevented by index | Possible |
| **Click count accuracy** | Atomic operations | Lost updates |
| **Performance** | O(1) operations | N/A |
| **Scalability** | Horizontal ready | Issues |

---

