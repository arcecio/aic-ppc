package com.lacity.aipppc.service;

import com.lacity.aipppc.model.Parcel;
import com.lacity.aipppc.repository.ParcelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves parcels from the GIS stand-in (SOW 2.2.1 — validate addresses against
 * City records / GIS such as ZIMAS &amp; NavigateLA; Appendix 3 UC 1.1, §3.4).
 * Matches by APN first, then normalized address, then a loose prefix match so a
 * partial street address still resolves during intake.
 */
@Service
public class ParcelService {

    private final ParcelRepository parcelRepository;

    public ParcelService(ParcelRepository parcelRepository) {
        this.parcelRepository = parcelRepository;
    }

    public List<Parcel> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return parcelRepository.search(query.trim());
    }

    public Optional<Parcel> findByApn(String apn) {
        return apn == null ? Optional.empty() : parcelRepository.findByApn(apn.trim());
    }

    /** Best-effort resolution used during intake: APN, exact normalized address, then prefix. */
    public Optional<Parcel> resolve(String apn, String address) {
        if (apn != null && !apn.isBlank()) {
            Optional<Parcel> byApn = parcelRepository.findByApn(apn.trim());
            if (byApn.isPresent()) return byApn;
        }
        if (address != null && !address.isBlank()) {
            String norm = normalize(address);
            Optional<Parcel> exact = parcelRepository.findByAddressNormalized(norm);
            if (exact.isPresent()) return exact;
            // Match on the leading street number + name (first ~3 tokens).
            String[] tokens = norm.split(" ");
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, Math.min(3, tokens.length)));
            List<Parcel> hits = parcelRepository.search(prefix);
            if (!hits.isEmpty()) return Optional.of(hits.get(0));
        }
        return Optional.empty();
    }

    public static String normalize(String address) {
        if (address == null) return "";
        return address.toUpperCase(Locale.ROOT).replaceAll("[.,]", " ").replaceAll("\\s+", " ").trim();
    }
}
