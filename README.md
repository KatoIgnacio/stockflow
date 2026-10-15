# StockFlow

API REST para gestionar productos, clientes, inventario y pedidos. El proyecto usa Java 21, Spring Boot, Spring Data JPA, Bean Validation y PostgreSQL.

## Base de datos

En este equipo está disponible Docker Desktop. Después de abrirlo, la forma más breve de iniciar PostgreSQL es:

```powershell
docker compose up -d
```

Como alternativa, si PostgreSQL está instalado directamente:

1. Crear la base:

   ```powershell
   psql -U postgres -f .\database\create-database.sql
   ```

2. Si el usuario o la contraseña son distintos, definir `DB_USER`, `DB_PASSWORD` y, si corresponde, `DB_URL`.

3. Al iniciar, `schema.sql` crea las tablas, restricciones e índices. Hibernate valida que el modelo Java coincida con el esquema.

Para detener el contenedor sin eliminar sus datos:

```powershell
docker compose stop
```

## Ejecutar

```powershell
.\gradlew.bat bootRun
```

La API queda disponible en `http://localhost:8081`. Se puede cambiar el puerto con `SERVER_PORT`.

## Verificar

```powershell
.\gradlew.bat --no-daemon check bootJar
```

## Flujo principal

1. Registrar productos en `POST /api/productos`.
2. Registrar clientes en `POST /api/clientes`.
3. Crear un pedido en `POST /api/pedidos` usando `clienteId` e `items`.
4. El servicio bloquea los productos, valida todas las existencias y crea o rechaza el pedido como una única transacción.

Ejemplo:

```json
{
  "clienteId": 1,
  "items": [
    { "productoId": 1, "cantidad": 2 }
  ]
}
```
