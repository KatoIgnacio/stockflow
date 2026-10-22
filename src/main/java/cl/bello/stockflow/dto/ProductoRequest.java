package cl.bello.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
		@NotBlank @Size(max = 150) String nombre,
		@NotBlank @Size(max = 60) String codigo,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal precio,
		@NotNull @PositiveOrZero Integer stockDisponible,
		@NotNull @PositiveOrZero Integer stockMinimo) {
}
