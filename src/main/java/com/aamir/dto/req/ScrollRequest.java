package com.aamir.dto.req;

// ScrollRequest.java
// Encapsulates all incoming scroll request parameters.
// Problem solved: avoids bloated @RequestParam lists in the controller.
import com.aamir.constant.ItemStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrollRequest {

    // --- Filter params ---
    private String keyword;
    private String zoneCode;
    private ItemStatus status;
    private Double minPrice;
    private Double maxPrice;
    private LocalDateTime expiringBefore;
    private Boolean belowReorder;

    // --- Scroll params ---
    private String scrollId;                    // null = first page
    private int pageSize = 20;                  // default batch size
    private String sortBy = "id";               // default sort field
    private String sortDirection = "ASC";       // ASC recommended for keyset (predictable)
}
