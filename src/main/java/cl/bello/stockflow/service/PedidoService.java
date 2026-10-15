package cl.bello.stockflow.service;

import cl.bello.stockflow.dto.DetallePedidoResponse;
import cl.bello.stockflow.dto.ItemPedidoRequest;
import cl.bello.stockflow.dto.PedidoRequest;
import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.entity.Cliente;
import cl.bello.stockflow.entity.DetallePedido;
import cl.bello.stockflow.entity.EstadoPedido;
import cl.bello.stockflow.entity.Pedido;
import cl.bello.stockflow.entity.Producto;
import cl.bello.stockflow.exception.ConflictoException;
import cl.bello.stockflow.exception.RecursoNoEncontradoException;
import cl.bello.stockflow.repository.ClienteRepository;
import cl.bello.stockflow.repository.PedidoRepository;
import cl.bello.stockflow.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

	private final PedidoRepository pedidoRepository;
	private final ClienteRepository clienteRepository;
	private final ProductoRepository productoRepository;

	public PedidoService(
			PedidoRepository pedidoRepository,
			ClienteRepository clienteRepository,
			ProductoRepository productoRepository) {
		this.pedidoRepository = pedidoRepository;
		this.clienteRepository = clienteRepository;
		this.productoRepository = productoRepository;
	}

	@Transactional
	public PedidoResponse crear(PedidoRequest request) {
		Cliente cliente = clienteRepository.findByIdAndActivoTrue(request.clienteId())
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"Cliente no encontrado o inactivo: " + request.clienteId()));

		Map<Long, Integer> cantidades = agruparCantidades(request.items());
		List<Long> idsOrdenados = cantidades.keySet().stream().sorted().toList();
		List<Producto> productos = productoRepository.buscarActivosPorIdsConBloqueo(idsOrdenados);
		if (productos.size() != idsOrdenados.size()) {
			throw new RecursoNoEncontradoException("Uno o más productos no existen o están inactivos");
		}

		for (Producto producto : productos) {
			int cantidad = cantidades.get(producto.getId());
			if (!producto.tieneStock(cantidad)) {
				throw new ConflictoException(
						"Stock insuficiente para " + producto.getCodigo()
								+ ": disponible " + producto.getStockDisponible()
								+ ", solicitado " + cantidad);
			}
		}

		Pedido pedido = new Pedido(cliente, LocalDateTime.now(), EstadoPedido.PENDIENTE);
		BigDecimal total = BigDecimal.ZERO;
		for (Producto producto : productos) {
			int cantidad = cantidades.get(producto.getId());
			DetallePedido detalle = new DetallePedido(producto, cantidad, producto.getPrecio());
			pedido.agregarDetalle(detalle);
			total = total.add(detalle.calcularSubtotal());
			producto.descontarStock(cantidad);
		}
		pedido.setTotal(total);

		return convertir(pedidoRepository.saveAndFlush(pedido));
	}

	@Transactional(readOnly = true)
	public PedidoResponse obtener(Long id) {
		return convertir(pedidoRepository.buscarDetallePorId(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado: " + id)));
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listarPorCliente(Long clienteId) {
		if (!clienteRepository.existsById(clienteId)) {
			throw new RecursoNoEncontradoException("Cliente no encontrado: " + clienteId);
		}
		return pedidoRepository.buscarPorCliente(clienteId).stream().map(this::convertir).toList();
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listarPorPeriodo(LocalDate desde, LocalDate hasta) {
		validarPeriodo(desde, hasta);
		LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
		LocalDateTime finExclusivo = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
		List<Pedido> pedidos;
		if (inicio == null && finExclusivo == null) {
			pedidos = pedidoRepository.findAllByOrderByFechaDesc();
		} else if (finExclusivo == null) {
			pedidos = pedidoRepository.findByFechaGreaterThanEqualOrderByFechaDesc(inicio);
		} else if (inicio == null) {
			pedidos = pedidoRepository.findByFechaLessThanOrderByFechaDesc(finExclusivo);
		} else {
			pedidos = pedidoRepository.findByFechaGreaterThanEqualAndFechaLessThanOrderByFechaDesc(
					inicio, finExclusivo);
		}
		return pedidos.stream()
				.map(this::convertir)
				.toList();
	}

	private Map<Long, Integer> agruparCantidades(List<ItemPedidoRequest> items) {
		Map<Long, Integer> cantidades = new TreeMap<>();
		try {
			for (ItemPedidoRequest item : items) {
				cantidades.merge(item.productoId(), item.cantidad(), Math::addExact);
			}
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException("La cantidad solicitada es demasiado grande");
		}
		return cantidades;
	}

	private void validarPeriodo(LocalDate desde, LocalDate hasta) {
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
		}
	}

	PedidoResponse convertir(Pedido pedido) {
		List<DetallePedidoResponse> items = pedido.getDetalles().stream()
				.map(detalle -> new DetallePedidoResponse(
						detalle.getProducto().getId(),
						detalle.getProducto().getCodigo(),
						detalle.getProducto().getNombre(),
						detalle.getCantidad(),
						detalle.getPrecioUnitario(),
						detalle.calcularSubtotal()))
				.toList();

		return new PedidoResponse(
				pedido.getId(),
				pedido.getCliente().getId(),
				pedido.getCliente().getNombre(),
				pedido.getFecha(),
				pedido.getEstado(),
				pedido.getTotal(),
				items);
	}
}
