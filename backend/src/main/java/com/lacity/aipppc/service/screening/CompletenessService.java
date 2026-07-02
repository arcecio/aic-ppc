package com.lacity.aipppc.service.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.Finding;
import com.lacity.aipppc.model.PermitType;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.FindingSource;
import com.lacity.aipppc.model.enums.ScanStatus;
import com.lacity.aipppc.model.enums.Severity;
import com.lacity.aipppc.service.JsonUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Submission-completeness validation (SOW 2.2.2). Compares uploaded documents
 * against the permit type's required-document checklist and flags anything
 * missing, plus common submission issues (no documents, files that failed the
 * security scan). Completeness findings are the main driver of the "Incomplete"
 * readiness status; the system never blocks submission, it advises.
 */
@Service
public class CompletenessService {

    private final JsonUtil json;

    public CompletenessService(JsonUtil json) {
        this.json = json;
    }

    public record Result(List<Finding> findings, int requiredCount, int presentRequiredCount,
                         List<String> missingLabels) {}

    public Result evaluate(PreCheckRun run, PermitType permitType, List<Document> documents) {
        List<Finding> findings = new ArrayList<>();
        List<String> missingLabels = new ArrayList<>();

        List<String> presentCategories = documents.stream()
            .filter(d -> d.getScanStatus() == ScanStatus.PASSED && d.getDocCategory() != null)
            .map(Document::getDocCategory).distinct().toList();

        // Files that failed the security scan (SOW 2.2.11).
        for (Document d : documents) {
            if (d.getScanStatus() == ScanStatus.FAILED || d.getScanStatus() == ScanStatus.QUARANTINED) {
                findings.add(Finding.builder()
                    .run(run).category(FindingCategory.COMPLETENESS).severity(Severity.BLOCKING)
                    .title("File failed security scan: " + d.getOriginalName())
                    .description(d.getScanDetail() == null ? "The file did not pass the automated security scan."
                        : d.getScanDetail())
                    .recommendation("Replace the file with a clean copy in an accepted format and re-upload.")
                    .source(FindingSource.COMPLETENESS).confidence(100)
                    .ruleCode("COMPLETENESS-SCAN")
                    .build());
            }
        }

        if (documents.isEmpty()) {
            findings.add(Finding.builder()
                .run(run).category(FindingCategory.COMPLETENESS).severity(Severity.BLOCKING)
                .title("No documents uploaded")
                .description("No plans or supporting documents have been uploaded for this project.")
                .recommendation("Upload the required plans and supporting documents before submitting.")
                .source(FindingSource.COMPLETENESS).confidence(100)
                .ruleCode("COMPLETENESS-EMPTY")
                .build());
        }

        int requiredCount = 0;
        int presentRequiredCount = 0;
        JsonNode docs = permitType == null ? null : json.readTree(permitType.getRequiredDocsJson());
        if (docs != null && docs.isArray()) {
            for (JsonNode d : docs) {
                boolean required = d.path("required").asBoolean(false);
                if (!required) continue;
                requiredCount++;
                String key = d.path("docKey").asText("");
                String label = d.path("label").asText(key);
                if (presentCategories.contains(key)) {
                    presentRequiredCount++;
                } else {
                    missingLabels.add(label);
                    findings.add(Finding.builder()
                        .run(run).category(FindingCategory.COMPLETENESS).severity(Severity.BLOCKING)
                        .title("Missing required document: " + label)
                        .description("The permit type \"" + permitType.getName() + "\" requires \"" + label
                            + "\", which was not found among the uploaded and scan-passed documents.")
                        .triggeringCondition("Required document key \"" + key + "\" has no matching upload.")
                        .recommendation("Upload \"" + label + "\" and tag it with the matching document category.")
                        .source(FindingSource.COMPLETENESS).confidence(100)
                        .ruleCode("COMPLETENESS-MISSING-" + key.toUpperCase())
                        .build());
                }
            }
        }

        return new Result(findings, requiredCount, presentRequiredCount, missingLabels);
    }
}
