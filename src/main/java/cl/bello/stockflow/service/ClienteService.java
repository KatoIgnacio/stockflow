package cl.bello.stockflow.service;

import cl.bello.stockflow.dto.ClienteRequest;
import cl.bello.stockflow.dto.ClienteResponse;
import cl.bello.stockflow.entity.Cliente;
import cl.bello.stockflow.exception.ConflictoException;
import cl.bello.stockflow.exception.RecursoNoEncontradoException;
import cl.bello.stockflow.repository.ClienteRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClienteService {

	private final ClienteRepository clienteRepository;

	public ClienteService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	@Transactional
	public ClienteResponse crear(ClienteRequest request) {
		String email = normalizarEmail(request.email());
		if (clienteRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictoException("Ya existe un cliente con el correo " + email);
		}

		Cliente cliente = new Cliente(request.nombre().trim(), email);
		return convertir(clienteRepository.save(cliente));
	}

	@Transactional(readOnly = true)
	public List<ClienteResponse> listar(String nombre, String email) {
		String nombreNormalizado = normalizarFiltro(nombre);
		String emailNormalizado = normalizarFiltro(email);
		List<Cliente> clientes;
		if (nombreNormalizado == null && emailNormalizado == null) {
			clientes = clienteRepository.findByActivoTrueOrderByNombreAsc();
		} else if (emailNormalizado == null) {
			clientes = clienteRepository.findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(
					nombreNormalizado);
		} else if (nombreNormalizado == null) {
			clientes = clienteRepository.findByActivoTrueAndEmailContainingIgnoreCaseOrderByNombreAsc(
					emailNormalizado);
		} else {
			clientes = clienteRepository
					.findByActivoTrueAndNombreContainingIgnoreCaseAndEmailContainingIgnoreCaseOrderByNombreAsc(
							nombreNormalizado, emailNormalizado);
		}
		return clientes
				.stream()
				.map(this::convertir)
				.toList();
	}

	@Transactional(readOnly = true)
	public ClienteResponse obtener(Long id) {
		return convertir(buscarActivo(id));
	}

	@Transactional
	public ClienteResponse actualizar(Long id, ClienteRequest request) {
		Cliente cliente = buscarActivo(id);
		String email = normalizarEmail(request.email());
		if (clienteRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw new ConflictoException("Ya existe un cliente con el correo " + email);
		}

		cliente.setNombre(request.nombre().trim());
		cliente.setEmail(email);
		return convertir(cliente);
	}

	@Transactional
	public void desactivar(Long id) {
		buscarActivo(id).desactivar();
	}

	private Cliente buscarActivo(Long id) {
		return clienteRepository.findByIdAndActivoTrue(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado: " + id));
	}

	private String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizarFiltro(String valor) {
		return StringUtils.hasText(valor) ? valor.trim() : null;
	}

	private ClienteResponse convertir(Cliente cliente) {
		return new ClienteResponse(cliente.getId(), cliente.getNombre(), cliente.getEmail(), cliente.isActivo());
	}
}
