package com.example.dynamicds.sync;

public record CleanRecordContext(
        Long globalId,
        String sourceSystem,
        String sourceType,
        Long sourceRowId,
        String cleanBatch,
        String createdAt
) {
}
