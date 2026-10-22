package cl.bello.stockflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Inicia un JAR real dos veces; solo se ejecuta mediante la tarea jarSmokeTest. */
class JarPersistenceTests {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private int port;
	private Process api;

	@Test
	void jarConservaPedidoYAmbosStocksTrasReiniciar() throws Exception {
		prepararPuertoYValidarBase();
		try {
			iniciar();
			String marca = UUID.randomUUID().toString().substring(0, 8);
			var cliente = enviar("POST", "/clientes", "{\"nombre\":\"Prueba JAR\",\"email\":\"jar-" + marca + "@ejemplo.cl\"}");
			assertThat(cliente.statusCode()).as("Cliente creado por el JAR").isEqualTo(201);
			long clienteId = json(cliente).path("id").asLong();
			long a = crearProducto("JAR-A-" + marca, 1000);
			long b = crearProducto("JAR-B-" + marca, 2000);
			String pedido = pedido(clienteId, a, b, 2);
			var creada = enviar("POST", "/pedidos", pedido);
			assertThat(creada.statusCode()).as("Pedido de dos productos creado").isEqualTo(201);
			JsonNode detalle = json(creada);
			long pedidoId = detalle.path("id").asLong();
			assertThat(detalle.path("items").size()).isEqualTo(2);
			assertThat(detalle.path("total").decimalValue()).isEqualByComparingTo("6000.00");
			assertThat(detalle.path("estado").asText()).isEqualTo("PENDIENTE");
			assertThat(enviar("POST", "/pedidos", pedido(clienteId, a, b, 100)).statusCode())
					.as("Rechazo completo sin existencias").isEqualTo(409);
			comprobarStock(a, "Rechazo conserva stock A");
			comprobarStock(b, "Rechazo conserva stock B");
			assertThat(json(enviar("GET", "/clientes/" + clienteId + "/pedidos", null)).size())
					.as("Solo queda un pedido para el cliente").isEqualTo(1);
			assertThat(enviar("GET", "/pedidos?desde=invalida", null).statusCode()).isEqualTo(400);
			detener();
			iniciar();
			comprobarStock(a, "Reinicio conserva stock A");
			comprobarStock(b, "Reinicio conserva stock B");
			var recuperada = enviar("GET", "/pedidos/" + pedidoId, null);
			assertThat(recuperada.statusCode()).as("Pedido persistente tras reinicio").isEqualTo(200);
			assertThat(json(recuperada).path("total").decimalValue()).isEqualByComparingTo("6000.00");
			assertThat(json(recuperada).path("items").size()).isEqualTo(2);
		} finally {
			detener();
		}
	}

	@Test
	void primerArranqueCargaDemoYReinicioConservaComprasSinDuplicar() throws Exception {
		prepararPuertoYValidarBase();
		// Solo en stockflow_test: simular la instalación del profesor sobre una base sin tablas.
		try (var connection = DriverManager.getConnection(System.getProperty("stockflow.test.dbUrl"),
				System.getProperty("stockflow.test.dbUser"), System.getProperty("stockflow.test.dbPassword"));
			 var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS detalles_pedido, pedidos, productos, clientes CASCADE");
		}
		try {
			iniciar();
			assertThat(json(enviar("GET", "/productos", null)).size()).as("Catálogo automático").isEqualTo(8);
			assertThat(json(enviar("GET", "/clientes", null)).size()).as("Clientes automáticos").isEqualTo(3);
			assertThat(json(enviar("GET", "/pedidos", null)).size()).as("Pedidos automáticos").isEqualTo(2);
			assertThat(json(enviar("GET", "/productos/stock-bajo", null)).size()).isEqualTo(2);
			assertThat(json(enviar("GET", "/reportes/pedidos", null)).path("montoTotal").decimalValue())
					.isEqualByComparingTo("284940.00");
			JsonNode teclado = json(enviar("GET", "/productos?codigo=DEMO-TEC-001", null)).get(0);
			long productoId = teclado.path("id").asLong();
			assertThat(teclado.path("stockDisponible").asInt()).isEqualTo(18);
			long clienteId = json(enviar("GET", "/clientes?email=ana.demo", null)).get(0).path("id").asLong();
			String compra = "{\"clienteId\":%d,\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(clienteId, productoId);
			var pedido = enviar("POST", "/pedidos", compra);
			assertThat(pedido.statusCode()).isEqualTo(201);
			long pedidoId = json(pedido).path("id").asLong();
			detener();
			iniciar();
			assertThat(json(enviar("GET", "/productos", null)).size()).isEqualTo(8);
			assertThat(json(enviar("GET", "/clientes", null)).size()).isEqualTo(3);
			assertThat(json(enviar("GET", "/pedidos", null)).size()).as("Sin duplicados tras reiniciar").isEqualTo(3);
			assertThat(json(enviar("GET", "/productos/" + productoId, null)).path("stockDisponible").asInt())
					.as("La compra se conserva sin reponer ni descontar nuevamente").isEqualTo(17);
			assertThat(enviar("GET", "/pedidos/" + pedidoId, null).statusCode()).isEqualTo(200);
		} finally {
			detener();
		}
	}

	private void prepararPuertoYValidarBase() throws Exception {
		String url = System.getProperty("stockflow.test.dbUrl", "");
		assertThat(url).matches("jdbc:postgresql://[^/]+/stockflow_test(\\?.*)?");
		try (ServerSocket socket = new ServerSocket()) {
			socket.bind(new InetSocketAddress("127.0.0.1", 0));
			port = socket.getLocalPort();
		}
	}

	private long crearProducto(String codigo, int precio) throws Exception {
		String cuerpo = "{\"nombre\":\"%s\",\"codigo\":\"%s\",\"precio\":%d,\"stockDisponible\":10,\"stockMinimo\":9}"
				.formatted(codigo, codigo, precio);
		var respuesta = enviar("POST", "/productos", cuerpo);
		assertThat(respuesta.statusCode()).as("Producto " + codigo + " creado").isEqualTo(201);
		return json(respuesta).path("id").asLong();
	}

	private void comprobarStock(long id, String contexto) throws Exception {
		var respuesta = enviar("GET", "/productos/" + id, null);
		assertThat(respuesta.statusCode()).as(contexto).isEqualTo(200);
		assertThat(json(respuesta).path("stockDisponible").asInt()).as(contexto).isEqualTo(8);
	}

	private String pedido(long cliente, long a, long b, int cantidadB) {
		return "{\"clienteId\":%d,\"items\":[{\"productoId\":%d,\"cantidad\":2},{\"productoId\":%d,\"cantidad\":%d}]}"
				.formatted(cliente, a, b, cantidadB);
	}

	private void iniciar() throws Exception {
		Path log = Path.of(System.getProperty("stockflow.test.log"));
		Files.createDirectories(log.getParent());
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		api = new ProcessBuilder(java, "-jar", System.getProperty("stockflow.test.jar"),
				"--server.address=127.0.0.1", "--server.port=" + port,
				"--spring.datasource.url=" + System.getProperty("stockflow.test.dbUrl"),
				"--spring.datasource.username=" + System.getProperty("stockflow.test.dbUser"),
				"--spring.datasource.password=" + System.getProperty("stockflow.test.dbPassword"),
				"--debug=false")
				.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
		long limite = System.nanoTime() + Duration.ofSeconds(45).toNanos();
		while (System.nanoTime() < limite) {
			if (!api.isAlive()) throw new IllegalStateException("El JAR terminó durante el arranque; revise " + log);
			try {
				if (enviar("GET", "/productos", null).statusCode() == 200) return;
			} catch (IOException esperadoDuranteArranque) {
				// La conexión aún no está disponible; el límite evita una espera indefinida.
			}
			Thread.sleep(200);
		}
		throw new IllegalStateException("El JAR no arrancó; revise " + log);
	}

	private void detener() throws InterruptedException {
		if (api != null && api.isAlive()) {
			api.destroy();
			if (!api.waitFor(10, TimeUnit.SECONDS)) {
				api.destroyForcibly();
				assertThat(api.waitFor(5, TimeUnit.SECONDS)).as("Proceso JAR detenido").isTrue();
			}
		}
		api = null;
	}

	private JsonNode json(HttpResponse<String> respuesta) { return JSON.readTree(respuesta.body()); }
	private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + ruta))
				.timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
				.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
