package cl.bello.stockflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReporteInventarioResponse(
		LocalDateTime generadoEn,
		List<ProductoResponse> productos) {
}
