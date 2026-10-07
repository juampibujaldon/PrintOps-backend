package com.printops.demo.dto;

import java.util.List;

public record CanvasLayoutDTO(
        List<CanvasNode> nodes,
        List<CanvasEdge> edges
) {
    public record CanvasNode(
            Long printerId,
            Double x,
            Double y
    ) {}

    public record CanvasEdge(
            String id,
            Long fromId,
            Long toId
    ) {}
}
