package com.acme.shipping;

import java.util.List;

class Shipping {

    int cost(List<Parcel> parcels, boolean express) {
        int total = 0;
        for (Parcel parcel : parcels) {
            if (parcel.weight() > 0) {
                if (express) {
                    for (Item item : parcel.items()) {
                        if (item.fragile()) {
                            total += 10;
                        }
                    }
                }
            }
        }
        return total;
    }
}
