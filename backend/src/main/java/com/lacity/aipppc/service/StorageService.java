package com.lacity.aipppc.service;

import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.enums.ScanStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Persists uploaded files, runs the pre-AI security scan, and extracts text for
 * the rule engine / AI (SOW 2.2.1, 2.2.6). Files are validated for format and
 * size and scanned for a malware signature before any AI integration
 * (SOW 2.2.11 — "all files passing an automated security scan prior to AI
 * integration"). Text extraction supports vector PDFs (PDFBox), DOCX (POI), and
 * text-based DXF; binary CAD/BIM are stored and classified but not parsed in the
 * MVP (initial focus is document structure + text extraction, SOW 2.2.6).
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** Accepted upload extensions (SOW 2.2.11). */
    private static final Set<String> ALLOWED = Set.of(
        "pdf", "docx", "doc", "dxf", "dwg", "rvt", "ifc", "png", "jpg", "jpeg");

    // EICAR anti-malware test signature — stands in for a real AV scan hook.
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR";

    private final Path basePath;
    private final long maxFileBytes;

    public StorageService(@Value("${app.storage.base-path:./storage}") String basePath,
                          @Value("${app.storage.max-file-bytes:104857600}") long maxFileBytes) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
    }

    public record StoredFile(String storagePath, String fileType, long size,
                             ScanStatus scanStatus, String scanDetail) {}

    public StoredFile store(UUID projectId, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String ext = extensionOf(original);

        // ── security scan: format + size + malware signature ───────────────────
        ScanStatus scanStatus = ScanStatus.PASSED;
        String scanDetail = "Passed format, size, and malware-signature checks.";
        if (!ALLOWED.contains(ext)) {
            scanStatus = ScanStatus.FAILED;
            scanDetail = "Rejected: file extension ." + ext + " is not an accepted format.";
        } else if (file.getSize() > maxFileBytes) {
            scanStatus = ScanStatus.FAILED;
            scanDetail = "Rejected: file exceeds the maximum size of " + (maxFileBytes / 1_048_576) + " MB.";
        }

        try {
            Path dir = basePath.resolve(projectId.toString());
            Files.createDirectories(dir);
            String safeName = UUID.randomUUID() + "_" + original.replaceAll("[^A-Za-z0-9._-]", "_");
            Path target = dir.resolve(safeName);
            byte[] bytes = file.getBytes();

            if (scanStatus == ScanStatus.PASSED && looksLikeMalware(bytes)) {
                scanStatus = ScanStatus.QUARANTINED;
                scanDetail = "Quarantined: matched a malware test signature.";
            }

            Files.write(target, bytes);
            String rel = basePath.relativize(target).toString();
            return new StoredFile(rel, normalizeType(ext), file.getSize(), scanStatus, scanDetail);
        } catch (IOException e) {
            log.error("Failed to store upload: {}", e.getMessage());
            throw ApiException.badRequest("Could not store the uploaded file.");
        }
    }

    /** Extracts text for screening. Empty string for unsupported/binary formats. */
    public String extractText(String storagePath, String fileType) {
        Path path = basePath.resolve(storagePath);
        if (!Files.exists(path)) return "";
        try {
            return switch (fileType) {
                case "PDF" -> extractPdf(path);
                case "DOCX" -> extractDocx(path);
                case "DXF" -> readText(path, 2_000_000);
                default -> "";
            };
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", storagePath, e.getMessage());
            return "";
        }
    }

    public byte[] read(String storagePath) {
        try {
            return Files.readAllBytes(basePath.resolve(storagePath));
        } catch (IOException e) {
            throw ApiException.notFound("Stored file not found.");
        }
    }

    public Path resolve(String storagePath) {
        return basePath.resolve(storagePath);
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractDocx(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String readText(Path path, int maxChars) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }

    private boolean looksLikeMalware(byte[] bytes) {
        int len = Math.min(bytes.length, 4096);
        String head = new String(bytes, 0, len, StandardCharsets.ISO_8859_1);
        return head.contains(EICAR);
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** Normalized display type per SOW 2.2.6 (PDF, CAD/DXF, BIM, DOCX). */
    public String normalizeType(String ext) {
        return switch (ext) {
            case "pdf" -> "PDF";
            case "docx", "doc" -> "DOCX";
            case "dxf" -> "DXF";
            case "dwg" -> "CAD";
            case "rvt", "ifc" -> "BIM";
            case "png", "jpg", "jpeg" -> "IMAGE";
            default -> "OTHER";
        };
    }
}
