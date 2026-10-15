package cl.bello.stockflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.bello.stockflow.controller.ClienteController;
import cl.bello.stockflow.controller.PedidoController;
import cl.bello.stockflow.controller.ProductoController;
import cl.bello.stockflow.controller.ReporteController;
import cl.bello.stockflow.dto.ItemPedidoRequest;
import cl.bello.stockflow.dto.PedidoRequest;
import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.entity.Cliente;
import cl.bello.stockflow.entity.Producto;
import cl.bello.stockflow.exception.ConflictoException;
import cl.bello.stockflow.repository.ClienteRepository;
import cl.bello.stockflow.repository.DetallePedidoRepository;
import cl.bello.stockflow.repository.PedidoRepository;
import cl.bello.stockflow.repository.ProductoRepository;
import cl.bello.stockflow.service.PedidoService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockflowApplicationTests {

	private static final Pattern ID_JSON = Pattern.compile("\\\"id\\\":(\\d+)");

	@LocalServerPort
	private int puerto;

	@Autowired
	private PedidoService pedidoService;

	@Autowired
	private ClienteRepository clienteRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private PedidoRepository pedidoRepository;

	@Autowired
	private DetallePedidoRepository detallePedidoRepository;

	@Autowired
	private ProductoController productoController;

	@Autowired
	private ClienteController clienteController;

	@Autowired
	private PedidoController pedidoController;

	@Autowired
	private ReporteController reporteController;

	@BeforeEach
	void limpiarBase() {
		detallePedidoRepository.deleteAll();
		pedidoRepository.deleteAll();
		productoRepository.deleteAll();
		clienteRepository.deleteAll();
	}

	@Test
	void cargaContextoYControllers() {
		assertThat(productoController).isNotNull();
		assertThat(clienteController).isNotNull();
		assertThat(pedidoController).isNotNull();
		assertThat(reporteController).isNotNull();
	}

	@Test
	void crearPedidoDescuentaStockYCalculaTotal() {
		Cliente cliente = clienteRepository.save(new Cliente("Cliente prueba", "cliente@ejemplo.cl"));
		Producto producto = productoRepository.save(
				new Producto("Teclado", "TEC-001", new BigDecimal("25000.00"), 10, 2));

		PedidoResponse respuesta = pedidoService.crear(new PedidoRequest(
				cliente.getId(),
				List.of(new ItemPedidoRequest(producto.getId(), 2))));

		assertThat(respuesta.total()).isEqualByComparingTo("50000.00");
		assertThat(respuesta.items()).hasSize(1);
		assertThat(productoRepository.findById(producto.getId()).orElseThrow().getStockDisponible()).isEqualTo(8);
		assertThat(pedidoRepository.count()).isEqualTo(1);
	}

	@Test
	void pedidoSinStockSeRechazaCompleto() {
		Cliente cliente = clienteRepository.save(new Cliente("Cliente prueba", "cliente@ejemplo.cl"));
		Producto producto = productoRepository.save(
				new Producto("Mouse", "MOU-001", new BigDecimal("15000.00"), 1, 1));

		PedidoRequest request = new PedidoRequest(
				cliente.getId(),
				List.of(new ItemPedidoRequest(producto.getId(), 2)));

		assertThatThrownBy(() -> pedidoService.crear(request))
				.isInstanceOf(ConflictoException.class)
				.hasMessageContaining("Stock insuficiente");
		assertThat(productoRepository.findById(producto.getId()).orElseThrow().getStockDisponible()).isEqualTo(1);
		assertThat(pedidoRepository.count()).isZero();
	}

	@Test
	void endpointsCreanProductoClienteYPedido() throws Exception {
		HttpResponse<String> producto = post("/api/productos", """
				{
				  "nombre": "Monitor",
				  "codigo": "MON-001",
				  "precio": 10000.00,
				  "stockDisponible": 5,
				  "stockMinimo": 1
				}
				""");
		assertThat(producto.statusCode()).isEqualTo(201);
		long productoId = extraerId(producto.body());

		HttpResponse<String> cliente = post("/api/clientes", """
				{
				  "nombre": "Cliente HTTP",
				  "email": "http@ejemplo.cl"
				}
				""");
		assertThat(cliente.statusCode()).isEqualTo(201);
		long clienteId = extraerId(cliente.body());

		HttpResponse<String> pedido = post("/api/pedidos", """
				{
				  "clienteId": %d,
				  "items": [
				    { "productoId": %d, "cantidad": 2 }
				  ]
				}
				""".formatted(clienteId, productoId));

		assertThat(pedido.statusCode()).isEqualTo(201);
		assertThat(pedido.body()).contains("PENDIENTE", "20000.00");
	}

	private HttpResponse<String> post(String ruta, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + puerto + ruta))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private long extraerId(String json) {
		Matcher matcher = ID_JSON.matcher(json);
		if (!matcher.find()) {
			throw new AssertionError("La respuesta no contiene un id: " + json);
		}
		return Long.parseLong(matcher.group(1));
	}
}
