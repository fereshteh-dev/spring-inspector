package com.acme.customers;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService {

    @Transactional
    public List<Customer> findByCountry(String country) {
        return List.of();
    }

    @Transactional
    public long count() {
        return 0;
    }
}
