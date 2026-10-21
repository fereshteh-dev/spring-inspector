package com.acme.customers;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService {

    @Transactional(readOnly = true)
    public List<Customer> findByCountry(String country) {
        return List.of();
    }

    @Transactional
    public void register(Customer customer) {}

    @Transactional
    public Customer getOrCreate(String email) {
        return null;
    }
}
