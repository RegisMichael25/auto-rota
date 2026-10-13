package br.com.autorota.service;

import br.com.autorota.domain.Address;
import br.com.autorota.domain.CoordSource;

public record ResolvedAddress(Address address, CoordSource coordSource) {
}
