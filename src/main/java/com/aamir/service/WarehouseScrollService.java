package com.aamir.service;

// WarehouseScrollService.java

import com.aamir.dto.req.ScrollRequest;
import com.aamir.dto.req.ShipmentItemDto;
import com.aamir.dto.res.ScrollResponse;
import com.aamir.entity.ShipmentItem;
import com.aamir.repo.ShipmentItemRepository;
import com.aamir.repo.spec.ShipmentItemSpecification;
import com.aamir.util.WarehouseScrollCodec;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseScrollService {

    private final ShipmentItemRepository repository;
    private final ModelMapper modelMapper;

    /**
     * Fetches a scroll page of shipment items.
     *
     * Why @Transactional(readOnly = true)?
     *   - Prevents Hibernate from tracking entity state (no dirty checking overhead)
     *   - Signals the DB driver to use read replicas if configured
     *   - 20-30% faster for large result sets
     */
    @Transactional(readOnly = true)
    public ScrollResponse<ShipmentItemDto> scroll(ScrollRequest request) {

        // 1. Decode the cursor (or get initial position for first page)
        ScrollPosition position = WarehouseScrollCodec.decode(request.getScrollId());

        // 2. Build filter specification
        Specification<@NonNull ShipmentItem> spec = ShipmentItemSpecification.build(
                request.getKeyword(),
                request.getZoneCode(),
                request.getStatus(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getExpiringBefore(),
                request.getBelowReorder()
        );

        // 3. Build sort — always append 'id' as the final tiebreaker.
        //    Without a unique tiebreaker, keyset pagination produces non-deterministic
        //    results when two rows share the same primary sort value (e.g., same price).
        Sort sort = buildSort(request.getSortBy(), request.getSortDirection());

        // 4. Clamp page size to prevent abuse (e.g., pageSize=999999)
        int safePageSize = Math.min(Math.max(request.getPageSize(), 1), 100);

        log.debug("Scroll query: sortBy={}, direction={}, pageSize={}, hasScrollId={}",
                request.getSortBy(), request.getSortDirection(), safePageSize,
                request.getScrollId() != null);

        // 5. Execute Window query — this is the core Spring Data Scroll API call
        Window<@NonNull ShipmentItem> window = repository.findBy(
                spec,
                query -> query
                        .limit(safePageSize)
                        .sortBy(sort)
                        .scroll(position)
        );

        // 6. Map entities to DTOs
        List<ShipmentItemDto> items = window.getContent()
                .stream()
                .map(item -> {
                    ShipmentItemDto dto = modelMapper.map(item, ShipmentItemDto.class);
                    // Compute derived field that doesn't exist on entity
                    dto.setNeedsReorder(item.getQuantityOnHand() <= item.getReorderLevel());
                    return dto;
                })
                .toList();

        // 7. Encode next cursor — null if this is the last page
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

    /**
     * Builds a stable Sort for keyset pagination.
     *
     * CRITICAL RULE: The last sort key MUST be unique (id/sku).
     * If it isn't, the DB can't determine a deterministic "after this row" boundary,
     * and you'll get duplicates or skips at page boundaries.
     */
    private Sort buildSort(String sortBy, String direction) {
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        String field = switch (sortBy.toLowerCase()) {
            case "sku"              -> "sku";
            case "itemname", "name" -> "itemName";
            case "zone", "zonecode" -> "zoneCode";
            case "price", "unitprice" -> "unitPrice";
            case "quantity", "qty"  -> "quantityOnHand";
            case "totalvalue"       -> "totalValue";
            case "expiry", "expirydate" -> "expiryDate";
            case "createdat", "created" -> "createdAt";
            default                 -> "id";
        };

        // If the primary field is already the unique key, no tiebreaker needed
        if ("id".equals(field) || "sku".equals(field)) {
            return Sort.by(dir, field);
        }

        // Multi-field sort: primary + id as stable tiebreaker
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
