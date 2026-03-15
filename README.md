# 🏭 Warehouse Inventory — Cursor Scroll Pagination API
### Built with Spring Boot 4 · Java 21 · MyQL · Spring Data Scroll API

---

## 📌 What Is This Project?

This is a **production-ready REST API** for a Warehouse Inventory Management System.

The main feature is **Cursor-based Scroll Pagination** — a modern, fast alternative to traditional page-number pagination — built using **Spring Data's Window / ScrollPosition API**.

Instead of saying *"give me page 500"*, the API uses a **bookmark (scrollId)** to say *"give me the next 20 items after where I last stopped."*

---

## ❌ The Problem — Why Normal Pagination Breaks

Most developers start with offset pagination. It looks simple:

```
GET /products?page=0&size=20   ← Page 1
GET /products?page=1&size=20   ← Page 2
GET /products?page=500&size=20 ← Page 500  ← 💀 THIS IS THE PROBLEM
```

### What actually happens at Page 500:

```
Database thinks:
  "I need rows 10,001 to 10,020"

  Step 1 → Scan and read 10,000 rows
  Step 2 → Throw all 10,000 away   ← WASTED WORK
  Step 3 → Return next 20 rows     ← only this was needed

  Rows touched  = 10,020
  Rows returned = 20
  Waste         = 10,000 rows ❌
```

### Real performance impact on 1 million rows:

| Page Depth | Offset Pagination | Cursor Pagination | Difference |
|------------|-------------------|-------------------|------------|
| Page 1     | 2ms               | 2ms               | Same       |
| Page 100   | 45ms              | 2ms               | 22x faster |
| Page 1,000 | 420ms             | 2ms               | 210x faster|
| Page 10,000| 4,200ms           | 2ms               | 2100x faster|
| Page 50,000| ⏱️ TIMEOUT        | 2ms               | ∞          |

### 5 problems with offset pagination:

1. **Gets slower the deeper you go** — linear scan grows with every page
2. **Data shifts under your feet** — new items added = duplicates or missing items in your feed
3. **COUNT(\*) is expensive** — showing "Page 5 of 2,847" costs a full table scan every request
4. **No memory** — each request is completely blind to what it returned before
5. **Breaks infinite scroll** — mobile apps get duplicate items when new data is added

---

## ✅ The Solution — Cursor / Keyset Pagination

Think of it like a **bookmark in a book**.

Instead of counting pages, you open directly where your bookmark is.

```sql
-- What Spring Scroll API generates internally:
SELECT * FROM wh_shipment_items
WHERE id > 1000          -- jumps directly using B-tree index
ORDER BY id ASC
LIMIT 20

-- Database:
  Step 1 → Jump to id=1000 via B-tree index  (nanoseconds)
  Step 2 → Read next 20 rows                 (microseconds)
  Step 3 → Done

  Rows touched  = 20
  Rows returned = 20
  Waste         = 0 ✅
```

**Constant time regardless of depth** — Page 1 and Page 50,000 are equally fast.

---

## 🔧 How It Works — Step by Step

### Round 1 — First Request (No cursor)

```
Client → GET /api/v1/warehouse/inventory/scroll?pageSize=20

Server:
  1. No scrollId → start from beginning
  2. Run: SELECT * FROM items ORDER BY id ASC LIMIT 20
  3. Encode last row's id into Base64 token

Response:
  {
    "items": [ ...20 items... ],
    "scrollId": "eyJpZCI6MjB9",   ← bookmark
    "hasNext": true,
    "pageSize": 20
  }
```

### Round 2 — Next Page (Pass the scrollId back)

```
Client → GET /scroll?scrollId=eyJpZCI6MjB9

Server:
  1. Decode "eyJpZCI6MjB9" → { id: 20 }
  2. Run: SELECT * FROM items WHERE id > 20 ORDER BY id LIMIT 20
  3. Encode new bookmark

Response:
  {
    "items": [ ...next 20 items... ],
    "scrollId": "eyJpZCI6NDB9",   ← new bookmark
    "hasNext": true
  }
```

### Last Page

```
Response:
  {
    "items": [ ...final items... ],
    "scrollId": null,    ← no more data
    "hasNext": false
  }
```

---

## 📂 Project Structure

```
warehouse-inventory/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/warehouse/inventory/
    │   │   ├── WarehouseApplication.java
    │   │   ├── controller/
    │   │   │   └── WarehouseInventoryController.java   ← REST endpoints
    │   │   ├── service/
    │   │   │   └── WarehouseScrollService.java         ← business logic + scroll
    │   │   ├── repository/
    │   │   │   └── ShipmentItemRepository.java         ← JPA + Specification
    │   │   ├── entity/
    │   │   │   ├── ShipmentItem.java                   ← @PrePersist / @PreUpdate
    │   │   │   ├── AuditableEntity.java                ← createdAt / updatedAt
    │   │   │   └── ItemStatus.java                     ← AVAILABLE, RESERVED, etc.
    │   │   ├── dto/
    │   │   │   ├── ShipmentItemDto.java
    │   │   │   ├── ScrollRequest.java
    │   │   │   └── ScrollResponse.java
    │   │   ├── scroll/
    │   │   │   └── WarehouseScrollCodec.java           ← encode/decode scrollId
    │   │   ├── specification/
    │   │   │   └── ShipmentItemSpecification.java      ← dynamic filters
    │   │   ├── config/
    │   │   │   ├── AppConfig.java                      ← ModelMapper bean
    │   │   │   └── DataSeeder.java                     ← @PostConstruct seed data
    │   │   └── exception/
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.yml                         ← prod config
    │       └── application-dev.yml                     ← dev + H2 config
    └── test/
        └── java/com/warehouse/inventory/
```

---

## 🚀 Tech Stack

| Technology        | Version | Purpose |
|-------------------|---------|---------|
| Spring Boot       | 4.0.0   | Core framework |
| Java              | 21      | Language (required by Boot 4) |
| Spring Data JPA   | Latest  | Window / Scroll API |
| Hibernate         | 7.x     | ORM |
| MySQL             | 8+      | Production database |
| ModelMapper       | 3.2.1   | Entity → DTO mapping |
| Jackson           | Latest  | JSON + LocalDateTime codec |
| Lombok            | Latest  | Boilerplate reduction |
| SpringDoc OpenAPI | 2.8.6   | Swagger UI |

---

## 🗝️ The scrollId Explained

The `scrollId` is just a **Base64-encoded JSON** of the last row's key values.

```
"eyJpZCI6MjB9"
       ↓  Base64 decode
  {"id": 20}
```

### Why Base64?

- JSON cannot be passed safely in a URL query parameter
- Base64 makes it URL-safe without any special characters
- It is NOT encrypted — it is just encoded

### Multi-column sort scrollId

When sorting by `unitPrice` (not unique), two items can share the same price.
The cursor stores **both fields** to guarantee uniqueness:

```
{ "unitPrice": 100.00, "id": 19 }
       ↓ encodes to
"eyJ1bml0UHJpY2UiOjEwMC4wLCJpZCI6MTl9"
```

The query becomes:
```sql
WHERE (unit_price > 100.00)
OR    (unit_price = 100.00 AND id > 19)
ORDER BY unit_price ASC, id ASC
LIMIT 20
```

**No duplicates. No skipped rows. Ever.**

---

## 🌱 Data Seeding — @PostConstruct

200 realistic warehouse items are seeded automatically on first startup using `@PostConstruct`.

```java
@PostConstruct        // fires after Spring injects all dependencies
@Transactional
public void saveData() {
    if (repository.count() > 0) return;  // skip if already seeded
    // ... save 200 items in batches of 50
}
```

Lifecycle order:
```
1. Spring creates DataSeeder bean
2. Spring injects ShipmentItemRepository
3. @PostConstruct fires → saveData() runs
4. 200 rows inserted in batches
5. App starts serving HTTP requests
```

Only runs when `spring.profiles.active=dev` — **never in production.**

---

## ⏱️ Timestamps — @PrePersist / @PreUpdate

No Spring Auditing needed. Pure JPA lifecycle hooks in `AuditableEntity`:

```java
@PrePersist
protected void onPrePersist() {
    this.createdAt = LocalDateTime.now();  // set on INSERT
    this.updatedAt = LocalDateTime.now();
}

@PreUpdate
protected void onPreUpdate() {
    this.updatedAt = LocalDateTime.now();  // update on every UPDATE
}
```

---

## 🔗 API Reference

### Base URL
```
http://localhost:8080/api/v1/warehouse/inventory
```

### Scroll Endpoint

```
GET /scroll
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `scrollId` | String | No | null | Cursor from previous response |
| `pageSize` | int | No | 20 | Items per page (max 100) |
| `sortBy` | String | No | id | Field to sort by |
| `sortDirection` | String | No | ASC | ASC or DESC |
| `keyword` | String | No | - | Search in name / SKU |
| `zoneCode` | String | No | - | Filter by zone (A1, HAZMAT, etc.) |
| `status` | Enum | No | - | AVAILABLE / RESERVED / DAMAGED / etc. |
| `minPrice` | Double | No | - | Minimum unit price |
| `maxPrice` | Double | No | - | Maximum unit price |
| `expiringBefore` | DateTime | No | - | ISO 8601 format |
| `belowReorder` | Boolean | No | - | Items needing restock |

### Response Shape

```json
{
  "items": [
    {
      "id": 21,
      "sku": "WH-A021",
      "itemName": "Hydraulic Pump #21",
      "zoneCode": "B1",
      "status": "AVAILABLE",
      "quantityOnHand": 342,
      "reorderLevel": 20,
      "unitPrice": 149.99,
      "totalValue": 51296.58,
      "expiryDate": null,
      "needsReorder": false,
      "createdAt": "2025-03-12T10:00:00",
      "updatedAt": "2025-03-12T10:00:00"
    }
  ],
  "scrollId": "eyJpZCI6NDB9",
  "hasNext": true,
  "pageSize": 20
}
```

---

## 🔍 Filter/Match Types — API Usage & Examples

The scroll API supports multiple filter types (match types) for flexible querying. Below are all supported types, their behavior, and example usages:

| Parameter         | Match Type         | Description                                                      | Example Query                                                                                 |
|-------------------|-------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `keyword`         | Partial (LIKE)    | Case-insensitive partial match on `itemName` or `sku`            | `/scroll?keyword=pump`                                                                       |
| `zoneCode`        | Exact (case-ins.) | Case-insensitive exact match on warehouse zone                   | `/scroll?zoneCode=A1`                                                                        |
| `status`          | Enum (exact)      | Exact match on item status (AVAILABLE, RESERVED, etc.)           | `/scroll?status=AVAILABLE`                                                                   |
| `minPrice`        | Range (>=)        | Minimum unit price (inclusive)                                   | `/scroll?minPrice=100`                                                                       |
| `maxPrice`        | Range (<=)        | Maximum unit price (inclusive)                                   | `/scroll?maxPrice=500`                                                                       |
| `expiringBefore`  | Date (<=)         | Items expiring before this datetime (ISO 8601)                   | `/scroll?expiringBefore=2025-12-31T00:00:00`                                                 |
| `belowReorder`    | Boolean           | Only items where `quantityOnHand` <= `reorderLevel`              | `/scroll?belowReorder=true`                                                                  |

### Example: Combined Filters

You can combine any filters for advanced queries:

```bash
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?keyword=pump&zoneCode=B1&minPrice=100&maxPrice=500&status=AVAILABLE&belowReorder=true"
```

- Returns all AVAILABLE items in zone B1, with 'pump' in name or SKU, price between 100 and 500, and needing reorder.

### Filter Logic Details
- All filters are **ANDed** together (must match all provided conditions).
- `keyword` matches if either `itemName` or `sku` contains the value (case-insensitive).
- `zoneCode` is case-insensitive (e.g., 'a1' and 'A1' are treated the same).
- `status` must be a valid enum value (see API docs for allowed values).
- `minPrice`/`maxPrice` are inclusive.
- `expiringBefore` uses ISO 8601 format (e.g., `2025-12-31T00:00:00`).
- `belowReorder=true` returns only items where `quantityOnHand` <= `reorderLevel`.

---

## 🧪 Sample CURL Commands

```bash
# First page
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll"

# Next page (use scrollId from above response)
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?scrollId=eyJpZCI6MjB9"

# Filter: COLD-STORAGE + AVAILABLE
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?zoneCode=COLD-STORAGE&status=AVAILABLE"

# Items expiring soon (FEFO)
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?expiringBefore=2025-12-31T00:00:00&sortBy=expiryDate&sortDirection=ASC"

# Low stock alert
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?belowReorder=true&pageSize=50"

# Price range + sort
curl "http://localhost:8080/api/v1/warehouse/inventory/scroll?minPrice=10&maxPrice=500&sortBy=unitPrice&sortDirection=DESC"
```

---

## ▶️ Running the App

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker (optional, for MySQL)

### Dev Mode (H2 in-memory — zero setup)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

App starts at: `http://localhost:8080`
H2 Console at: `http://localhost:8080/h2-console`
Swagger UI at:  `http://localhost:8080/swagger-ui.html`

### Production Mode (MySQL)

```bash
export DB_PASSWORD=your_password
./mvnw spring-boot:run
```

---

## 🛡️ Production Safety Features

| Feature | How |
|---------|-----|
| Page size clamped to max 100 | `Math.min(pageSize, 100)` in service |
| Corrupt scrollId → graceful reset | Try-catch in codec returns first page |
| SQL injection impossible | Field whitelist in `validateAndMapSortField()` |
| Soft deletes excluded always | `deleted = false` always in Specification |
| Read transactions optimized | `@Transactional(readOnly = true)` |
| Error responses standardized | RFC 9457 `ProblemDetail` format |

---

## 🧩 Code & Logic Walkthrough

### 1. Controller: `WarehouseInventoryController`

- **Purpose:** Exposes the `/api/v1/warehouse/inventory/scroll` endpoint for cursor-based pagination.
- **Key Code:**
  ```java
  @GetMapping("/scroll")
  public ResponseEntity<@NonNull ScrollResponse<ShipmentItemDto>> scroll(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String zoneCode,
      @RequestParam(required = false) ItemStatus status,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiringBefore,
      @RequestParam(required = false) Boolean belowReorder,
      @RequestParam(required = false) String scrollId,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "id")  String sortBy,
      @RequestParam(defaultValue = "ASC") String sortDirection
  ) {
      ScrollRequest request = ScrollRequest.builder()
          .keyword(keyword)
          .zoneCode(zoneCode)
          .status(status)
          .minPrice(minPrice)
          .maxPrice(maxPrice)
          .expiringBefore(expiringBefore)
          .belowReorder(belowReorder)
          .scrollId(scrollId)
          .pageSize(pageSize)
          .sortBy(sortBy)
          .sortDirection(sortDirection)
          .build();
      return ResponseEntity.ok(scrollService.scroll(request));
  }
  ```
- **Logic:**
  - Accepts all filter, sort, and pagination params as query parameters.
  - Builds a `ScrollRequest` DTO.
  - Delegates to the service layer for business logic.
  - Returns a paginated response with items and next cursor.

### 2. API Endpoint Logic

- **First Page:** Omit `scrollId` to start from the beginning.
- **Next Page:** Pass `scrollId` from previous response to get the next set.
- **Last Page:** When `hasNext=false`, `scrollId=null` (no more data).
- **Safety:**
  - Page size is clamped: `int safePageSize = Math.min(Math.max(request.getPageSize(), 1), 100);`
  - Corrupt or blank scrollId resets to first page.

### 3. Service Layer: `WarehouseScrollService`

- **Purpose:** Handles business logic for scroll pagination.
- **Key Code:**
  ```java
  @Transactional(readOnly = true)
  public ScrollResponse<ShipmentItemDto> scroll(ScrollRequest request) {
      ScrollPosition position = WarehouseScrollCodec.decode(request.getScrollId());
      Specification<ShipmentItem> spec = ShipmentItemSpecification.build(
          request.getKeyword(), request.getZoneCode(), request.getStatus(),
          request.getMinPrice(), request.getMaxPrice(),
          request.getExpiringBefore(), request.getBelowReorder()
      );
      Sort sort = buildSort(request.getSortBy(), request.getSortDirection());
      int safePageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
      Window<ShipmentItem> window = repository.findBy(
          spec,
          query -> query.limit(safePageSize).sortBy(sort).scroll(position)
      );
      List<ShipmentItemDto> items = window.getContent().stream()
          .map(item -> {
              ShipmentItemDto dto = modelMapper.map(item, ShipmentItemDto.class);
              dto.setNeedsReorder(item.getQuantityOnHand() <= item.getReorderLevel());
              return dto;
          }).toList();
      String nextScrollId = null;
      if (window.hasNext() && !window.isEmpty()) {
          ScrollPosition nextPosition = window.positionAt(window.size() - 1);
          nextScrollId = WarehouseScrollCodec.encode(nextPosition);
      }
      return ScrollResponse.<ShipmentItemDto>builder()
          .items(items)
          .scrollId(nextScrollId)
          .hasNext(window.hasNext())
          .pageSize(safePageSize)
          .build();
  }
  ```
- **Logic:**
  - Decodes the scroll cursor.
  - Builds a dynamic JPA specification for filtering.
  - Builds a stable sort (always appends `id` as tiebreaker unless sorting by unique key).
  - Executes a windowed query for efficient keyset pagination.
  - Maps entities to DTOs and computes derived fields.
  - Encodes the next cursor if more data exists.

#### Sort Logic:
  ```java
  private Sort buildSort(String sortBy, String direction) {
      Sort.Direction dir = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
      String field = switch (sortBy.toLowerCase()) {
          case "sku" -> "sku";
          case "itemname", "name" -> "itemName";
          case "zone", "zonecode" -> "zoneCode";
          case "price", "unitprice" -> "unitPrice";
          case "quantity", "qty" -> "quantityOnHand";
          case "totalvalue" -> "totalValue";
          case "expiry", "expirydate" -> "expiryDate";
          case "createdat", "created" -> "createdAt";
          default -> "id";
      };
      if ("id".equals(field) || "sku".equals(field)) {
          return Sort.by(dir, field);
      }
      return Sort.by(dir, field).and(Sort.by(Sort.Direction.ASC, "id"));
  }
  ```
- **Logic:**
  - Ensures stable, deterministic pagination by always sorting by a unique key last.

### 4. Util Class: `WarehouseScrollCodec`

- **Purpose:** Encodes/decodes `ScrollPosition` to/from a URL-safe Base64 JSON token.
- **Key Code:**
  ```java
  public static String encode(ScrollPosition position) {
      if (position == null || position.isInitial()) return null;
      if (position instanceof KeysetScrollPosition keyset) {
          try {
              Map<String, Object> keys = new LinkedHashMap<>(keyset.getKeys());
              String json = MAPPER.writeValueAsString(keys);
              return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
          } catch (Exception ex) {
              log.warn("Failed to encode scroll position: {}", ex.getMessage());
              return null;
          }
      }
      return null;
  }
  public static ScrollPosition decode(String token) {
      if (token == null || token.isBlank()) return ScrollPosition.keyset();
      try {
          byte[] decoded = Base64.getUrlDecoder().decode(token);
          Map<String, Object> keys = MAPPER.readValue(decoded, new TypeReference<LinkedHashMap<String, Object>>() {});
          Map<String, Object> normalized = new LinkedHashMap<>();
          keys.forEach((k, v) -> {
              if (v instanceof Integer i) normalized.put(k, i.longValue());
              else normalized.put(k, v);
          });
          return ScrollPosition.forward(normalized);
      } catch (Exception ex) {
          log.warn("Invalid scroll token received, resetting to first page. Reason: {}", ex.getMessage());
          return ScrollPosition.keyset();
      }
  }
  ```
- **Logic:**
  - Encodes the last row's key(s) as Base64 JSON for stateless, URL-safe cursors.
  - Decodes and normalizes keys, always fails gracefully (never throws).

### 5. Specification: `ShipmentItemSpecification`

- **Purpose:** Dynamically builds JPA predicates for all filter types.
- **Key Code:**
  ```java
  public static Specification<ShipmentItem> build(
      String keyword, String zoneCode, ItemStatus status,
      Double minPrice, Double maxPrice, LocalDateTime expiringBefore, Boolean belowReorder
  ) {
      return (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (keyword != null && !keyword.isBlank()) {
              String pattern = "%" + keyword.toLowerCase() + "%";
              predicates.add(cb.or(
                  cb.like(cb.lower(root.get("itemName")), pattern),
                  cb.like(cb.lower(root.get("sku")), pattern)
              ));
          }
          if (zoneCode != null && !zoneCode.isBlank()) {
              predicates.add(cb.equal(cb.lower(root.get("zoneCode")), zoneCode.toLowerCase()));
          }
          if (status != null) {
              predicates.add(cb.equal(root.get("status"), status));
          }
          if (minPrice != null) {
              predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), minPrice));
          }
          if (maxPrice != null) {
              predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), maxPrice));
          }
          if (expiringBefore != null) {
              predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), expiringBefore));
          }
          if (Boolean.TRUE.equals(belowReorder)) {
              predicates.add(cb.lessThanOrEqualTo(root.get("quantityOnHand"), root.get("reorderLevel")));
          }
          predicates.add(cb.equal(root.get("deleted"), false));
          return cb.and(predicates.toArray(new Predicate[0]));
      };
  }
  ```
- **Logic:**
  - Only non-null parameters are added as predicates (AND logic).
  - Supports partial, exact, range, and boolean filters.
  - Always excludes soft-deleted records.

---

## 🛠️ Key Implementation Details & Their Uses

### 1. Stable Sort with Tiebreaker

**Code:**
```java
// If the primary field is already the unique key, no tiebreaker needed
if ("id".equals(field) || "sku".equals(field)) {
    return Sort.by(dir, field);
// Multi-field sort: primary + id as stable tiebreaker
return Sort.by(dir, field).and(Sort.by(Sort.Direction.ASC, "id"));
```
**Use & Logic:**
- Ensures deterministic ordering for keyset pagination.
- If sorting by a non-unique field (e.g., price), appends `id` as a secondary sort key.
- Prevents duplicate or missing rows at page boundaries when multiple items share the same primary sort value.
- If sorting by a unique field (`id` or `sku`), no tiebreaker is needed.

---

### 2. Safe Page Size Clamping

**Code:**
```java
int safePageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
```
**Use & Logic:**
- Prevents abuse by clamping the requested page size between 1 and 100.
- Protects the API and database from excessive load (e.g., `pageSize=999999`).
- Ensures consistent and safe performance for all clients.

---

### 3. Next Cursor Encoding for Pagination

**Code:**
```java
// 7. Encode next cursor — null if this is the last page
String nextScrollId = null;
if (window.hasNext() && !window.isEmpty()) {
    ScrollPosition nextPosition = window.positionAt(window.size() - 1);
    nextScrollId = WarehouseScrollCodec.encode(nextPosition);
}
```
**Use & Logic:**
- After fetching a page, encodes the position of the last item as a `scrollId` for the next request.
- If there are no more items (`hasNext=false`), returns `null` for `scrollId`.
- Enables stateless, efficient, and reliable cursor-based pagination for clients.

---

### 4. WarehouseScrollCodec Utility

**Code:**
```java
public static String encode(ScrollPosition position) {
    if (position == null || position.isInitial()) {
        return null;
    }
    if (position instanceof KeysetScrollPosition keyset) {
        try {
            Map<String, Object> keys = new LinkedHashMap<>(keyset.getKeys());
            String json = MAPPER.writeValueAsString(keys);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes());
        } catch (Exception ex) {
            log.warn("Failed to encode scroll position: {}", ex.getMessage());
            return null;
        }
    }
    return null;
}

public static ScrollPosition decode(String token) {
    if (token == null || token.isBlank()) {
        return ScrollPosition.keyset(); // Initial / first page
    }
    try {
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        Map<String, Object> keys = MAPPER.readValue(
                decoded,
                new TypeReference<LinkedHashMap<String, Object>>() {}
        );
        Map<String, Object> normalized = new LinkedHashMap<>();
        keys.forEach((k, v) -> {
            if (v instanceof Integer i) {
                normalized.put(k, i.longValue());
            } else {
                normalized.put(k, v);
            }
        });
        return ScrollPosition.forward(normalized);
    } catch (Exception ex) {
        log.warn("Invalid scroll token received, resetting to first page. Reason: {}", ex.getMessage());
        return ScrollPosition.keyset();
    }
}
```
**Use & Logic:**
- **encode:** Converts a `ScrollPosition` (from Spring Data) into a URL-safe Base64 JSON string for use as a cursor (`scrollId`).
  - Returns `null` for the first page (no cursor).
  - Handles multi-key sorts by preserving key order.
  - Never throws; logs and returns `null` on error.
- **decode:** Converts a Base64 JSON string back into a `ScrollPosition`.
  - Returns the initial position for null/blank/corrupt tokens (prevents cursor-poisoning attacks).
  - Normalizes integer keys to `Long` for compatibility.
  - Never throws; logs and resets to first page on error.
- **Why needed:**
  - The raw `ScrollPosition` object cannot be sent over HTTP.
  - This utility enables stateless, secure, and robust cursor-based pagination for any client (web, mobile, etc.).

---

