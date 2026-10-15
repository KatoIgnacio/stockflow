package cl.bello.stockflow.repository;

import cl.bello.stockflow.entity.Pedido;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	@Query("""
			select distinct p from Pedido p
			join fetch p.cliente
			left join fetch p.detalles d
			left join fetch d.producto
			where p.id = :id
			""")
	Optional<Pedido> buscarDetallePorId(@Param("id") Long id);

	@Query("""
			select distinct p from Pedido p
			join fetch p.cliente
			left join fetch p.detalles d
			left join fetch d.producto
			where p.cliente.id = :clienteId
			order by p.fecha desc
			""")
	List<Pedido> buscarPorCliente(@Param("clienteId") Long clienteId);

	@EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
	List<Pedido> findAllByOrderByFechaDesc();

	@EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
	List<Pedido> findByFechaGreaterThanEqualOrderByFechaDesc(LocalDateTime inicio);

	@EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
	List<Pedido> findByFechaLessThanOrderByFechaDesc(LocalDateTime finExclusivo);

	@EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
	List<Pedido> findByFechaGreaterThanEqualAndFechaLessThanOrderByFechaDesc(
			LocalDateTime inicio,
			LocalDateTime finExclusivo);
}
