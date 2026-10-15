package cl.bello.stockflow.dto;

import java.math.BigDecimal;

public record ProductoResponse(
		Long id,
		String nombre,
		String codigo,
		BigDecimal precio,
		int stockDisponible,
		int stockMinimo,
		boolean activo) {
}
