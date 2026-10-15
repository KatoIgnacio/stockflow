package cl.bello.stockflow.dto;

import java.math.BigDecimal;

public record DetallePedidoResponse(
		Long productoId,
		String codigo,
		String nombre,
		int cantidad,
		BigDecimal precioUnitario,
		BigDecimal subtotal) {
}
