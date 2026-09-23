// src/main/java/com/printops/demo/service/StatusTransitionValidator.java
package com.printops.demo.service;

import com.printops.demo.entity.OrderStatus;
import com.printops.demo.entity.Role;
import com.printops.demo.exception.ForbiddenTransitionException;
import com.printops.demo.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

// Máquina de estados de la orden (US-05).
//
// Reglas (roles del proyecto: MANAGER = supervisor, TECNICO = ejecutor):
//   PENDING     -> IN_PROGRESS   : TECNICO (solo el asignado) o MANAGER
//   IN_PROGRESS -> IN_REVIEW     : TECNICO (solo el asignado) o MANAGER
//   IN_REVIEW   -> COMPLETED     : MANAGER
//   IN_REVIEW   -> IN_PROGRESS   : MANAGER (rechazo; comentario obligatorio)
//   PENDING/IN_PROGRESS/IN_REVIEW -> CANCELLED : TECNICO asignado o MANAGER
//
// Distingue dos errores:
//   - InvalidStatusTransitionException (400): la transición no existe en la tabla.
//   - ForbiddenTransitionException (403): existe, pero el rol/asignación no alcanza.
@Component
public class StatusTransitionValidator {

    public void validate(OrderStatus from, OrderStatus to, Role role,
                         Long assignedTechnicianId, Long currentUserId) {
        boolean isManager = role == Role.MANAGER;
        boolean isAssigned = assignedTechnicianId != null && assignedTechnicianId.equals(currentUserId);

        // 1) ¿La transición existe en la tabla (para algún rol)?
        boolean known = switch (to) {
            case IN_PROGRESS -> from == OrderStatus.PENDING || from == OrderStatus.IN_REVIEW;
            case IN_REVIEW -> from == OrderStatus.IN_PROGRESS;
            case COMPLETED -> from == OrderStatus.IN_REVIEW;
            case CANCELLED -> from == OrderStatus.PENDING
                    || from == OrderStatus.IN_PROGRESS
                    || from == OrderStatus.IN_REVIEW;
            default -> false;
        };

        if (!known) {
            throw new InvalidStatusTransitionException(
                    "Transición no permitida de " + from + " a " + to + ".");
        }

        // 2) ¿El rol / asignación permite ejecutarla?
        boolean allowed;
        if (isManager) {
            allowed = switch (to) {
                case COMPLETED -> from == OrderStatus.IN_REVIEW;
                case IN_PROGRESS -> from == OrderStatus.IN_REVIEW; // rechazo
                case CANCELLED -> true;
                default -> false;
            };
        } else {
            if (!isAssigned) {
                throw new ForbiddenTransitionException(
                        "Solo el técnico asignado puede mover esta orden.");
            }
            allowed = switch (to) {
                case IN_PROGRESS -> from == OrderStatus.PENDING;
                case IN_REVIEW -> from == OrderStatus.IN_PROGRESS;
                case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.IN_PROGRESS;
                default -> false;
            };
        }

        if (!allowed) {
            throw new ForbiddenTransitionException(
                    "No tenés permisos para mover la orden de " + from + " a " + to + ".");
        }
    }
}
