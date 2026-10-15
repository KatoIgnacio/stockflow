package cl.bello.stockflow.controller;

import cl.bello.stockflow.dto.ReporteInventarioResponse;
import cl.bello.stockflow.dto.ReportePedidosResponse;
import cl.bello.stockflow.service.ReporteService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

	private final ReporteService reporteService;

	public ReporteController(ReporteService reporteService) {
		this.reporteService = reporteService;
	}

	@GetMapping("/inventario")
	public ReporteInventarioResponse inventario() {
		return reporteService.generarInventario();
	}

	@GetMapping("/pedidos")
	public ReportePedidosResponse pedidos(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return reporteService.generarPedidos(desde, hasta);
	}
}
