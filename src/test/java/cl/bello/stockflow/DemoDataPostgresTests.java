package cl.bello.stockflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/** Comprueba el SQL de demostración con PostgreSQL real, únicamente en stockflow_test. */
class DemoDataPostgresTests {
	@Test
	void datosDemoSonCoherentesRepetiblesYPreservanRegistrosExistentes() throws Exception {
		String url = System.getProperty("spring.datasource.url", "");
		assertThat(url).matches("jdbc:postgresql://[^/]+/stockflow_test(\\?.*)?");
		try (Connection connection = DriverManager.getConnection(url,
				System.getProperty("spring.datasource.username"), System.getProperty("spring.datasource.password"));
			 Statement sql = connection.createStatement()) {
			sql.execute(Files.readString(Path.of("src/main/resources/schema.sql")));
			sql.execute("DELETE FROM detalles_pedido; DELETE FROM pedidos; DELETE FROM productos; DELETE FROM clientes;");
			sql.execute("""
					INSERT INTO productos(nombre,codigo,precio,stock_disponible,stock_minimo,activo)
					VALUES ('Registro anterior','CONTROL',123.45,7,1,TRUE);
					INSERT INTO clientes(nombre,email,activo) VALUES ('Anterior','anterior@ejemplo.cl',TRUE);
					""");
			String datos = Files.readString(Path.of("database/demo-data.sql"));
			sql.execute(datos);
			comprobarCantidades(sql);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-TEC-001'")).isEqualTo(18);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-MOU-001'")).isEqualTo(14);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-MON-001'")).isEqualTo(5);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-AUD-001'")).isEqualTo(8);
			assertThat(numero(sql, "SELECT count(*) FROM productos WHERE stock_disponible < stock_minimo")).isEqualTo(2);
			assertThat(decimal(sql, "SELECT sum(total) FROM pedidos")).isEqualByComparingTo("284940.00");
			assertThat(numero(sql, "SELECT count(*) FROM pedidos WHERE estado='PENDIENTE'")).isEqualTo(2);
			assertThat(numero(sql, "SELECT count(*) FROM pedidos p WHERE p.total <> (SELECT sum(d.cantidad*d.precio_unitario) FROM detalles_pedido d WHERE d.pedido_id=p.id)")).isZero();
			sql.execute(datos);
			comprobarCantidades(sql);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-TEC-001'")).isEqualTo(18);
			// Un operador modifica el catálogo después de cargar la demo: repetirla no debe sobrescribirlo.
			sql.execute("UPDATE productos SET stock_disponible=22, precio=31000 WHERE codigo='DEMO-TEC-001'");
			sql.execute(datos);
			comprobarCantidades(sql);
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='DEMO-TEC-001'")).isEqualTo(22);
			assertThat(decimal(sql, "SELECT precio FROM productos WHERE codigo='DEMO-TEC-001'")).isEqualByComparingTo("31000");
			assertThat(decimal(sql, "SELECT d.precio_unitario FROM detalles_pedido d JOIN productos p ON p.id=d.producto_id WHERE p.codigo='DEMO-TEC-001'")).isEqualByComparingTo("29990");
			assertThat(numero(sql, "SELECT stock_disponible FROM productos WHERE codigo='CONTROL'")).isEqualTo(7);
			assertThat(decimal(sql, "SELECT precio FROM productos WHERE codigo='CONTROL'")).isEqualByComparingTo("123.45");
		}
	}

	private void comprobarCantidades(Statement sql) throws Exception {
		assertThat(numero(sql, "SELECT count(*) FROM productos")).isEqualTo(9);
		assertThat(numero(sql, "SELECT count(*) FROM clientes")).isEqualTo(4);
		assertThat(numero(sql, "SELECT count(*) FROM pedidos")).isEqualTo(2);
		assertThat(numero(sql, "SELECT count(*) FROM detalles_pedido")).isEqualTo(4);
	}
	private long numero(Statement sql, String consulta) throws Exception {
		try (var filas = sql.executeQuery(consulta)) { filas.next(); return filas.getLong(1); }
	}
	private BigDecimal decimal(Statement sql, String consulta) throws Exception {
		try (var filas = sql.executeQuery(consulta)) { filas.next(); return filas.getBigDecimal(1); }
	}
}
