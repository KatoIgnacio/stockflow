package cl.bello.stockflow.dto;

import cl.bello.stockflow.entity.EstadoPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
		Long id,
		Long clienteId,
		String clienteNombre,
		LocalDateTime fecha,
		EstadoPedido estado,
		BigDecimal total,
		List<DetallePedidoResponse> items) {
}
