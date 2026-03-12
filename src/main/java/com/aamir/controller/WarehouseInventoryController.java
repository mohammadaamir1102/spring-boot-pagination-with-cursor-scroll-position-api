package com.aamir.controller;

// WarehouseInventoryController.java
import com.aamir.constant.ItemStatus;
import com.aamir.dto.req.ScrollRequest;
import com.aamir.dto.req.ShipmentItemDto;
import com.aamir.dto.res.ScrollResponse;
import com.aamir.service.WarehouseScrollService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/warehouse/inventory")
@RequiredArgsConstructor
public class WarehouseInventoryController {

    private final WarehouseScrollService scrollService;

    /**
     * GET /api/v1/warehouse/inventory/scroll
     *
     * Cursor-based (keyset) pagination for warehouse inventory.
     * Designed for mobile apps, infinite-scroll UIs, and large export jobs.
     *
     * Usage:
     *   1st page: call without scrollId
     *   Next page: pass scrollId from previous response
     *   Last page: response.hasNext == false, scrollId == null
     */
    @GetMapping("/scroll")
    public ResponseEntity<@NonNull ScrollResponse<ShipmentItemDto>> scroll(

            // --- Search & Filter ---
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,

            // ISO 8601 datetime: 2025-12-31T23:59:59
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime expiringBefore,

            @RequestParam(required = false) Boolean belowReorder,

            // --- Scroll cursor ---
            @RequestParam(required = false) String scrollId,

            // --- Page config ---
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
}
