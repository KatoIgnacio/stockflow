package cl.bello.stockflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReportePedidosResponse(
		LocalDateTime generadoEn,
		LocalDate desde,
		LocalDate hasta,
		BigDecimal montoTotal,
		List<PedidoResponse> pedidos) {
}
