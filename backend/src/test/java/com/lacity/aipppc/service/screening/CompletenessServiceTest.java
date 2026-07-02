package com.lacity.aipppc.service.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.PermitType;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.enums.PermitCategory;
import com.lacity.aipppc.model.enums.ScanStatus;
import com.lacity.aipppc.model.enums.Severity;
import com.lacity.aipppc.service.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompletenessServiceTest {

    private final CompletenessService service = new CompletenessService(new JsonUtil(new ObjectMapper()));

    private PermitType permitType() {
        return PermitType.builder().code("SFD_NEW").name("New SFD").category(PermitCategory.RESIDENTIAL)
            .requiredDocsJson("[{\"docKey\":\"architectural_plans\",\"label\":\"Architectural plans\",\"required\":true},"
                + "{\"docKey\":\"structural_plans\",\"label\":\"Structural plans\",\"required\":true},"
                + "{\"docKey\":\"soils_report\",\"label\":\"Soils report\",\"required\":false}]")
            .build();
    }

    private Document doc(String category, ScanStatus scan) {
        return Document.builder().originalName(category + ".pdf").fileType("PDF").sizeBytes(10)
            .storagePath("p").docCategory(category).scanStatus(scan).build();
    }

    @Test
    void flagsMissingRequiredDocumentsAsBlocking() {
        PreCheckRun run = new PreCheckRun();
        CompletenessService.Result result = service.evaluate(run, permitType(),
            List.of(doc("architectural_plans", ScanStatus.PASSED)));

        assertThat(result.requiredCount()).isEqualTo(2);
        assertThat(result.presentRequiredCount()).isEqualTo(1);
        assertThat(result.missingLabels()).containsExactly("Structural plans");
        assertThat(result.findings()).anyMatch(f ->
            f.getSeverity() == Severity.BLOCKING && f.getTitle().contains("Structural plans"));
    }

    @Test
    void noDocumentsProducesBlockingFinding() {
        CompletenessService.Result result = service.evaluate(new PreCheckRun(), permitType(), List.of());
        assertThat(result.findings()).anyMatch(f -> f.getTitle().equals("No documents uploaded"));
    }

    @Test
    void scanFailedDocumentIsFlagged() {
        CompletenessService.Result result = service.evaluate(new PreCheckRun(), permitType(),
            List.of(doc("architectural_plans", ScanStatus.PASSED),
                    doc("structural_plans", ScanStatus.QUARANTINED)));
        assertThat(result.findings()).anyMatch(f -> f.getTitle().startsWith("File failed security scan"));
        // The quarantined structural_plans doesn't count as present → still missing.
        assertThat(result.missingLabels()).contains("Structural plans");
    }

    @Test
    void allRequiredPresentProducesNoMissingFindings() {
        CompletenessService.Result result = service.evaluate(new PreCheckRun(), permitType(),
            List.of(doc("architectural_plans", ScanStatus.PASSED), doc("structural_plans", ScanStatus.PASSED)));
        assertThat(result.missingLabels()).isEmpty();
        assertThat(result.findings()).isEmpty();
    }
}
