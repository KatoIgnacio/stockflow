package cl.bello.stockflow.repository;

import cl.bello.stockflow.entity.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByIdAndActivoTrue(Long id);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	List<Cliente> findByActivoTrueOrderByNombreAsc();

	List<Cliente> findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);

	List<Cliente> findByActivoTrueAndEmailContainingIgnoreCaseOrderByNombreAsc(String email);

	List<Cliente> findByActivoTrueAndNombreContainingIgnoreCaseAndEmailContainingIgnoreCaseOrderByNombreAsc(
			String nombre,
			String email);
}
