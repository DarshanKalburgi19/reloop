package com.reloop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Reference table of demo cities (searchable by name/state) used by addresses,
 * stores and the repairer map. Kept in product-service because addresses and
 * stores live here.
 */
@Entity
@Table(name = "cities", indexes = {
    @Index(name = "ix_cities_name", columnList = "name"),
    @Index(name = "ix_cities_state", columnList = "state")
})
public class City extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "state", nullable = false, length = 120)
    private String state;

    @Column(name = "country", nullable = false, length = 80)
    private String country = "India";

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    public City() {
    }

    public City(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
