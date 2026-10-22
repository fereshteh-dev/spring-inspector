package com.acme.owners;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import java.util.Set;

@Entity
class Owner {

    @Id
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Pet> pets;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> phoneNumbers;
}
