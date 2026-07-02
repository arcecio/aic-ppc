package com.lacity.aipppc.service.embedding;

import java.util.List;

/**
 * A text-embedding backend for the knowledgebase's semantic retriever. The only
 * registered implementation is {@link TeiEmbeddingProvider} (a local Hugging Face
 * Text Embeddings Inference sidecar serving intfloat/e5-large-v2, exactly as in
 * the Blue reference). The e5 {@code passage:} / {@code query:} prefix discipline
 * is encapsulated inside the provider.
 */
public interface EmbeddingProvider {

    String type();

    /** True when the provider is configured and believed reachable. */
    boolean available();

    /** Embeds passages (documents). One vector per input, in order. */
    List<float[]> embedPassages(List<String> texts);

    /** Embeds a search query. */
    float[] embedQuery(String text);
}
