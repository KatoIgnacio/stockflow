package cl.bello.stockflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PedidoRequest(
		@NotNull @Positive Long clienteId,
		@NotEmpty List<@Valid ItemPedidoRequest> items) {
}
