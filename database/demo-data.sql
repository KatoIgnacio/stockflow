-- Datos ficticios para demostrar StockFlow. Carga automática al arrancar la API.
-- Puede desactivarse mediante DEMO_DATA_ENABLED=false.
-- Requiere las tablas de src/main/resources/schema.sql.
-- No borra registros ni modifica productos/clientes existentes al repetir la carga.
-- Los pedidos de muestra descuentan stock una sola vez y conservan el precio de compra.
BEGIN;

-- Evita que dos ejecuciones simultáneas dupliquen los pedidos de este mismo ejemplo.
SELECT pg_advisory_xact_lock(20261021, 1);

INSERT INTO clientes (nombre, email, activo) VALUES
    ('Ana Torres - Demo', 'ana.demo@stockflow.example', TRUE),
    ('Bruno Rojas - Demo', 'bruno.demo@stockflow.example', TRUE),
    ('Carla Vega - Demo', 'carla.demo@stockflow.example', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO productos (nombre, codigo, precio, stock_disponible, stock_minimo, activo) VALUES
    ('Teclado mecanico', 'DEMO-TEC-001', 29990.00, 20, 5, TRUE),
    ('Mouse inalambrico', 'DEMO-MOU-001', 14990.00, 15, 4, TRUE),
    ('Monitor 24 pulgadas', 'DEMO-MON-001', 129990.00, 6, 2, TRUE),
    ('Cable USB-C', 'DEMO-USB-001', 4990.00, 3, 5, TRUE),
    ('Hub USB-C', 'DEMO-HUB-001', 24990.00, 0, 3, TRUE),
    ('Audifonos con microfono', 'DEMO-AUD-001', 39990.00, 10, 3, TRUE),
    ('Camara web', 'DEMO-WEB-001', 34990.00, 2, 2, TRUE),
    ('Soporte para notebook', 'DEMO-NOT-001', 19990.00, 8, 2, TRUE)
ON CONFLICT (codigo) DO NOTHING;

DO $stockflow_demo$
DECLARE
    pedido_demo RECORD;
    item_demo RECORD;
    producto_demo RECORD;
    cliente_demo_id BIGINT;
    cliente_demo_activo BOOLEAN;
    pedido_demo_id BIGINT;
    total_demo NUMERIC(12, 2);
BEGIN
    FOR pedido_demo IN
        SELECT * FROM (VALUES
            ('ana.demo@stockflow.example', TIMESTAMP '2026-10-20 10:30:00',
             ARRAY['DEMO-TEC-001', 'DEMO-MOU-001'], ARRAY[2, 1]),
            ('bruno.demo@stockflow.example', TIMESTAMP '2026-10-21 09:15:00',
             ARRAY['DEMO-MON-001', 'DEMO-AUD-001'], ARRAY[1, 2])
        ) AS ejemplos(email, fecha, codigos, cantidades)
    LOOP
        SELECT id, activo INTO cliente_demo_id, cliente_demo_activo
        FROM clientes WHERE email = pedido_demo.email;

        -- Cliente y fecha fijos identifican cada pedido ficticio, sin usar IDs fijos.
        IF EXISTS (SELECT 1 FROM pedidos
                   WHERE cliente_id = cliente_demo_id AND fecha = pedido_demo.fecha) THEN
            CONTINUE;
        END IF;
        IF cliente_demo_id IS NULL OR NOT cliente_demo_activo THEN
            RAISE EXCEPTION 'Cliente demo inexistente o inactivo: %', pedido_demo.email;
        END IF;

        -- Mismo orden de bloqueo por ID que utiliza el servicio de pedidos.
        PERFORM id FROM productos WHERE codigo = ANY(pedido_demo.codigos)
        ORDER BY id FOR UPDATE;

        INSERT INTO pedidos (cliente_id, fecha, estado, total)
        VALUES (cliente_demo_id, pedido_demo.fecha, 'PENDIENTE', 0)
        RETURNING id INTO pedido_demo_id;
        total_demo := 0;

        FOR item_demo IN
            SELECT * FROM unnest(pedido_demo.codigos, pedido_demo.cantidades)
                AS lineas(codigo, cantidad)
        LOOP
            SELECT id, precio, stock_disponible, activo INTO producto_demo
            FROM productos WHERE codigo = item_demo.codigo;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'Producto demo inexistente: %', item_demo.codigo;
            END IF;
            IF NOT producto_demo.activo OR producto_demo.stock_disponible < item_demo.cantidad THEN
                RAISE EXCEPTION 'Producto demo inactivo o sin stock suficiente: %', item_demo.codigo;
            END IF;

            INSERT INTO detalles_pedido (pedido_id, producto_id, cantidad, precio_unitario)
            VALUES (pedido_demo_id, producto_demo.id, item_demo.cantidad, producto_demo.precio);
            UPDATE productos SET stock_disponible = stock_disponible - item_demo.cantidad
            WHERE id = producto_demo.id;
            total_demo := total_demo + producto_demo.precio * item_demo.cantidad;
        END LOOP;

        UPDATE pedidos SET total = total_demo WHERE id = pedido_demo_id;
    END LOOP;
END;
$stockflow_demo$;

COMMIT;
