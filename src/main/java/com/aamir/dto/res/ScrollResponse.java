package com.aamir.dto.res;

// ScrollResponse.java
// Generic wrapper — reusable across any entity that needs scroll pagination.
// Problem solved: front-end only needs to check hasNext and pass scrollId
// back to get the next batch. No page math needed.

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResponse<T> {

    private List<T> items;

    // Opaque token — client treats it as a black box and passes it back as-is.
    // Null means: you're on the last page, no more data.
    private String scrollId;

    private boolean hasNext;

    // Echo back the requested page size for client validation
    private int pageSize;

    // Total matching items (expensive COUNT query — optional, skip for pure infinite scroll)
    private Long totalMatchingItems;
}
