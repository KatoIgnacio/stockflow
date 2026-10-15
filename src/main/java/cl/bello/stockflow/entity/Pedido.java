package cl.bello.stockflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Column(nullable = false)
	private LocalDateTime fecha;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EstadoPedido estado;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DetallePedido> detalles = new ArrayList<>();

	protected Pedido() {
	}

	public Pedido(Cliente cliente, LocalDateTime fecha, EstadoPedido estado) {
		this.cliente = cliente;
		this.fecha = fecha;
		this.estado = estado;
		this.total = BigDecimal.ZERO;
	}

	public Long getId() {
		return id;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public LocalDateTime getFecha() {
		return fecha;
	}

	public EstadoPedido getEstado() {
		return estado;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public List<DetallePedido> getDetalles() {
		return Collections.unmodifiableList(detalles);
	}

	public void agregarDetalle(DetallePedido detalle) {
		detalle.asignarPedido(this);
		detalles.add(detalle);
	}
}
