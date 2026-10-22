package cl.bello.stockflow.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Carga los ejemplos después del esquema y antes de que la API esté lista. */
@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(name = "stockflow.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements InitializingBean {
	private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);
	private final DataSource dataSource;

	public DemoDataInitializer(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String sql = new ClassPathResource("demo-data.sql").getContentAsString(StandardCharsets.UTF_8);
		// PostgreSQL interpreta el bloque DO completo; no se separa por sus puntos y coma internos.
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
		LOGGER.info("Datos de demostración listos; se conservan los registros y cambios existentes");
	}
}
