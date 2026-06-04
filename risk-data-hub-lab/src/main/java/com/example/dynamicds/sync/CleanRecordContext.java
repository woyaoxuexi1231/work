package com.example.dynamicds.sync;

public class CleanRecordContext {

    private final Long globalId;

    private final String sourceSystem;

    private final String sourceType;

    private final Long sourceRowId;

    private final String cleanBatch;

    private final String createdAt;


    public CleanRecordContext(Long globalId, String sourceSystem, String sourceType, Long sourceRowId, String cleanBatch, String createdAt) {

        this.globalId = globalId;

        this.sourceSystem = sourceSystem;

        this.sourceType = sourceType;

        this.sourceRowId = sourceRowId;

        this.cleanBatch = cleanBatch;

        this.createdAt = createdAt;

    }


    public Long getGlobalId() { return globalId; }

    public String getSourceSystem() { return sourceSystem; }

    public String getSourceType() { return sourceType; }

    public Long getSourceRowId() { return sourceRowId; }

    public String getCleanBatch() { return cleanBatch; }

    public String getCreatedAt() { return createdAt; }

}
