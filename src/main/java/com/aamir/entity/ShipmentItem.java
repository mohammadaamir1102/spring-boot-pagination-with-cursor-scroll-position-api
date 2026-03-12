package com.aamir.entity;

// ShipmentItem.java
// Represents a stock line item in the warehouse —
// A warehouse can have millions of these, making scroll pagination critical.

import com.aamir.autitable.AuditableEntity;
import com.aamir.constant.ItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLDelete;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "wh_shipment_items",
        indexes = {
                // Composite index: most queries filter by warehouse zone + status together
                @Index(name = "idx_zone_status", columnList = "zone_code, status"),

                // Covering index for price-range queries — avoids table scan
                @Index(name = "idx_item_unit_price", columnList = "unit_price"),

                // Needed for scroll keyset — must be unique & indexed
                @Index(name = "idx_item_sku", columnList = "sku", unique = true),

                // Composite for sort stability in scroll: (unit_price, sku)
                @Index(name = "idx_price_sku", columnList = "unit_price, sku"),

                // For expiry-based queries (FIFO/FEFO warehouse logic)
                @Index(name = "idx_expiry_date", columnList = "expiry_date")
        }
)
// Soft-delete: mark as deleted instead of physically removing —
// physical deletes would break scroll position (keyset would miss items)
@SQLDelete(sql = "UPDATE wh_shipment_items SET deleted = true WHERE id = ?")  // Hibernate will execute this instead of a DELETE statement
@FilterDef(name = "activeItemsFilter", parameters = @ParamDef(name = "deleted", type = Boolean.class)) // Define a filter to exclude deleted items from queries by default
@Filter(name = "activeItemsFilter", condition = "deleted = :deleted") // Apply the filter to all queries on this entity
@Getter
@Setter
public class ShipmentItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SKU is the business key — used as secondary sort key for stable scrolling
    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false)
    private String itemName;

    // Zone code: A1, B2, COLD-STORAGE, HAZMAT, etc.
    @Column(name = "zone_code", nullable = false, length = 20)
    private String zoneCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status; // AVAILABLE, RESERVED, DAMAGED, EXPIRED, IN_TRANSIT

    @Column(nullable = false)
    private Integer quantityOnHand;

    @Column(nullable = false)
    private Integer reorderLevel; // Alert threshold

    @Column(nullable = false, precision = 2)
    private Double unitPrice;

    @Column(nullable = false)
    private Double totalValue; // quantityOnHand * unitPrice — denormalized for reporting

    private LocalDateTime expiryDate;

    // Soft delete flag — never physically delete warehouse records
    @Column(nullable = false)
    private boolean deleted = false;
}
