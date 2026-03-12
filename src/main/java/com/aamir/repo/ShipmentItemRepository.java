package com.aamir.repo;


import com.aamir.entity.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long>, JpaSpecificationExecutor<ShipmentItem> {

    // JpaSpecificationExecutor gives us:
    //   findBy(Specification, FluentQuery) — used by the Window/Scroll API
    //   findAll(Specification, Pageable)  — used by standard pagination
    //
    // Both interfaces needed so the same repo handles offset AND scroll pagination
}
