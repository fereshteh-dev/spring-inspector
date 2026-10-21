package com.acme.shipping;

import java.util.List;

class Shipping {

    int cost(List<Parcel> parcels, boolean express) {
        if (!express) {
            return 0;
        }
        int total = 0;
        for (Parcel parcel : parcels) {
            total += fragileSurcharge(parcel);
        }
        return total;
    }

    String zone(int code) {
        if (code < 10) {
            return "local";
        } else if (code < 20) {
            return "regional";
        } else if (code < 30) {
            return "national";
        } else if (code < 40) {
            return "continental";
        } else {
            return "world";
        }
    }

    private int fragileSurcharge(Parcel parcel) {
        return (int) parcel.items().stream().filter(Item::fragile).count() * 10;
    }
}
