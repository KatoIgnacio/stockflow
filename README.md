# StockFlow

API REST para gestionar productos, clientes, inventario y pedidos de GlobalRetail S.A.

**Integrantes:** Kato Bello Martínez y Jaime Palavecino Suárez. Universidad del Bío-Bío, Desarrollo Web Orientado a Objetos.

Desarrollada con Java 21, Spring Boot, Spring Data JPA, Bean Validation, PostgreSQL y Gradle Wrapper.

## Requisitos

- JDK 21 instalado y configurado en `JAVA_HOME`.
- Docker Desktop iniciado, con contenedores Linux.
- Git y conexión a internet para descargar las dependencias la primera vez.

## Iniciar el proyecto

```powershell
git clone https://github.com/KatoIgnacio/stockflow.git
cd stockflow
docker compose up -d --wait
.\gradlew.bat bootRun
```

La API queda disponible en **http://localhost:8081** y responde en JSON. Para comprobar el arranque, abrir:

- http://localhost:8081/api/productos
- http://localhost:8081/api/clientes
- http://localhost:8081/api/pedidos

En Linux/macOS se utiliza `./gradlew` en lugar de `.\gradlew.bat`. No es necesario instalar Gradle por separado.

Para detener la API, presionar `Ctrl+C`. Para detener PostgreSQL conservando los datos:

```powershell
docker compose stop
```

## Base de datos y ejemplos automáticos

Docker crea la base `stockflow`. Al arrancar la API, `src/main/resources/schema.sql` crea las tablas, relaciones, restricciones e índices; Hibernate valida el esquema.

Después se cargan automáticamente **8 productos, 3 clientes y 2 pedidos con 4 detalles**, sin ejecutar SQL manualmente. Son datos ficticios identificados con códigos `DEMO-` y correos `@stockflow.example`.

Los ejemplos incluyen stock normal, stock bajo, un producto agotado, clientes con y sin pedidos y compras del 20 y 21 de octubre de 2026. Los pedidos iniciales suman `284940.00` y su inventario ya está descontado.

El script `database/demo-data.sql` se incluye dentro del JAR y lo ejecuta `DemoDataInitializer`. Reiniciar no duplica los pedidos, no descuenta stock nuevamente ni restablece los cambios realizados al catálogo. Los registros anteriores se conservan y los IDs se generan normalmente.

El volumen Docker mantiene los datos entre reinicios. Para usar PostgreSQL instalado directamente, `database/create-database.sql` crea la base vacía y las tablas se crean al iniciar la API.

## Configuración

| Variable | Valor predeterminado |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/stockflow` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `SERVER_PORT` | `8081` |
| `DEMO_DATA_ENABLED` | `true` |

Las credenciales predeterminadas son para desarrollo local. Ejemplo de configuración en PowerShell:

```powershell
$env:SERVER_PORT = "8082"
```

Para no agregar ejemplos al arrancar, establecer `$env:DEMO_DATA_ENABLED = "false"`. Esto conserva los datos que ya estén guardados.

## Endpoints

| Método | Ruta | Función / filtros |
|---|---|---|
| POST | `/api/productos` | Registrar producto |
| GET | `/api/productos` | Listar activos; `nombre`, `codigo` |
| GET | `/api/productos/{id}` | Consultar producto activo |
| PUT | `/api/productos/{id}` | Actualizar producto |
| DELETE | `/api/productos/{id}` | Desactivar producto |
| GET | `/api/productos/stock-bajo` | Stock inferior al mínimo |
| POST | `/api/clientes` | Registrar cliente |
| GET | `/api/clientes` | Listar activos; `nombre`, `email` |
| GET | `/api/clientes/{id}` | Consultar cliente activo |
| PUT | `/api/clientes/{id}` | Actualizar cliente |
| DELETE | `/api/clientes/{id}` | Desactivar cliente |
| POST | `/api/pedidos` | Crear pedido transaccional |
| GET | `/api/pedidos/{id}` | Consultar pedido y detalles |
| GET | `/api/pedidos` | Listar; `desde`, `hasta` |
| GET | `/api/clientes/{id}/pedidos` | Historial del cliente |
| GET | `/api/reportes/inventario` | Reporte JSON de productos activos |
| GET | `/api/reportes/pedidos` | Reporte JSON y monto total; `desde`, `hasta` |

Los filtros de texto son parciales, combinables e ignoran mayúsculas. Las fechas usan `AAAA-MM-DD` e incluyen todo el día de `hasta`. Ejemplo: `/api/reportes/pedidos?desde=2026-10-20&hasta=2026-10-21`.

## Registrar un pedido

Enviar a `POST /api/pedidos` con `Content-Type: application/json`, sustituyendo los IDs por los obtenidos al consultar clientes y productos:

```json
{
  "clienteId": 1,
  "items": [
    { "productoId": 1, "cantidad": 2 },
    { "productoId": 2, "cantidad": 1 }
  ]
}
```

El servidor agrupa productos repetidos, valida todas las existencias, calcula el total y crea el pedido en estado `PENDIENTE`. El registro y el descuento de stock se realizan en una sola transacción. Si un ítem no tiene stock, se rechaza el pedido completo sin modificar ningún inventario. El bloqueo pesimista evita la sobreventa entre pedidos simultáneos.

La arquitectura separa `Controller`, `Service` y `Repository`. Los DTOs definen el contrato JSON. Los importes usan `BigDecimal`; cada detalle conserva el precio de compra. Los métodos DELETE desactivan registros y mantienen el historial.

Respuestas principales: `200` consulta/actualización, `201` creación, `204` desactivación, `400` entrada inválida, `404` recurso inexistente o inactivo y `409` conflicto de stock o duplicados. Los errores incluyen `timestamp`, `status`, `error`, `mensaje`, `ruta` y `detalles`.

## Pruebas y ejecutable

Pruebas con H2 y generación del JAR:

```powershell
.\gradlew.bat --no-daemon clean check bootJar
```

Verificación completa con PostgreSQL real y reinicio del JAR:

```powershell
docker compose -f compose.test.yaml -p stockflow-verificacion up -d --wait
.\gradlew.bat --no-daemon clean check bootJar postgresTest jarSmokeTest
docker compose -f compose.test.yaml -p stockflow-verificacion down
```

Las pruebas PostgreSQL utilizan una instancia separada en `localhost:5433/stockflow_test`; vacían únicamente esa base. Se puede configurar con `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD`, manteniendo el nombre `stockflow_test`.

Se comprueban CRUD, filtros, validaciones, reportes, borrado lógico, precios históricos, pedidos con varios ítems, rechazo completo, concurrencia, carga automática desde una base vacía y persistencia tras reiniciar. Las pruebas de servicios usan sus propios datos; las del JAR comprueban los ejemplos automáticos.

- Reportes HTML: `build/reports/tests/test/index.html`, `build/reports/tests/postgresTest/index.html` y `build/reports/tests/jarSmokeTest/index.html`.
- Ejecutable: `build/libs/stockflow-0.0.1-SNAPSHOT.jar`. Se inicia con `java -jar build/libs/stockflow-0.0.1-SNAPSHOT.jar` y requiere PostgreSQL disponible.

Los archivos generados en `build/` están excluidos de Git.
