package com.lacity.aipppc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lacity.aipppc.model.Parcel;
import com.lacity.aipppc.repository.PermitTypeRepository;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.JsonUtil;
import com.lacity.aipppc.service.ParcelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * Minimal Model Context Protocol (MCP) endpoint — the SOW calls for MCP "to
 * provide a universal, standardized way for AI models to securely connect to
 * external tools, databases, and data sources" (SOW 1.1). This exposes the
 * assistant's read-only knowledge tools over JSON-RPC 2.0 so an external AI agent
 * can look up parcels, permit types, and code sections, and get plain-language
 * permit guidance (Appendix 3 UC 1.7). Secured to API clients and City staff.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final ParcelService parcelService;
    private final PermitTypeRepository permitTypes;
    private final RegulatoryCodeRepository codes;
    private final JsonUtil json;
    private final ObjectMapper mapper;

    public McpController(ParcelService parcelService, PermitTypeRepository permitTypes,
                        RegulatoryCodeRepository codes, JsonUtil json, ObjectMapper mapper) {
        this.parcelService = parcelService;
        this.permitTypes = permitTypes;
        this.codes = codes;
        this.json = json;
        this.mapper = mapper;
    }

    @PostMapping
    public ObjectNode rpc(@RequestBody JsonNode req) {
        JsonNode idNode = req.get("id");
        String method = req.path("method").asText("");
        try {
            JsonNode result = switch (method) {
                case "initialize" -> initialize();
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(req.path("params"));
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            return envelope(idNode, result, null);
        } catch (Exception e) {
            ObjectNode err = mapper.createObjectNode();
            err.put("code", -32603);
            err.put("message", e.getMessage());
            return envelope(idNode, null, err);
        }
    }

    private ObjectNode initialize() {
        ObjectNode r = mapper.createObjectNode();
        r.put("protocolVersion", "2024-11-05");
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", "aip-ppc-mcp");
        info.put("version", "0.1.0");
        r.putObject("capabilities").putObject("tools");
        return r;
    }

    private ObjectNode toolsList() {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode tools = r.putArray("tools");
        tools.add(tool("lookup_parcel", "Look up LA parcel zoning, overlays, and hazard zones by address or APN.",
            "query", "Address or APN"));
        tools.add(tool("list_permit_types", "List available permit types and categories.", null, null));
        tools.add(tool("identify_permit", "Suggest a likely permit type from a plain-language project description.",
            "description", "Plain-language description of the work"));
        tools.add(tool("search_codes", "Search the LAMC / Title 24 / clearance knowledgebase.",
            "query", "Keywords"));
        return r;
    }

    private ObjectNode tool(String name, String desc, String argName, String argDesc) {
        ObjectNode t = mapper.createObjectNode();
        t.put("name", name);
        t.put("description", desc);
        ObjectNode schema = t.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        if (argName != null) {
            props.putObject(argName).put("type", "string").put("description", argDesc);
            schema.putArray("required").add(argName);
        }
        return t;
    }

    private ObjectNode toolsCall(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        String text = switch (name) {
            case "lookup_parcel" -> lookupParcel(args.path("query").asText(""));
            case "list_permit_types" -> listPermitTypes();
            case "identify_permit" -> identifyPermit(args.path("description").asText(""));
            case "search_codes" -> searchCodes(args.path("query").asText(""));
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
        ObjectNode r = mapper.createObjectNode();
        ArrayNode content = r.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        return r;
    }

    private String lookupParcel(String query) {
        return parcelService.search(query).stream().findFirst().map(p ->
            "APN " + p.getApn() + " | " + p.getAddress() + " | Zone " + p.getZone()
                + " | Overlays: " + json.toStringList(p.getOverlaysJson())
                + " | Hazards: " + json.toStringList(p.getHazardZonesJson())
                + " | Council District " + p.getCouncilDistrict())
            .orElse("No parcel found for: " + query);
    }

    private String listPermitTypes() {
        return permitTypes.findByActiveTrueOrderByName().stream()
            .map(p -> p.getCode() + " — " + p.getName() + " (" + p.getCategory() + ")")
            .reduce((a, b) -> a + "\n" + b).orElse("No permit types configured.");
    }

    /** Plain-language → permit type (Appendix 3 UC 1.7). Simple keyword heuristic. */
    private String identifyPermit(String description) {
        String d = description.toLowerCase(Locale.ROOT);
        String code;
        if (d.contains("ev charger") || d.contains("solar") || d.contains("battery")) code = "SOLAR_EV";
        else if (d.contains("sign")) code = "SIGN";
        else if (d.contains("adu") || d.contains("accessory dwelling") || d.contains("granny flat")) code = "ADU";
        else if (d.contains("tenant improvement") || d.contains("ti ") || d.contains("remodel of")) code = "COMMERCIAL_TI";
        else if (d.contains("apartment") || d.contains("multi-family") || d.contains("condo") || d.contains("units")) code = "MULTIFAMILY_NEW";
        else if (d.contains("commercial") || d.contains("retail") || d.contains("office") || d.contains("restaurant")) code = "COMMERCIAL_NEW";
        else if (d.contains("addition") || d.contains("remodel") || d.contains("deck") || d.contains("window")) code = "SFD_ADDITION";
        else code = "SFD_NEW";
        return permitTypes.findByCode(code).map(p ->
            "Likely permit type: " + p.getCode() + " — " + p.getName()
                + ". Note: advisory suggestion; confirm with LADBS. Final determination is made by City staff.")
            .orElse("Suggested: " + code);
    }

    private String searchCodes(String query) {
        return codes.search(query).stream().limit(6)
            .map(c -> c.getCodeType() + " " + c.getSection() + " — " + c.getTitle()
                + (c.getUrl() != null ? " (" + c.getUrl() + ")" : ""))
            .reduce((a, b) -> a + "\n" + b).orElse("No code sections found for: " + query);
    }

    private ObjectNode envelope(JsonNode id, JsonNode result, ObjectNode error) {
        ObjectNode env = mapper.createObjectNode();
        env.put("jsonrpc", "2.0");
        if (id != null) env.set("id", id); else env.putNull("id");
        if (error != null) env.set("error", error);
        else env.set("result", result == null ? mapper.nullNode() : result);
        return env;
    }
}
