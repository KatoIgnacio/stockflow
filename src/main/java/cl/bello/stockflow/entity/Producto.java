package cl.bello.stockflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(nullable = false, unique = true, length = 60)
	private String codigo;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(name = "stock_disponible", nullable = false)
	private int stockDisponible;

	@Column(name = "stock_minimo", nullable = false)
	private int stockMinimo;

	@Column(nullable = false)
	private boolean activo = true;

	protected Producto() {
	}

	public Producto(String nombre, String codigo, BigDecimal precio, int stockDisponible, int stockMinimo) {
		this.nombre = nombre;
		this.codigo = codigo;
		this.precio = precio;
		this.stockDisponible = stockDisponible;
		this.stockMinimo = stockMinimo;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public void setPrecio(BigDecimal precio) {
		this.precio = precio;
	}

	public int getStockDisponible() {
		return stockDisponible;
	}

	public void setStockDisponible(int stockDisponible) {
		this.stockDisponible = stockDisponible;
	}

	public int getStockMinimo() {
		return stockMinimo;
	}

	public void setStockMinimo(int stockMinimo) {
		this.stockMinimo = stockMinimo;
	}

	public boolean isActivo() {
		return activo;
	}

	public void desactivar() {
		this.activo = false;
	}

	public boolean tieneStock(int cantidad) {
		return stockDisponible >= cantidad;
	}

	public void descontarStock(int cantidad) {
		this.stockDisponible -= cantidad;
	}
}
