package cl.bello.stockflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiError(
		LocalDateTime timestamp,
		int status,
		String error,
		String mensaje,
		String ruta,
		List<String> detalles) {
}
