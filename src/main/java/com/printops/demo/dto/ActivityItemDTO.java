package com.printops.demo.dto;

import java.time.Instant;

// Evento del registro de actividad. kind: ORDER_STATUS, STOCK, LOGIN, LOGOUT, LOGIN_FAILED, JOB.
public record ActivityItemDTO(String kind, Instant at, String actor, Long refId, String ref, String detail) {
}
