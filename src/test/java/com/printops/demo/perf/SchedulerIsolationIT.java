package com.printops.demo.perf;

import com.printops.demo.entity.*;
import com.printops.demo.service.MaintenanceRuleService;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Una regla que falla no debe revertir ni frenar a las demas.
class SchedulerIsolationIT extends IntegrationTestBase {

    @Autowired MaintenanceRuleService ruleService;

    @Test
    void failingRuleDoesNotPreventOthersFromBeingProcessed() {
        deactivateAllRules();
        World w = newWorld();
        addOrders(w, 5, 1);
        addTriggeredRules(w, 5);

        // La regla del medio apunta a una impresora con nombre de 250 caracteres: el
        // mensaje de la notificacion (> 255) viola el largo de la columna al insertarse,
        // despues de que la orden de esa regla ya se inserto en la misma transaccion.
        // (regla i -> impresora i: ninguna otra regla comparte esa impresora)
        Printer longName = w.rules.get(2).getPrinter();
        jdbc.update("UPDATE printers SET name = ? WHERE id = ?", "N".repeat(250), longName.getId());

        ruleService.evaluateAllRules();

        Set<Long> ruleIdsWithOrder = orderRepository.findByWorkspaceId(w.workspaceId).stream()
                .map(MaintenanceOrder::getMaintenanceRuleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> expected = w.rules.stream().map(MaintenanceRule::getId).collect(Collectors.toSet());
        expected.remove(w.rules.get(2).getId());
        assertThat(ruleIdsWithOrder).containsExactlyInAnyOrderElementsOf(expected);
        // y la regla que fallo no dejo una orden a medias
        assertThat(ruleIdsWithOrder).doesNotContain(w.rules.get(2).getId());
    }
}
