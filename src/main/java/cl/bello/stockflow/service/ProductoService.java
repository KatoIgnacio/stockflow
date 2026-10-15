package cl.bello.stockflow.service;

import cl.bello.stockflow.dto.ProductoRequest;
import cl.bello.stockflow.dto.ProductoResponse;
import cl.bello.stockflow.entity.Producto;
import cl.bello.stockflow.exception.ConflictoException;
import cl.bello.stockflow.exception.RecursoNoEncontradoException;
import cl.bello.stockflow.repository.ProductoRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductoService {

	private final ProductoRepository productoRepository;

	public ProductoService(ProductoRepository productoRepository) {
		this.productoRepository = productoRepository;
	}

	@Transactional
	public ProductoResponse crear(ProductoRequest request) {
		String codigo = normalizarCodigo(request.codigo());
		if (productoRepository.existsByCodigoIgnoreCase(codigo)) {
			throw new ConflictoException("Ya existe un producto con el código " + codigo);
		}

		Producto producto = new Producto(
				request.nombre().trim(),
				codigo,
				request.precio(),
				request.stockDisponible(),
				request.stockMinimo());
		return convertir(productoRepository.save(producto));
	}

	@Transactional(readOnly = true)
	public List<ProductoResponse> listar(String nombre, String codigo) {
		String nombreNormalizado = normalizarFiltro(nombre);
		String codigoNormalizado = normalizarFiltro(codigo);
		List<Producto> productos;
		if (nombreNormalizado == null && codigoNormalizado == null) {
			productos = productoRepository.findByActivoTrueOrderByNombreAsc();
		} else if (codigoNormalizado == null) {
			productos = productoRepository.findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(
					nombreNormalizado);
		} else if (nombreNormalizado == null) {
			productos = productoRepository.findByActivoTrueAndCodigoContainingIgnoreCaseOrderByNombreAsc(
					codigoNormalizado);
		} else {
			productos = productoRepository
					.findByActivoTrueAndNombreContainingIgnoreCaseAndCodigoContainingIgnoreCaseOrderByNombreAsc(
							nombreNormalizado, codigoNormalizado);
		}
		return productos
				.stream()
				.map(this::convertir)
				.toList();
	}

	@Transactional(readOnly = true)
	public ProductoResponse obtener(Long id) {
		return convertir(buscarActivo(id));
	}

	@Transactional
	public ProductoResponse actualizar(Long id, ProductoRequest request) {
		Producto producto = buscarActivo(id);
		String codigo = normalizarCodigo(request.codigo());
		if (productoRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
			throw new ConflictoException("Ya existe un producto con el código " + codigo);
		}

		producto.setNombre(request.nombre().trim());
		producto.setCodigo(codigo);
		producto.setPrecio(request.precio());
		producto.setStockDisponible(request.stockDisponible());
		producto.setStockMinimo(request.stockMinimo());
		return convertir(producto);
	}

	@Transactional
	public void desactivar(Long id) {
		buscarActivo(id).desactivar();
	}

	@Transactional(readOnly = true)
	public List<ProductoResponse> listarStockBajo() {
		return productoRepository.buscarConStockBajo().stream()
				.map(this::convertir)
				.toList();
	}

	private Producto buscarActivo(Long id) {
		return productoRepository.findByIdAndActivoTrue(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado: " + id));
	}

	private String normalizarCodigo(String codigo) {
		return codigo.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizarFiltro(String valor) {
		return StringUtils.hasText(valor) ? valor.trim() : null;
	}

	ProductoResponse convertir(Producto producto) {
		return new ProductoResponse(
				producto.getId(),
				producto.getNombre(),
				producto.getCodigo(),
				producto.getPrecio(),
				producto.getStockDisponible(),
				producto.getStockMinimo(),
				producto.isActivo());
	}
}
