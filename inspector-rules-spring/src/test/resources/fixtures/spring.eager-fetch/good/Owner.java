package com.acme.owners;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;

@Entity
class Owner {

    @Id
    private Long id;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Pet> pets;

    @ManyToOne(fetch = FetchType.EAGER)
    private City city;
}
