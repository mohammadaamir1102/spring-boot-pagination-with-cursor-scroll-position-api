package com.aamir.dto.req;

import com.aamir.constant.ItemStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentItemDto {
    private Long id;
    private String sku;
    private String itemName;
    private String zoneCode;
    private ItemStatus status;
    private Integer quantityOnHand;
    private Integer reorderLevel;
    private Double unitPrice;
    private Double totalValue;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed convenience field — avoids front-end calculation
    private boolean needsReorder;
}
