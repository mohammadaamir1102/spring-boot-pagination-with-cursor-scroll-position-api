package com.aamir.repo.spec;

// ShipmentItemSpecification.java
// Builder pattern for dynamic JPA predicates.
// Problem solved: avoids N different repository methods for N filter combinations.
// A warehouse UI might filter by zone + status + price range simultaneously.

import com.aamir.constant.ItemStatus;
import com.aamir.entity.ShipmentItem;
import jakarta.persistence.criteria.Predicate;
import lombok.NonNull;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ShipmentItemSpecification {

    private ShipmentItemSpecification() {
        // Utility class — no instantiation
    }

    /**
     * Builds a composite Specification from optional filter parameters.
     * Only non-null parameters are added as predicates (AND logic).
     *
     * @param keyword     Partial match on itemName or SKU
     * @param zoneCode    Exact match on warehouse zone
     * @param status      Exact match on item status (AVAILABLE, RESERVED, etc.)
     * @param minPrice    Minimum unit price (inclusive)
     * @param maxPrice    Maximum unit price (inclusive)
     * @param expiringBefore  Items expiring before this datetime (FEFO/FIFO support)
     * @param belowReorder    If true, only items where quantityOnHand <= reorderLevel
     */
    public static Specification<@NonNull ShipmentItem> build(
            String keyword,
            String zoneCode,
            ItemStatus status,
            Double minPrice,
            Double maxPrice,
            LocalDateTime expiringBefore,
            Boolean belowReorder
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Full-text-like search across itemName and SKU
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("itemName")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern)
                ));
            }

            // Zone filter — case-insensitive exact match
            if (zoneCode != null && !zoneCode.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("zoneCode")),
                        zoneCode.toLowerCase()
                ));
            }

            // Status enum filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Price range
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), maxPrice));
            }

            // FEFO: First Expired First Out — find items expiring soon
            if (expiringBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), expiringBefore));
            }

            // Reorder alert: quantity <= reorder level
            if (Boolean.TRUE.equals(belowReorder)) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("quantityOnHand"),
                        root.get("reorderLevel")
                ));
            }

            // Always exclude soft-deleted records
            predicates.add(cb.equal(root.get("deleted"), false));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
