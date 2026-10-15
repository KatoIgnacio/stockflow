package cl.bello.stockflow.repository;

import cl.bello.stockflow.entity.Producto;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

	Optional<Producto> findByIdAndActivoTrue(Long id);

	boolean existsByCodigoIgnoreCase(String codigo);

	boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

	List<Producto> findByActivoTrueOrderByNombreAsc();

	List<Producto> findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);

	List<Producto> findByActivoTrueAndCodigoContainingIgnoreCaseOrderByNombreAsc(String codigo);

	List<Producto> findByActivoTrueAndNombreContainingIgnoreCaseAndCodigoContainingIgnoreCaseOrderByNombreAsc(
			String nombre,
			String codigo);

	@Query("""
			select p from Producto p
			where p.activo = true and p.stockDisponible < p.stockMinimo
			order by p.stockDisponible, p.nombre
			""")
	List<Producto> buscarConStockBajo();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p from Producto p
			where p.activo = true and p.id in :ids
			order by p.id
			""")
	List<Producto> buscarActivosPorIdsConBloqueo(@Param("ids") List<Long> ids);
}
