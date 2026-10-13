package br.com.autorota.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "route_stop")
public class RouteStop {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "route_plan_id", nullable = false)
	private RoutePlan routePlan;

	/** Posição em que o destino foi informado (1 a 10). */
	@Column(name = "input_order", nullable = false)
	private int inputOrder;

	/** Posição na ordem otimizada de visita (1 a 10). */
	@Column(name = "visit_order", nullable = false)
	private int visitOrder;

	@Embedded
	private Address address;

	@Enumerated(EnumType.STRING)
	@Column(name = "coord_source", nullable = false, length = 20)
	private CoordSource coordSource;

	/** Distância do trecho anterior (origem ou parada anterior) até esta parada. */
	@Column(name = "leg_distance_m", nullable = false)
	private int legDistanceM;

	@Column(name = "leg_duration_s", nullable = false)
	private int legDurationS;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private StopStatus status = StopStatus.PENDENTE;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "delivery_note", length = 500)
	private String deliveryNote;

	protected RouteStop() {
	}

	public RouteStop(int inputOrder, int visitOrder, Address address, CoordSource coordSource, int legDistanceM,
			int legDurationS) {
		this.inputOrder = inputOrder;
		this.visitOrder = visitOrder;
		this.address = address;
		this.coordSource = coordSource;
		this.legDistanceM = legDistanceM;
		this.legDurationS = legDurationS;
	}

	void attachTo(RoutePlan routePlan) {
		this.routePlan = routePlan;
	}

	public void markDelivered(Instant when, String note) {
		this.status = StopStatus.ENTREGUE;
		this.deliveredAt = when;
		this.deliveryNote = note;
	}

	public void markNotDelivered(String note) {
		this.status = StopStatus.NAO_ENTREGUE;
		this.deliveredAt = null;
		this.deliveryNote = note;
	}

	public UUID getId() {
		return id;
	}

	public RoutePlan getRoutePlan() {
		return routePlan;
	}

	public int getInputOrder() {
		return inputOrder;
	}

	public int getVisitOrder() {
		return visitOrder;
	}

	public Address getAddress() {
		return address;
	}

	public CoordSource getCoordSource() {
		return coordSource;
	}

	public int getLegDistanceM() {
		return legDistanceM;
	}

	public int getLegDurationS() {
		return legDurationS;
	}

	public StopStatus getStatus() {
		return status;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public String getDeliveryNote() {
		return deliveryNote;
	}

}
