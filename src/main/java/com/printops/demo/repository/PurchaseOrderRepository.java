package com.printops.demo.repository;

import com.printops.demo.entity.PurchaseOrder;
import com.printops.demo.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "supplier")
    List<PurchaseOrder> findByWorkspaceIdOrderByCreatedAtDescIdDesc(Long workspaceId);

    @EntityGraph(attributePaths = "supplier")
    Optional<PurchaseOrder> findByIdAndWorkspaceId(Long id, Long workspaceId);

    // Repuestos que ya están en una orden abierta: no se vuelven a sugerir.
    @Query("SELECT DISTINCT l.sparePart.id FROM PurchaseOrderLine l " +
            "WHERE l.purchaseOrder.workspaceId = :workspaceId AND l.purchaseOrder.status IN :statuses")
    List<Long> partIdsInOrders(@Param("workspaceId") Long workspaceId,
                               @Param("statuses") Collection<PurchaseOrderStatus> statuses);
}
