package com.lacity.aipppc.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A parcel record standing in for the City's authoritative GIS sources (ZIMAS /
 * NavigateLA). Address validation and zoning/overlay/hazard lookup during intake
 * resolve against these rows (SOW 2.2.1; Appendix 3 UC 1.1, §3.4). Overlays and
 * hazard zones are stored as JSON arrays of strings.
 */
@Entity
@Table(name = "parcels")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Assessor Parcel Number — the canonical parcel key. */
    @Column(nullable = false, unique = true, length = 32)
    private String apn;

    @Column(nullable = false)
    private String address;

    /** Normalized (upper, single-spaced) address for lookup. */
    @Column(name = "address_normalized", nullable = false)
    private String addressNormalized;

    @Column(length = 32)
    private String zone;

    @Column(name = "general_plan_land_use")
    private String generalPlanLandUse;

    /** JSON array, e.g. ["Hillside","Coastal Zone","Specific Plan: Venice"]. */
    @Column(name = "overlays_json", columnDefinition = "text")
    private String overlaysJson;

    /** JSON array, e.g. ["Methane","Liquefaction","Very High Fire Hazard Severity Zone"]. */
    @Column(name = "hazard_zones_json", columnDefinition = "text")
    private String hazardZonesJson;

    @Column(name = "council_district")
    private Integer councilDistrict;

    @Column(name = "community_plan_area")
    private String communityPlanArea;

    private Double latitude;
    private Double longitude;

    public Parcel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getApn() { return apn; }
    public void setApn(String apn) { this.apn = apn; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAddressNormalized() { return addressNormalized; }
    public void setAddressNormalized(String addressNormalized) { this.addressNormalized = addressNormalized; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getGeneralPlanLandUse() { return generalPlanLandUse; }
    public void setGeneralPlanLandUse(String generalPlanLandUse) { this.generalPlanLandUse = generalPlanLandUse; }
    public String getOverlaysJson() { return overlaysJson; }
    public void setOverlaysJson(String overlaysJson) { this.overlaysJson = overlaysJson; }
    public String getHazardZonesJson() { return hazardZonesJson; }
    public void setHazardZonesJson(String hazardZonesJson) { this.hazardZonesJson = hazardZonesJson; }
    public Integer getCouncilDistrict() { return councilDistrict; }
    public void setCouncilDistrict(Integer councilDistrict) { this.councilDistrict = councilDistrict; }
    public String getCommunityPlanArea() { return communityPlanArea; }
    public void setCommunityPlanArea(String communityPlanArea) { this.communityPlanArea = communityPlanArea; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Parcel p = new Parcel();
        public Builder apn(String v) { p.apn = v; return this; }
        public Builder address(String v) { p.address = v; return this; }
        public Builder addressNormalized(String v) { p.addressNormalized = v; return this; }
        public Builder zone(String v) { p.zone = v; return this; }
        public Builder generalPlanLandUse(String v) { p.generalPlanLandUse = v; return this; }
        public Builder overlaysJson(String v) { p.overlaysJson = v; return this; }
        public Builder hazardZonesJson(String v) { p.hazardZonesJson = v; return this; }
        public Builder councilDistrict(Integer v) { p.councilDistrict = v; return this; }
        public Builder communityPlanArea(String v) { p.communityPlanArea = v; return this; }
        public Builder latitude(Double v) { p.latitude = v; return this; }
        public Builder longitude(Double v) { p.longitude = v; return this; }
        public Parcel build() { return p; }
    }
}
