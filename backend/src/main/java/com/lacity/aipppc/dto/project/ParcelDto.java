package com.lacity.aipppc.dto.project;

import com.lacity.aipppc.model.Parcel;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record ParcelDto(
    UUID id,
    String apn,
    String address,
    String zone,
    String generalPlanLandUse,
    List<String> overlays,
    List<String> hazardZones,
    Integer councilDistrict,
    String communityPlanArea,
    Double latitude,
    Double longitude
) {
    public static ParcelDto from(Parcel p, Function<String, List<String>> jsonToList) {
        if (p == null) return null;
        return new ParcelDto(p.getId(), p.getApn(), p.getAddress(), p.getZone(),
            p.getGeneralPlanLandUse(), jsonToList.apply(p.getOverlaysJson()),
            jsonToList.apply(p.getHazardZonesJson()), p.getCouncilDistrict(),
            p.getCommunityPlanArea(), p.getLatitude(), p.getLongitude());
    }
}
