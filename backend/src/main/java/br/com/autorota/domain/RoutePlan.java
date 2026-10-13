package br.com.autorota.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "route_plan")
public class RoutePlan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "name", length = 120)
	private String name;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "cep", column = @Column(name = "origin_cep", nullable = false, length = 8)),
			@AttributeOverride(name = "street", column = @Column(name = "origin_street", length = 200)),
			@AttributeOverride(name = "number", column = @Column(name = "origin_number", nullable = false, length = 20)),
			@AttributeOverride(name = "complement", column = @Column(name = "origin_complement", length = 100)),
			@AttributeOverride(name = "neighborhood", column = @Column(name = "origin_neighborhood", length = 100)),
			@AttributeOverride(name = "city", column = @Column(name = "origin_city", nullable = false, length = 100)),
			@AttributeOverride(name = "uf", column = @Column(name = "origin_uf", nullable = false, length = 2)),
			@AttributeOverride(name = "lat", column = @Column(name = "origin_lat", nullable = false)),
			@AttributeOverride(name = "lng", column = @Column(name = "origin_lng", nullable = false)) })
	private Address origin;

	@Column(name = "round_trip", nullable = false)
	private boolean roundTrip = true;

	@Column(name = "consumption_km_per_l", nullable = false, precision = 6, scale = 2)
	private BigDecimal consumptionKmPerL;

	@Column(name = "fuel_price_per_l", nullable = false, precision = 8, scale = 3)
	private BigDecimal fuelPricePerL;

	@Column(name = "total_distance_m", nullable = false)
	private int totalDistanceM;

	@Column(name = "total_duration_s", nullable = false)
	private int totalDurationS;

	@Column(name = "fuel_liters", nullable = false, precision = 10, scale = 3)
	private BigDecimal fuelLiters;

	@Column(name = "fuel_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal fuelCost;

	/** Só preenchido quando {@code roundTrip = true}. */
	@Column(name = "return_distance_m")
	private Integer returnDistanceM;

	/** Só preenchido quando {@code roundTrip = true}. */
	@Column(name = "return_duration_s")
	private Integer returnDurationS;

	/** GeoJSON (LineString) devolvido pelo OSRM /route. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "route_geometry")
	private String routeGeometry;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private RoutePlanStatus status = RoutePlanStatus.PLANEJADA;

	@OneToMany(mappedBy = "routePlan", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("visitOrder")
	private List<RouteStop> stops = new ArrayList<>();

	protected RoutePlan() {
	}

	public RoutePlan(String name, Address origin, boolean roundTrip, BigDecimal consumptionKmPerL,
			BigDecimal fuelPricePerL) {
		this.name = name;
		this.origin = origin;
		this.roundTrip = roundTrip;
		this.consumptionKmPerL = consumptionKmPerL;
		this.fuelPricePerL = fuelPricePerL;
		this.createdAt = Instant.now();
	}

	public void addStop(RouteStop stop) {
		stop.attachTo(this);
		stops.add(stop);
	}

	public void applyTotals(int totalDistanceM, int totalDurationS, BigDecimal fuelLiters, BigDecimal fuelCost,
			Integer returnDistanceM, Integer returnDurationS, String routeGeometry) {
		this.totalDistanceM = totalDistanceM;
		this.totalDurationS = totalDurationS;
		this.fuelLiters = fuelLiters;
		this.fuelCost = fuelCost;
		this.returnDistanceM = returnDistanceM;
		this.returnDurationS = returnDurationS;
		this.routeGeometry = routeGeometry;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Address getOrigin() {
		return origin;
	}

	public boolean isRoundTrip() {
		return roundTrip;
	}

	public BigDecimal getConsumptionKmPerL() {
		return consumptionKmPerL;
	}

	public BigDecimal getFuelPricePerL() {
		return fuelPricePerL;
	}

	public int getTotalDistanceM() {
		return totalDistanceM;
	}

	public int getTotalDurationS() {
		return totalDurationS;
	}

	public BigDecimal getFuelLiters() {
		return fuelLiters;
	}

	public BigDecimal getFuelCost() {
		return fuelCost;
	}

	public Integer getReturnDistanceM() {
		return returnDistanceM;
	}

	public Integer getReturnDurationS() {
		return returnDurationS;
	}

	public String getRouteGeometry() {
		return routeGeometry;
	}

	public RoutePlanStatus getStatus() {
		return status;
	}

	public void setStatus(RoutePlanStatus status) {
		this.status = status;
	}

	public List<RouteStop> getStops() {
		return Collections.unmodifiableList(stops);
	}

}
