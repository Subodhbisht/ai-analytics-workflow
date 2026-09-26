package com.sbisht.analytic.ragagent;

import com.sbisht.analytic.agent.dto.QueryResult;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class ReasonCodeKnowledgeBase {

    private static final Pattern CHUNK = Pattern.compile("(?ms)^##\\s+(Restart_[A-Za-z0-9_]+)\\s*$\\R(.*?)(?=^##\\s+Restart_|\\z)");
    private static final Pattern REASON_CODE = Pattern.compile("\\bRestart_[A-Za-z0-9_]+\\b");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "what", "when", "which", "why", "how", "are", "is", "was",
            "were", "does", "did", "this", "that", "from", "into", "about", "reason", "code", "codes"
    );

    private final List<ReasonCodeChunk> chunks;

    ReasonCodeKnowledgeBase(ResourceLoader resourceLoader) throws IOException {
        var resource = resourceLoader.getResource("classpath:knowledge/onboarding-reason-codes.txt");
        this.chunks = parse(resource.getContentAsString(StandardCharsets.UTF_8));
    }

    List<ReasonCodeChunk> retrieve(String prompt, QueryResult queryResult, int maxChunks, boolean fallback) {
        var exactReasonCodes = exactReasonCodes(prompt);
        if (queryResult != null) {
            exactReasonCodes.addAll(reasonCodesFromRows(queryResult));
        }

        var tokens = tokens(prompt);
        var scored = new ArrayList<ScoredChunk>();
        for (var chunk : chunks) {
            var score = exactReasonCodes.contains(chunk.reasonCode().toLowerCase(Locale.ROOT)) ? 100 : 0;
            for (var token : tokens) {
                if (chunk.searchText().contains(token)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        if (scored.isEmpty()) {
            return fallback ? chunks.stream().limit(maxChunks).toList() : List.of();
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(maxChunks)
                .map(ScoredChunk::chunk)
                .toList();
    }

    String format(List<ReasonCodeChunk> retrievedChunks) {
        if (retrievedChunks.isEmpty()) {
            return "No matching onboarding reason-code documentation was found.";
        }
        var formatted = new StringBuilder();
        for (var chunk : retrievedChunks) {
            formatted.append("Reason Code: ").append(chunk.reasonCode()).append('\n')
                    .append(chunk.content()).append("\n\n");
        }
        return formatted.toString().strip();
    }

    private List<ReasonCodeChunk> parse(String content) {
        var matcher = CHUNK.matcher(content);
        var parsed = new ArrayList<ReasonCodeChunk>();
        while (matcher.find()) {
            parsed.add(new ReasonCodeChunk(matcher.group(1).strip(), matcher.group(2).strip()));
        }
        return List.copyOf(parsed);
    }

    private Set<String> exactReasonCodes(String text) {
        var reasonCodes = new LinkedHashSet<String>();
        if (text != null) {
            var matcher = REASON_CODE.matcher(text);
            while (matcher.find()) {
                reasonCodes.add(matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        return reasonCodes;
    }

    private Set<String> reasonCodesFromRows(QueryResult queryResult) {
        var reasonCodes = new LinkedHashSet<String>();
        for (var row : queryResult.rows()) {
            for (var value : row.values()) {
                if (value != null) {
                    reasonCodes.addAll(exactReasonCodes(value.toString()));
                }
            }
        }
        return reasonCodes;
    }

    private List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var uniqueTokens = new LinkedHashMap<String, Boolean>();
        for (var token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                uniqueTokens.put(token, Boolean.TRUE);
            }
        }
        return List.copyOf(uniqueTokens.keySet());
    }

    record ReasonCodeChunk(String reasonCode, String content) {
        String searchText() {
            return (reasonCode + "\n" + content).toLowerCase(Locale.ROOT);
        }
    }

    private record ScoredChunk(ReasonCodeChunk chunk, int score) {
    }
}
