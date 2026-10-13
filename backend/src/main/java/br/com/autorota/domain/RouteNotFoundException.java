package br.com.autorota.domain;

/** Não existe rota terrestre entre os pontos (ponto sem via próxima ou sem ligação por estrada). */
public class RouteNotFoundException extends DomainException {

	public RouteNotFoundException(String message) {
		super(message);
	}

}
