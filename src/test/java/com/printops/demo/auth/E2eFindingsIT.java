package com.printops.demo.auth;

import com.printops.demo.service.JwtService;
import com.printops.demo.service.PrinterHistoryService;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Hallazgos de la verificacion end-to-end.
class E2eFindingsIT extends IntegrationTestBase {

    @Autowired JwtService jwtService;
    @Autowired PrinterHistoryService historyService;

    // Dos access tokens emitidos en el mismo segundo para el mismo usuario eran identicos
    // (sin jti): si el primero se habia blacklisteado en el logout, el refresh inmediato
    // (Face ID tras un soft logout) devolvia un token ya revocado -> 401.
    @Test
    void accessTokensIssuedInTheSameSecondAreDistinct() {
        String a = jwtService.generateAccessToken("x@test.local", "MANAGER", 1L);
        String b = jwtService.generateAccessToken("x@test.local", "MANAGER", 1L);
        assertThat(a).isNotEqualTo(b);
    }

    // history devolvia 404 para una impresora ajena pero history/metrics y history/parts
    // respondian 200 con ceros.
    @Test
    void printerHistoryMetricsAndParts_rejectForeignPrinter() {
        World a = newWorld();
        World b = newWorld();
        var printer = newPrinter(a.workspaceId, "ajena");
        loginAs(b.manager);

        assertThatThrownBy(() -> historyService.getMetrics(printer.getId())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> historyService.getPartsUsage(printer.getId())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void printerHistoryMetricsAndParts_workForOwnPrinter() {
        World a = newWorld();
        var printer = newPrinter(a.workspaceId, "propia");
        loginAs(a.manager);

        assertThat(historyService.getMetrics(printer.getId()).totalInterventions()).isZero();
        assertThat(historyService.getPartsUsage(printer.getId())).isEmpty();
    }
}
