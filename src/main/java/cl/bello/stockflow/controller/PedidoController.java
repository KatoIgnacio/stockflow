package cl.bello.stockflow.controller;

import cl.bello.stockflow.dto.PedidoRequest;
import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.service.PedidoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

	private final PedidoService pedidoService;

	public PedidoController(PedidoService pedidoService) {
		this.pedidoService = pedidoService;
	}

	@PostMapping
	public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoRequest request) {
		PedidoResponse pedido = pedidoService.crear(request);
		return ResponseEntity.created(URI.create("/api/pedidos/" + pedido.id())).body(pedido);
	}

	@GetMapping("/{id}")
	public PedidoResponse obtener(@PathVariable Long id) {
		return pedidoService.obtener(id);
	}

	@GetMapping
	public List<PedidoResponse> listar(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return pedidoService.listarPorPeriodo(desde, hasta);
	}
}
