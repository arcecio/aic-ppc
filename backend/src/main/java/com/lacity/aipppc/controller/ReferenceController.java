package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.project.ParcelDto;
import com.lacity.aipppc.dto.reference.PermitTypeDto;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.repository.PermitTypeRepository;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.JsonUtil;
import com.lacity.aipppc.service.ParcelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only reference data for the intake UI: permit types (with dynamic form +
 * required-doc schema), parcel/GIS lookup, and regulatory-code search
 * (SOW 2.2.1; Appendix 3 UC 1.1).
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final PermitTypeRepository permitTypes;
    private final RegulatoryCodeRepository codes;
    private final ParcelService parcelService;
    private final JsonUtil json;

    public ReferenceController(PermitTypeRepository permitTypes,
                              RegulatoryCodeRepository codes,
                              ParcelService parcelService,
                              JsonUtil json) {
        this.permitTypes = permitTypes;
        this.codes = codes;
        this.parcelService = parcelService;
        this.json = json;
    }

    @GetMapping("/permit-types")
    public List<PermitTypeDto> permitTypes() {
        return permitTypes.findByActiveTrueOrderByName().stream()
            .map(p -> PermitTypeDto.from(p, json::readTree)).toList();
    }

    @GetMapping("/permit-types/{code}")
    public PermitTypeDto permitType(@PathVariable String code) {
        return permitTypes.findByCode(code)
            .map(p -> PermitTypeDto.from(p, json::readTree))
            .orElseThrow(() -> ApiException.notFound("Permit type not found: " + code));
    }

    @GetMapping("/parcels")
    public List<ParcelDto> searchParcels(@RequestParam("q") String q) {
        return parcelService.search(q).stream()
            .map(p -> ParcelDto.from(p, json::toStringList)).toList();
    }

    @GetMapping("/codes")
    public List<Object> searchCodes(@RequestParam("q") String q) {
        return codes.search(q).stream().map(c -> (Object) new CodeResult(
            c.getExternalId(), c.getJurisdiction().name(), c.getCodeType(), c.getSection(),
            c.getTitle(), c.getSummary(), c.getUrl(), c.getVersion())).toList();
    }

    public record CodeResult(String externalId, String jurisdiction, String codeType, String section,
                             String title, String summary, String url, String version) {}
}
