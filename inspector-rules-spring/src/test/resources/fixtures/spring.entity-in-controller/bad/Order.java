package com.acme.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;

@Entity
class Order {

    @Id
    Long id;

    @OneToMany(mappedBy = "order")
    List<OrderLine> lines;

    @ManyToOne(fetch = FetchType.LAZY)
    Customer customer;

    @ManyToOne
    Warehouse warehouse;

    List<OrderLine> getLines() {
        return lines;
    }

    Customer getCustomer() {
        return customer;
    }

    Warehouse getWarehouse() {
        return warehouse;
    }

    Long getId() {
        return id;
    }
}
