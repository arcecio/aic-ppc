package com.lacity.aipppc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI-Powered Pre-Plan Check Assistant (AIP PPC) — City of Los Angeles / LADBS.
 * RFP 2025AIP007. Advisory pre-plan-check tool with a human-in-the-loop guardrail:
 * it never issues permits or performs Formal Plan Check; City staff retain final
 * authority over all determinations (SOW 1.1).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AipPpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(AipPpcApplication.class, args);
    }
}
