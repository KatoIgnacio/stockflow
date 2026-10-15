package cl.bello.stockflow.controller;

import cl.bello.stockflow.dto.ApiError;
import cl.bello.stockflow.exception.ConflictoException;
import cl.bello.stockflow.exception.RecursoNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ApiError> noEncontrado(
			RecursoNoEncontradoException error,
			HttpServletRequest request) {
		return respuesta(HttpStatus.NOT_FOUND, error.getMessage(), request, List.of());
	}

	@ExceptionHandler({ConflictoException.class, DataIntegrityViolationException.class,
			PessimisticLockingFailureException.class})
	public ResponseEntity<ApiError> conflicto(Exception error, HttpServletRequest request) {
		String mensaje = error instanceof ConflictoException
				? error.getMessage()
				: "La operación entra en conflicto con el estado actual de los datos";
		return respuesta(HttpStatus.CONFLICT, mensaje, request, List.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> validacion(
			MethodArgumentNotValidException error,
			HttpServletRequest request) {
		List<String> detalles = error.getBindingResult().getFieldErrors().stream()
				.map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
				.toList();
		return respuesta(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos", request, detalles);
	}

	@ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
			HttpMessageNotReadableException.class})
	public ResponseEntity<ApiError> solicitudInvalida(Exception error, HttpServletRequest request) {
		String mensaje = error instanceof HttpMessageNotReadableException
				? "El cuerpo de la solicitud no tiene un formato JSON válido"
				: error.getMessage();
		return respuesta(HttpStatus.BAD_REQUEST, mensaje, request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> inesperado(Exception error, HttpServletRequest request) {
		return respuesta(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Ocurrió un error interno al procesar la solicitud",
				request,
				List.of());
	}

	private ResponseEntity<ApiError> respuesta(
			HttpStatus estado,
			String mensaje,
			HttpServletRequest request,
			List<String> detalles) {
		ApiError cuerpo = new ApiError(
				LocalDateTime.now(),
				estado.value(),
				estado.getReasonPhrase(),
				mensaje,
				request.getRequestURI(),
				detalles);
		return ResponseEntity.status(estado).body(cuerpo);
	}
}
