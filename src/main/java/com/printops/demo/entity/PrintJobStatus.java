package com.printops.demo.entity;

public enum PrintJobStatus {
    QUEUED, PRINTING, DONE, FAILED, CANCELLED;

    public boolean isFinal() {
        return this == DONE || this == FAILED || this == CANCELLED;
    }
}
