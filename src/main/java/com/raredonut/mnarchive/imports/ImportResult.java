package com.raredonut.mnarchive.imports;

public record ImportResult(
        long batchId,
        int parsed,      // 읽어온 보면 수
        int changed,     // 실제로 갱신된 보면 수
        int newCharts,   // 새로 등록된 보면(마스터) 수
        boolean duplicate
) {}
