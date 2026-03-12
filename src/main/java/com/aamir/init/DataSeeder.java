package com.aamir.init;

// DataSeeder.java
// @PostConstruct fires ONCE after the Spring Bean is fully initialized
// and all dependencies (@Autowired / constructor injection) are injected.
//
// Problem solved: no CommandLineRunner, no Flyway, no SQL scripts —
// just pure Java that runs automatically on startup and fills the table.

import com.aamir.constant.ItemStatus;
import com.aamir.entity.ShipmentItem;
import com.aamir.repo.ShipmentItemRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final ShipmentItemRepository repository;

    private static final int    TOTAL_RECORDS = 200;
    private static final int    BATCH_SIZE    = 50;
    private static final long   RANDOM_SEED   = 42L;

    /**
     * @PostConstruct fires automatically after Spring injects
     * ShipmentItemRepository into this bean.
     *
     * Execution order:
     *   1. Spring creates DataSeeder bean
     *   2. Spring injects ShipmentItemRepository
     *   3. @PostConstruct fires → saveData() runs
     *   4. App starts serving requests
     */



//    @PostConstruct
//    @Transactional
    public void saveData() {

        // Guard: skip if table already has data — prevents duplicates on hot-reload
        if (repository.count() > 0) {
            log.info("[@PostConstruct] Table already has {} records — skipping seed.",
                    repository.count());
            return;
        }

        log.info("[@PostConstruct] Seeding {} ShipmentItem records...", TOTAL_RECORDS);
        long start = System.currentTimeMillis();

        Random random = new Random(RANDOM_SEED);
        List<ShipmentItem> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 1; i <= TOTAL_RECORDS; i++) {

            ShipmentItem item = buildShipmentItem(i, random);
            batch.add(item);

            // Flush every 50 records to avoid memory buildup
            if (batch.size() == BATCH_SIZE) {
                repository.saveAll(batch);
                batch.clear();
                log.debug("[@PostConstruct] Saved batch — progress: {}/{}", i, TOTAL_RECORDS);
            }
        }

        // Save the last remaining partial batch
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[@PostConstruct] ✅ Done! {} records saved in {}ms.", TOTAL_RECORDS, elapsed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE BUILDER
    // ─────────────────────────────────────────────────────────────────────────

    private ShipmentItem buildShipmentItem(int index, Random random) {

        int    qty      = random.nextInt(500) + 1;
        double price    = Math.round((random.nextDouble() * 995 + 5) * 100.0) / 100.0;
        double total    = Math.round(qty * price * 100.0) / 100.0;

        ShipmentItem item = new ShipmentItem();
        item.setSku(buildSku(index));
        item.setItemName(ITEM_NAMES[random.nextInt(ITEM_NAMES.length)] + " #" + index);
        item.setZoneCode(ZONES[random.nextInt(ZONES.length)]);
        item.setStatus(STATUSES[random.nextInt(STATUSES.length)]);
        item.setQuantityOnHand(qty);
        item.setReorderLevel(random.nextInt(50) + 5);
        item.setUnitPrice(price);
        item.setTotalValue(total);
        item.setDeleted(false);

        // 30% of items get an expiry date (perishables / pharma)
        if (random.nextInt(10) < 3) {
            item.setExpiryDate(LocalDateTime.now().plusDays(random.nextInt(365) + 1));
        }

        return item;
    }

    // SKU format: WH-A001 → WH-H200
    private String buildSku(int index) {
        char letter = (char) ('A' + ((index - 1) / 26) % 8);
        int  num    = ((index - 1) % 26) + 1;
        return String.format("WH-%c%03d", letter, num);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATA POOLS
    // ─────────────────────────────────────────────────────────────────────────

    private static final String[] ITEM_NAMES = {
            "Industrial Bearing", "Hydraulic Pump",    "Steel Bolt M12",
            "Circuit Breaker",    "Conveyor Belt",      "Safety Gloves",
            "PVC Pipe 2in",       "Oxygen Cylinder",    "Lithium Battery Pack",
            "Forklift Tire",      "Sensor Module",      "Power Cable 10m",
            "Heat Exchanger",     "Pressure Valve",     "Pallet Wrap Roll",
            "Solvent Cleaner",    "Welding Rod",        "Air Filter Unit",
            "Emergency Light",    "Label Printer Ribbon"
    };

    private static final String[] ZONES = {
            "A1", "A2", "B1", "B2",
            "COLD-STORAGE", "HAZMAT", "QUARANTINE", "DISPATCH"
    };

    private static final ItemStatus[] STATUSES = {
            ItemStatus.AVAILABLE,   // weighted 3x
            ItemStatus.AVAILABLE,
            ItemStatus.AVAILABLE,
            ItemStatus.RESERVED,
            ItemStatus.IN_TRANSIT,
            ItemStatus.DAMAGED,
            ItemStatus.EXPIRED,
            ItemStatus.QUARANTINE
    };
}
