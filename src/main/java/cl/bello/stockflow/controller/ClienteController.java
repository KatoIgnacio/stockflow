package cl.bello.stockflow.controller;

import cl.bello.stockflow.dto.ClienteRequest;
import cl.bello.stockflow.dto.ClienteResponse;
import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.service.ClienteService;
import cl.bello.stockflow.service.PedidoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService clienteService;
	private final PedidoService pedidoService;

	public ClienteController(ClienteService clienteService, PedidoService pedidoService) {
		this.clienteService = clienteService;
		this.pedidoService = pedidoService;
	}

	@PostMapping
	public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
		ClienteResponse cliente = clienteService.crear(request);
		return ResponseEntity.created(URI.create("/api/clientes/" + cliente.id())).body(cliente);
	}

	@GetMapping
	public List<ClienteResponse> listar(
			@RequestParam(required = false) String nombre,
			@RequestParam(required = false) String email) {
		return clienteService.listar(nombre, email);
	}

	@GetMapping("/{id}")
	public ClienteResponse obtener(@PathVariable Long id) {
		return clienteService.obtener(id);
	}

	@PutMapping("/{id}")
	public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
		return clienteService.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> desactivar(@PathVariable Long id) {
		clienteService.desactivar(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/pedidos")
	public List<PedidoResponse> listarPedidos(@PathVariable Long id) {
		return pedidoService.listarPorCliente(id);
	}
}
