package cl.bello.stockflow.service;

import cl.bello.stockflow.dto.PedidoResponse;
import cl.bello.stockflow.dto.ReporteInventarioResponse;
import cl.bello.stockflow.dto.ReportePedidosResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReporteService {

	private final ProductoService productoService;
	private final PedidoService pedidoService;

	public ReporteService(ProductoService productoService, PedidoService pedidoService) {
		this.productoService = productoService;
		this.pedidoService = pedidoService;
	}

	public ReporteInventarioResponse generarInventario() {
		return new ReporteInventarioResponse(LocalDateTime.now(), productoService.listar(null, null));
	}

	public ReportePedidosResponse generarPedidos(LocalDate desde, LocalDate hasta) {
		List<PedidoResponse> pedidos = pedidoService.listarPorPeriodo(desde, hasta);
		BigDecimal montoTotal = pedidos.stream()
				.map(PedidoResponse::total)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new ReportePedidosResponse(LocalDateTime.now(), desde, hasta, montoTotal, pedidos);
	}
}
