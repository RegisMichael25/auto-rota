package br.com.autorota.domain;

/** Normalização de CEP: mantém só os dígitos e exige exatamente 8. */
public final class Cep {

	private Cep() {
	}

	public static String normalize(String rawCep) {
		if (rawCep == null) {
			throw new InvalidCepException(null);
		}
		String digits = rawCep.replaceAll("\\D", "");
		if (digits.length() != 8) {
			throw new InvalidCepException(rawCep);
		}
		return digits;
	}

}
