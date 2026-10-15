package cl.bello.stockflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
		@NotBlank @Size(max = 120) String nombre,
		@NotBlank @Email @Size(max = 180) String email) {
}
