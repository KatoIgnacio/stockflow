package cl.bello.stockflow;

import static org.assertj.core.api.Assertions.assertThat;

import cl.bello.stockflow.dto.ItemPedidoRequest;
import cl.bello.stockflow.dto.PedidoRequest;
import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.entity.Cliente;
import cl.bello.stockflow.entity.Pedido;
import cl.bello.stockflow.entity.EstadoPedido;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiAcceptanceTests {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	@LocalServerPort private int puerto;
	@Autowired private ClienteRepository clientes;
	@Autowired private ProductoRepository productos;
	@Autowired private PedidoRepository pedidos;
	@Autowired private DetallePedidoRepository detalles;
	@Autowired private PedidoService servicio;

	@BeforeEach
	void limpiarSoloBaseDePruebas() {
		detalles.deleteAll();
		pedidos.deleteAll();
		productos.deleteAll();
		clientes.deleteAll();
	}

	@Test
	void pedidoConVariosProductosCalculaTotalYDescuentaAmbosStocks() throws Exception {
		Cliente cliente = cliente();
		Producto a = producto("A", 10, 2, "1000.50");
		Producto b = producto("B", 5, 2, "2000.00");
		var respuesta = enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), a.getId(), 2, b.getId(), 3));
		assertThat(respuesta.statusCode()).isEqualTo(201);
		JsonNode pedido = json(respuesta);
		assertThat(pedido.path("estado").asText()).isEqualTo("PENDIENTE");
		assertThat(pedido.path("items").size()).isEqualTo(2);
		assertThat(pedido.path("total").decimalValue()).isEqualByComparingTo("8001.00");
		assertThat(stock(a)).isEqualTo(8);
		assertThat(stock(b)).isEqualTo(2);
		assertThat(detalles.count()).isEqualTo(2);
		var consulta = enviar("GET", "/api/pedidos/" + pedido.path("id").asLong(), null);
		assertThat(consulta.statusCode()).isEqualTo(200);
		assertThat(json(consulta).path("items").size()).isEqualTo(2);
	}

	@Test
	void rechazoDeUnItemConservaTodosLosStocksYSinPedidoNiDetalles() throws Exception {
		Cliente cliente = cliente();
		Producto a = producto("A", 10, 2, "1000.00");
		Producto b = producto("B", 1, 2, "2000.00");
		var respuesta = enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), a.getId(), 2, b.getId(), 2));
		assertThat(respuesta.statusCode()).isEqualTo(409);
		assertThat(json(respuesta).path("mensaje").asText()).contains("Stock insuficiente");
		assertThat(stock(a)).isEqualTo(10);
		assertThat(stock(b)).isEqualTo(1);
		assertThat(pedidos.count()).isZero();
		assertThat(detalles.count()).isZero();
	}

	@Test
	void itemsRepetidosSeAgrupanYSeValidaLaCantidadAcumulada() throws Exception {
		Cliente cliente = cliente();
		Producto a = producto("A", 5, 1, "10.00");
		var rechazada = enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), a.getId(), 3, a.getId(), 3));
		assertThat(rechazada.statusCode()).isEqualTo(409);
		assertThat(stock(a)).isEqualTo(5);
		var aceptada = enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), a.getId(), 2, a.getId(), 3));
		assertThat(aceptada.statusCode()).isEqualTo(201);
		assertThat(json(aceptada).path("items").size()).isEqualTo(1);
		assertThat(json(aceptada).path("items").get(0).path("cantidad").asInt()).isEqualTo(5);
		assertThat(stock(a)).isZero();
	}

	@Test
	void pedidosSimultaneosNoSobrevendenLaUltimaUnidad() throws Exception {
		Cliente cliente = cliente();
		Producto producto = producto("ULTIMO", 1, 1, "50.00");
		PedidoRequest request = new PedidoRequest(cliente.getId(), List.of(new ItemPedidoRequest(producto.getId(), 1)));
		CountDownLatch listos = new CountDownLatch(2);
		CountDownLatch inicio = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var tarea = (java.util.concurrent.Callable<String>) () -> {
				listos.countDown();
				if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No se inició la concurrencia");
				try {
					servicio.crear(request);
					return "CREADO";
				} catch (ConflictoException esperado) {
					return "SIN_STOCK";
				}
			};
			var primera = executor.submit(tarea);
			var segunda = executor.submit(tarea);
			assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
			inicio.countDown();
			assertThat(List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder("CREADO", "SIN_STOCK");
			assertThat(stock(producto)).isZero();
			assertThat(pedidos.count()).isEqualTo(1);
			assertThat(detalles.count()).isEqualTo(1);
		} finally {
			inicio.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void crudProductosFiltraActualizaYDesactiva() throws Exception {
		var creada = enviar("POST", "/api/productos", productoJson("Teclado", "tec-1", "100.00", 10, 2));
		assertThat(creada.statusCode()).isEqualTo(201);
		long id = json(creada).path("id").asLong();
		assertThat(creada.headers().firstValue("Location")).contains("/api/productos/" + id);
		producto("OTRO", 4, 1, "50.00");
		for (String filtro : List.of("nombre=tecla", "codigo=TEC", "nombre=tecla&codigo=TEC")) {
			var lista = enviar("GET", "/api/productos?" + filtro, null);
			assertThat(lista.statusCode()).isEqualTo(200);
			assertThat(json(lista).size()).isEqualTo(1);
			assertThat(json(lista).get(0).path("id").asLong()).isEqualTo(id);
		}
		var actualizada = enviar("PUT", "/api/productos/" + id, productoJson("Teclado nuevo", "TEC-1", "150.00", 8, 3));
		assertThat(actualizada.statusCode()).isEqualTo(200);
		assertThat(json(actualizada).path("precio").decimalValue()).isEqualByComparingTo("150.00");
		assertThat(enviar("DELETE", "/api/productos/" + id, null).statusCode()).isEqualTo(204);
		assertThat(enviar("GET", "/api/productos/" + id, null).statusCode()).isEqualTo(404);
		assertThat(json(enviar("GET", "/api/productos?codigo=TEC-1", null)).size()).isZero();
		assertThat(productos.findById(id).orElseThrow().isActivo()).isFalse();
	}

	@Test
	void crudClientesFiltraActualizaYDesactiva() throws Exception {
		var creada = enviar("POST", "/api/clientes", "{\"nombre\":\"Ana Perez\",\"email\":\"ANA@EJEMPLO.CL\"}");
		assertThat(creada.statusCode()).isEqualTo(201);
		long id = json(creada).path("id").asLong();
		clientes.save(new Cliente("Otro", "otro@ejemplo.cl"));
		for (String filtro : List.of("nombre=ana", "email=ana@", "nombre=ana&email=ana@")) {
			var lista = enviar("GET", "/api/clientes?" + filtro, null);
			assertThat(lista.statusCode()).isEqualTo(200);
			assertThat(json(lista).size()).isEqualTo(1);
			assertThat(json(lista).get(0).path("email").asText()).isEqualTo("ana@ejemplo.cl");
		}
		assertThat(enviar("GET", "/api/clientes/" + id, null).statusCode()).isEqualTo(200);
		var actualizada = enviar("PUT", "/api/clientes/" + id, "{\"nombre\":\"Ana Nueva\",\"email\":\"nueva@ejemplo.cl\"}");
		assertThat(actualizada.statusCode()).isEqualTo(200);
		assertThat(json(actualizada).path("nombre").asText()).isEqualTo("Ana Nueva");
		assertThat(enviar("DELETE", "/api/clientes/" + id, null).statusCode()).isEqualTo(204);
		assertThat(enviar("GET", "/api/clientes/" + id, null).statusCode()).isEqualTo(404);
		assertThat(json(enviar("GET", "/api/clientes?email=nueva@", null)).size()).isZero();
		assertThat(clientes.findById(id).orElseThrow().isActivo()).isFalse();
	}

	@Test
	void borradoLogicoConservaPedidosYPrecioHistorico() throws Exception {
		Cliente cliente = cliente();
		Producto producto = producto("A", 10, 1, "100.00");
		PedidoResponse pedido = servicio.crear(new PedidoRequest(cliente.getId(), List.of(new ItemPedidoRequest(producto.getId(), 2))));
		enviar("PUT", "/api/productos/" + producto.getId(), productoJson("A", "A", "200.00", 8, 1));
		enviar("DELETE", "/api/productos/" + producto.getId(), null);
		enviar("DELETE", "/api/clientes/" + cliente.getId(), null);
		var historico = enviar("GET", "/api/pedidos/" + pedido.id(), null);
		assertThat(historico.statusCode()).isEqualTo(200);
		assertThat(json(historico).path("items").get(0).path("precioUnitario").decimalValue()).isEqualByComparingTo("100.00");
		assertThat(json(enviar("GET", "/api/clientes/" + cliente.getId() + "/pedidos", null)).size()).isEqualTo(1);
		assertThat(enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), producto.getId(), 1, producto.getId(), 1)).statusCode()).isEqualTo(404);
	}

	@Test
	void stockBajoUsaComparacionEstrictaYSoloActivos() throws Exception {
		Producto bajo = producto("BAJO", 2, 3, "10.00");
		producto("IGUAL", 3, 3, "10.00");
		producto("ALTO", 4, 3, "10.00");
		Producto inactivo = producto("INACTIVO", 0, 3, "10.00");
		enviar("DELETE", "/api/productos/" + inactivo.getId(), null);
		JsonNode lista = json(enviar("GET", "/api/productos/stock-bajo", null));
		assertThat(lista.size()).isEqualTo(1);
		assertThat(lista.get(0).path("id").asLong()).isEqualTo(bajo.getId());
		JsonNode reporte = json(enviar("GET", "/api/reportes/inventario", null));
		assertThat(reporte.path("productos").size()).isEqualTo(3);
		assertThat(reporte.path("generadoEn").asText()).isNotBlank();
	}

	@Test
	void filtrosDeFechaIncluyenTodoElUltimoDiaYReportanTotales() throws Exception {
		Cliente cliente = cliente();
		Cliente otro = clientes.save(new Cliente("Otro", "otro@ejemplo.cl"));
		guardarPedido(cliente, "2026-10-01T00:00:00", "10.00");
		guardarPedido(cliente, "2026-10-02T23:59:59", "20.00");
		guardarPedido(otro, "2026-10-03T00:00:00", "40.00");
		String periodo = "?desde=2026-10-01&hasta=2026-10-02";
		assertThat(json(enviar("GET", "/api/pedidos" + periodo, null)).size()).isEqualTo(2);
		assertThat(json(enviar("GET", "/api/pedidos?desde=2026-10-02", null)).size()).isEqualTo(2);
		assertThat(json(enviar("GET", "/api/pedidos?hasta=2026-10-01", null)).size()).isEqualTo(1);
		assertThat(json(enviar("GET", "/api/pedidos?desde=2027-01-01", null)).size()).isZero();
		JsonNode reporte = json(enviar("GET", "/api/reportes/pedidos" + periodo, null));
		assertThat(reporte.path("pedidos").size()).isEqualTo(2);
		assertThat(reporte.path("montoTotal").decimalValue()).isEqualByComparingTo("30.00");
		assertThat(json(enviar("GET", "/api/clientes/" + cliente.getId() + "/pedidos", null)).size()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/", "/api/ruta-inexistente"})
	void rutaInexistenteDevuelve404(String ruta) throws Exception {
		var respuesta = enviar("GET", ruta, null);
		assertThat(respuesta.statusCode()).isEqualTo(404);
		assertThat(json(respuesta).path("mensaje").asText()).isEqualTo("Ruta no encontrada");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/api/productos/999999", "/api/clientes/999999", "/api/pedidos/999999", "/api/clientes/999999/pedidos"
	})
	void recursoInexistenteDevuelve404(String ruta) throws Exception {
		assertThat(enviar("GET", ruta, null).statusCode()).isEqualTo(404);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/api/productos/abc", "/api/clientes/abc", "/api/pedidos/abc",
		"/api/pedidos?desde=fecha-invalida", "/api/reportes/pedidos?hasta=2026-99-99",
		"/api/pedidos?desde=2026-10-03&hasta=2026-10-01",
		"/api/reportes/pedidos?desde=2026-10-03&hasta=2026-10-01"
	})
	void parametrosInvalidosDevuelven400SinDetallesInternos(String ruta) throws Exception {
		var respuesta = enviar("GET", ruta, null);
		assertThat(respuesta.statusCode()).isEqualTo(400);
		assertThat(json(respuesta).path("mensaje").asText()).isNotBlank();
		assertThat(respuesta.body()).doesNotContain("stackTrace", "org.hibernate", "jdbc:", "java.lang");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"nombre\":\"\",\"codigo\":\"A\",\"precio\":10,\"stockDisponible\":1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"\",\"precio\":10,\"stockDisponible\":1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"A\",\"precio\":-1,\"stockDisponible\":1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"A\",\"precio\":1.001,\"stockDisponible\":1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"A\",\"precio\":10000000000,\"stockDisponible\":1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"A\",\"precio\":10,\"stockDisponible\":-1,\"stockMinimo\":0}",
		"{\"nombre\":\"A\",\"codigo\":\"A\",\"precio\":10,\"stockDisponible\":1,\"stockMinimo\":-1}",
		"{}", "{mal-json"
	})
	void productosInvalidosNoSeGuardan(String cuerpo) throws Exception {
		assertThat(enviar("POST", "/api/productos", cuerpo).statusCode()).isEqualTo(400);
		assertThat(productos.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"nombre\":\"\",\"email\":\"a@ejemplo.cl\"}", "{\"nombre\":\"Ana\",\"email\":\"incorrecto\"}"})
	void clientesInvalidosNoSeGuardan(String cuerpo) throws Exception {
		assertThat(enviar("POST", "/api/clientes", cuerpo).statusCode()).isEqualTo(400);
		assertThat(clientes.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"clienteId\":1,\"items\":[]}", "{\"clienteId\":1,\"items\":[null]}",
		"{\"clienteId\":1,\"items\":[{\"productoId\":1,\"cantidad\":0}]}",
		"{\"clienteId\":1,\"items\":[{\"productoId\":1,\"cantidad\":-1}]}",
		"{\"clienteId\":1,\"items\":[{\"productoId\":1}]}", "{\"items\":[]}", "{}"
	})
	void pedidosInvalidosNoSeGuardan(String cuerpo) throws Exception {
		assertThat(enviar("POST", "/api/pedidos", cuerpo).statusCode()).isEqualTo(400);
		assertThat(pedidos.count()).isZero();
	}

	@Test
	void codigoYCorreoDuplicadosDevuelven409() throws Exception {
		producto("A", 1, 1, "10.00");
		cliente();
		assertThat(enviar("POST", "/api/productos", productoJson("Otro", "a", "10.00", 1, 1)).statusCode()).isEqualTo(409);
		assertThat(enviar("POST", "/api/clientes", "{\"nombre\":\"Otro\",\"email\":\"CLIENTE@EJEMPLO.CL\"}").statusCode()).isEqualTo(409);
	}

	@Test
	void pedidosConClienteOProductoInactivoOInexistenteSeRechazanSinCambios() throws Exception {
		Cliente cliente = cliente();
		Producto producto = producto("A", 5, 1, "10.00");
		assertThat(enviar("POST", "/api/pedidos", pedidoJson(999999L, producto.getId(), 1, producto.getId(), 1)).statusCode()).isEqualTo(404);
		assertThat(enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), producto.getId(), 1, 999999L, 1)).statusCode()).isEqualTo(404);
		enviar("DELETE", "/api/productos/" + producto.getId(), null);
		assertThat(enviar("POST", "/api/pedidos", pedidoJson(cliente.getId(), producto.getId(), 1, producto.getId(), 1)).statusCode()).isEqualTo(404);
		assertThat(stock(producto)).isEqualTo(5);
		assertThat(pedidos.count()).isZero();
	}

	private Cliente cliente() { return clientes.save(new Cliente("Cliente", "cliente@ejemplo.cl")); }
	private Producto producto(String codigo, int stock, int minimo, String precio) {
		return productos.save(new Producto(codigo, codigo, new BigDecimal(precio), stock, minimo));
	}
	private int stock(Producto producto) { return productos.findById(producto.getId()).orElseThrow().getStockDisponible(); }
	private void guardarPedido(Cliente cliente, String fecha, String total) {
		Pedido pedido = new Pedido(cliente, LocalDateTime.parse(fecha), EstadoPedido.PENDIENTE);
		pedido.setTotal(new BigDecimal(total));
		pedidos.save(pedido);
	}
	private String productoJson(String nombre, String codigo, String precio, int stock, int minimo) {
		return "{\"nombre\":\"%s\",\"codigo\":\"%s\",\"precio\":%s,\"stockDisponible\":%d,\"stockMinimo\":%d}"
				.formatted(nombre, codigo, precio, stock, minimo);
	}
	private String pedidoJson(long cliente, long a, int ca, long b, int cb) {
		return "{\"clienteId\":%d,\"items\":[{\"productoId\":%d,\"cantidad\":%d},{\"productoId\":%d,\"cantidad\":%d}]}"
				.formatted(cliente, a, ca, b, cb);
	}
	private JsonNode json(HttpResponse<String> respuesta) { return JSON.readTree(respuesta.body()); }
	private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
				.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
