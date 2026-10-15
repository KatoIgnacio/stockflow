package cl.bello.stockflow.controller;

import cl.bello.stockflow.dto.ProductoRequest;
import cl.bello.stockflow.dto.ProductoResponse;
import cl.bello.stockflow.service.ProductoService;
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
@RequestMapping("/api/productos")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	@PostMapping
	public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
		ProductoResponse producto = productoService.crear(request);
		return ResponseEntity.created(URI.create("/api/productos/" + producto.id())).body(producto);
	}

	@GetMapping
	public List<ProductoResponse> listar(
			@RequestParam(required = false) String nombre,
			@RequestParam(required = false) String codigo) {
		return productoService.listar(nombre, codigo);
	}

	@GetMapping("/{id}")
	public ProductoResponse obtener(@PathVariable Long id) {
		return productoService.obtener(id);
	}

	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
		return productoService.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> desactivar(@PathVariable Long id) {
		productoService.desactivar(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/stock-bajo")
	public List<ProductoResponse> listarStockBajo() {
		return productoService.listarStockBajo();
	}
}
